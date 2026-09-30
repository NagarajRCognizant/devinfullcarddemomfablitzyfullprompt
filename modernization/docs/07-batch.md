# Batch Conversion — READACCT / CBACT01C and CBPAUP0J / CBPAUP0C to Spring Batch

Two jobs are in scope: the account extract (§1-§6) and the expired-authorization purge (§7).

## 1. Job-step mapping

| JCL step | Utility / program | DD statements | Target |
|---|---|---|---|
| `PREDEL` | `IEFBR14` with `DISP=(MOD,DELETE,DELETE)` on the three extracts | `DD1`, `DD2`, `DD3` | `FlatFileItemWriter.setShouldDeleteIfExists(true)` on each writer, so a fresh run starts from an empty file |
| `STEP05` | `CBACT01C` | `ACCTFILE` (KSDS, input), `OUTFILE` 107 FB, `ARRFILE` 110 FB, `VBRFILE` 84 VB | step `readAcctStep` in `ReadAcctJobConfig` |

Job name `readAcctJob`, one chunk-oriented step, chunk size 50.

- **Reader** — `JpaPagingItemReader` over `select a from AccountEntity a order by a.acctId`. Ordering
  by the primary key reproduces the sequential read of a KSDS in key order, which is the order the
  extract records must appear in.
- **Writer** — `CompositeItemWriter` over three `FlatFileItemWriter`s, one per output data set, each
  with its own `LineAggregator` producing a byte-exact record image and `lineSeparator` set to the
  empty string for the fixed-length files.
- **Restart** — the Spring Batch job repository lives in the same PostgreSQL database, so a failed
  run can be relaunched with the same parameters and resumes from the last committed chunk. A *new*
  run (new `run.id`) recreates the output files, which is the PREDEL behaviour.
- **Idempotency** — the step only reads `account` and writes the three extracts; re-running with a
  new `run.id` produces identical output for identical input.

## 2. Output record layouts

### OUTFILE — `AWS.M2.CARDDEMO.ACCTDATA.PSCOMP`, LRECL 107 FB

| Offset | Len | Field | Source | Form |
|---|---|---|---|---|
| 1 | 11 | account id | `ACCT-ID` | `9(11)` zero-filled |
| 12 | 1 | active status | `ACCT-ACTIVE-STATUS` | `X(1)` |
| 13 | 12 | current balance | `ACCT-CURR-BAL` | `S9(10)V99` display, sign overpunch |
| 25 | 12 | credit limit | `ACCT-CREDIT-LIMIT` | `S9(10)V99` display |
| 37 | 12 | cash credit limit | `ACCT-CASH-CREDIT-LIMIT` | `S9(10)V99` display |
| 49 | 10 | open date | `ACCT-OPEN-DATE` | `X(10)` |
| 59 | 10 | expiration date | `ACCT-EXPIRAION-DATE` | `X(10)` |
| 69 | 10 | reissue date | `ACCT-REISSUE-DATE` via `COBDATFT` | `X(10)`, 8-digit result truncated into 10 bytes |
| 79 | 12 | current cycle credit | `ACCT-CURR-CYC-CREDIT` | `S9(10)V99` display |
| 91 | 7 | current cycle debit | literal `2525.00` **only when the stored value is zero** | `COMP-3` `S9(10)V99` |
| 98 | 10 | group id | `ACCT-GROUP-ID` | `X(10)` |

### ARRFILE — `…ACCTDATA.ARRYPS`, LRECL 110 FB

| Offset | Len | Field | Value written by the source |
|---|---|---|---|
| 1 | 11 | account id | `ACCT-ID` |
| 12 | 12 + 7 | occurrence 1 balance / debit | `ACCT-CURR-BAL` / literal `1005.00` |
| 31 | 12 + 7 | occurrence 2 balance / debit | `ACCT-CURR-BAL` / literal `1525.00` |
| 50 | 12 + 7 | occurrence 3 balance / debit | literal `-1025.00` / literal `-2500.00` |
| 69 | 12 + 7 | occurrence 4 | never populated — `INITIALIZE` zeroes |
| 88 | 12 + 7 | occurrence 5 | never populated — `INITIALIZE` zeroes |
| 107 | 4 | filler | blanks |

### VBRFILE — `…ACCTDATA.VBPS`, LRECL 84 VB

Each account produces two records into an `X(80)` area whose written length comes from
`WS-RECD-LEN`:

| Record | Len | Content |
|---|---|---|
| VB1 | 12 | account id `9(11)` + active status `X(1)` |
| VB2 | 39 | account id `9(11)` + current balance `S9(10)V99` + credit limit `S9(10)V99` + reissue year `X(4)` |

Both are emitted for one input item and each carries the 4-byte record descriptor word a
variable-blocked data set holds on disk.

## 3. Preserved source behaviours that look like defects

None of these were "fixed": intended behaviour is not corroborated anywhere, so the AS-IS behaviour
stands and each is registered (UCR-07, UCR-05) and flagged in `BRD-CBACT01C.md`.

| Behaviour | Source | Consequence in the target |
|---|---|---|
| `2525.00` written only when the stored debit is zero, otherwise the field keeps the previous record's content (uninitialised for the first record) | `1300-POPUL-ACCT-RECORD` | `AcctCompRecordAggregator` keeps the record-area field between items, blank-initialised for the first record |
| Four hard-coded array amounts and occurrence 3 ignoring the account | `1400-POPUL-ARRAY-RECORD` | written as literals |
| Occurrences 4 and 5 never populated | `1400` | zeroes |
| `COBDATFT` return status never checked | `1300` | `CobdatftDateFormatter` returns a status the aggregator deliberately does not act on, mirroring the source |
| Output files never explicitly closed | `9000-*` | JVM/Spring Batch closes the writers; no observable difference |

## 4. Failure handling

`CBACT01C` aborts the step (`CALL 'CEE3ABD'`, code 999) on any file status other than `00`, and
treats `10` on the input read as end of file. In the target, end of data is the reader returning
`null`; any other data-access failure propagates, the chunk transaction rolls back and the step
finishes `FAILED`, which is the closest local equivalent to a non-zero step completion. There is no
retry or skip policy, because the source has none.

## 5. Local launch

```bash
cd modernization
java -jar batch-account-extract/target/carddemo-batch-account-extract.jar \
  --spring.batch.job.enabled=true \
  --spring.batch.job.name=readAcctJob \
  --carddemo.batch.readacct.output-directory=/absolute/output/dir \
  run.id=1
```

Increment `run.id` for each fresh run (a repeated parameter set is rejected as already complete,
which is the intended guard against accidental duplicate extracts). No external scheduler is
introduced.

Evidence: `evidence/batch-readacct.log` — job `COMPLETED`, three files produced
(`acctdata.pscomp`, `acctdata.arryps`, `acctdata.vbps`).

## 6. Tests

`AcctRecordAggregatorTest` asserts the byte content, offsets and total length of all three layouts
including the sign overpunch, the packed field, the literals and the unpopulated occurrences.
`ReadAcctJobIntegrationTest` launches the job against the local PostgreSQL database and asserts the
record counts and per-record lengths of the produced files.

## 7. `CBPAUP0J` / `CBPAUP0C` — expired authorization purge

The second converted job is the IMS BMP `CBPAUP0C`, submitted by `CBPAUP0J`. It walks the
`PAUTSUM0` roots of `DBPAUTP0`, deletes `PAUTDTL1` children older than the configured number of days,
reverses the approved and declined counters those children contributed, and checkpoints every *n*
roots. It lives in `batch-auth-purge`, built and launched exactly like `batch-account-extract`.

### 7.1 Job-step mapping

| Source | Target | Notes |
|---|---|---|
| `CBPAUP0J` EXEC of `DFSRRC00` with PSB `PSBPAUTB` | `purgeExpiredAuthorizationsJob` | one job, one step — the source has one `MAIN-PARA` loop |
| SYSIN card `expiry-days,chkp-freq,chkp-display-freq,debug` | `carddemo.batch.authpurge.*` (`AuthPurgeProperties`) | the defaults reproduce the source's own defaulting: 5 days when the field is non-numeric, 5 checkpoints and 10 display when blank, zero or LOW-VALUES, and debug `N` unless `Y` is supplied |
| `GN` on the root PCB | `AuthSummaryKeysetReader` | keyset read ordered by account id; the last key is saved in the step execution context |
| `GB` on the root read | reader returns `null` | end of database |
| any other root status | the data-access failure propagates, the chunk rolls back and the step ends `FAILED` with a non-zero exit status | the source's abend with return code 16; a scheduler reacts to the same signal |
| `GNP` on the detail PCB | `ExpiredAuthorizationProcessor` reading the summary's children | — |
| `GE`/`GB` on the detail read | end of children | — |
| expiry test `99999 - PA-AUTH-DATE-9C` then `CURRENT-YYDDD - WS-AUTH-DATE >= days` | `ExpiredAuthorizationProcessor` | the same complement decode and the same Julian-day subtraction, including its year-boundary defect (UCR-25) |
| `DLET` of an expired child | `AuthPurgeWriter` delete | — |
| counter reversal on the root | `AuthPurgeWriter` summary update | approved children decrement `approved_auth_cnt`/`approved_auth_amt`, declined children the declined pair |
| `DLET` of the root when the duplicated condition holds | `AuthPurgeWriter` summary delete | the duplicated `approvedCnt <= 0 && approvedCnt <= 0` condition is preserved verbatim (UCR-26) |
| `CHKP` every *n* roots with id `RMAD…` | chunk commit, chunk size = checkpoint frequency | the checkpoint id prefix is kept in the log line so the operator sees the same trace |
| the end-of-job report | the `# TOTAL SUMMARY READ` / `SUMMARY REC DELETED` / `TOTAL DETAILS READ` / `DETAILS REC DELETED` / `SUMMARY REC ADJUSTED` lines | same labels, same order |

`CBPAUP0C` decrements the summary counters in working storage but never issues a `REPL` for
`PAUTSUM0`, so unless the root is deleted the adjustment the job exists to make is discarded — the
available credit is never actually adjusted (UCR-27). This is one of the two defects corrected in
this conversion, because the correct behaviour is corroborated by the program's own computation, its
report line and the module README: `carddemo.batch.authpurge.persist-summary-adjustments` defaults to
`true` and writes the adjusted counters back. Setting it to `false` reproduces the AS-IS behaviour for
comparison runs. Both paths are covered by tests and the delta is recorded in the parity evidence.

### 7.2 Restartability and checkpointing

An IMS `CHKP` commits the database position and the counters together; a Spring Batch chunk commit
does the same for the deletes, the counter reversals and the reader's saved key. A relaunch with the
same parameters resumes after the last committed root rather than at the top of the database, which
is strictly better than the source's restart-from-checkpoint-id and is recorded as CMD-15.

### 7.3 Local launch

```bash
cd modernization
java -jar batch-auth-purge/target/carddemo-batch-auth-purge.jar \
  --spring.batch.job.enabled=true \
  --spring.batch.job.name=purgeExpiredAuthorizationsJob \
  --carddemo.batch.authpurge.expiry-days=5 \
  run.id=1
```

Evidence: `evidence/authorization-purge-batch-run.txt` — 21 summaries and 202 details read, all
deleted (every authorization in the supplied unload predates the five-day window), job `COMPLETED`.

### 7.4 Tests

`AuthPurgePropertiesTest` covers the SYSIN defaulting rules. `ExpiredAuthorizationProcessorTest`
covers the complement decode, the day arithmetic and the expiry boundary. `AuthPurgeWriterTest`
covers the counter reversal in both directions, the detail deletes and the duplicated summary-delete
condition. `AuthSummaryKeysetReaderTest` covers the paging read and the saved position.
`PurgeAuthJobIntegrationTest` launches the job against the local PostgreSQL database with a fixed
clock and asserts the deleted rows, the reversed approved and declined totals, the root deleted on the
approved counter alone, an authorization inside the window left untouched, the commit count at the
configured checkpoint frequency, and that a re-run purges nothing further (idempotence).
