# Business Rules — CBACT04C (monthly interest calculation, batch job INTCALC)

Source: `app/cbl/CBACT04C.cbl` (652 lines), driven by `app/jcl/INTCALC.jcl`
(`PGM=CBACT04C,PARM='2022071800'`). Copybooks: CVTRA01Y (transaction category balance), CVACT01Y
(account master), CVACT03Y (card cross-reference), CVTRA02Y (disclosure group), CVTRA05Y
(transaction record), CSUTLDPY / COCRDUP (shared work areas). Inputs: TCATBALF, XREFFILE
(+ XREFFIL1 alternate index path), ACCTFILE (opened I-O), DISCGRP. Output: TRANSACT (new data set,
LRECL 350).

## 1. Business rules

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | The run date is supplied by the operator | The run identifies its interest transactions by the date passed to it when the job is started; the programme takes no date from the system for that purpose. | `PROCEDURE DIVISION USING EXTERNAL-PARMS`, `PARM-DATE` X(10); `INTCALC.jcl` supplies `'2022071800'` | Derivation/Default | Hard-coded literal (the JCL's `2022071800`) | Hard-coded run date in the JCL — requires business sign-off before each run |
| BR-02 | Every input must be available before interest is calculated | The category balances, the cross-reference, the disclosure groups, the account master and the output transaction file are all opened before the first balance is processed; any failed open ends the run abnormally. | `0000-TCATBALF-OPEN`, `0100-XREFFILE-OPEN`, `0200-DISCGRP-OPEN`, `0300-ACCTFILE-OPEN` (`OPEN I-O`), `0400-TRANFILE-OPEN` (`OPEN OUTPUT`) | Exception Handling | Business-derived | The `DISPLAY` of `0200` says "DALY REJECTS FILE" although it opens the disclosure group file — misleading operator message |
| BR-03 | Interest is calculated for every category balance, account by account | The run reads every transaction category balance in account order and treats each one as one interest-bearing balance of that account. | `PERFORM UNTIL END-OF-FILE = 'Y'` with `1000-TCATBALF-GET-NEXT`; `ACCESS MODE IS SEQUENTIAL` | Data Selection | Business-derived | — |
| BR-04 | A change of account closes the previous account | When the account number on a balance differs from the previous one, the previous account is settled first and a new interest total is started for the new account. | main loop `IF TRANCAT-ACCT-ID NOT= WS-LAST-ACCT-NUM` → `1050-UPDATE-ACCOUNT` (guarded by `WS-FIRST-TIME`), `MOVE 0 TO WS-TOTAL-INT` | Control Flow | Business-derived | Correct only while the input is in account order; an out-of-order input would settle an account twice |
| BR-05 | The first account is not settled prematurely | On the very first balance read there is no previous account, so no settlement is made. | main loop `IF WS-FIRST-TIME NOT = 'Y'` | Control Flow | Business-derived | `WS-FIRST-TIME` is set to `'N'` in `1100-GET-ACCT-DATA`, so the guard depends on a side effect of another paragraph |
| BR-06 | The account and its card are fetched once per account | At each change of account the account master record and the account's cross-reference (card) record are read once and reused for all of that account's balances. | `1100-GET-ACCT-DATA` (keyed read of ACCTFILE), `1110-GET-XREF-DATA` (keyed read on the account alternate index) | Data Selection | Business-derived | A missing account or cross-reference is only reported (`DISPLAY 'ACCOUNT NOT FOUND: '`) and then treated as a hard read error by the status check that follows — see §4 |
| BR-07 | The interest rate comes from the account's disclosure group | The rate is the one published for the combination of the account's disclosure group, the transaction type and the transaction category. | `1200-GET-INTEREST-RATE`, keyed read of DISCGRP on group + type + category | Data Selection | Business-derived | — |
| BR-08 | A missing disclosure group falls back to the default group | If the account's own disclosure group has no published rate for that type and category, the rate published for the group named `DEFAULT` is used. | `1200-GET-INTEREST-RATE` `IF DISCGRP-STATUS = '23'` → `MOVE 'DEFAULT'`, `1200-A-GET-DEFAULT-INT-RATE` | Derivation/Default | Hard-coded literal (`'DEFAULT'`) | A missing `DEFAULT` row is fatal: `1200-A` accepts only status `'00'` and abends otherwise |
| BR-09 | A zero rate produces no interest | A balance whose applicable rate is zero produces no interest and no interest transaction. | main loop `IF DIS-INT-RATE NOT = 0` … `END-IF` | Control Flow | Business-derived | No `ELSE`: a zero rate is silently skipped and nothing is logged |
| BR-10 | Monthly interest is one twelfth of the annual rate on the balance | The interest on a category balance is the balance multiplied by the annual percentage rate, divided by twelve months and by one hundred. | `1300-COMPUTE-INTEREST`: `COMPUTE WS-MONTHLY-INT = ( TRAN-CAT-BAL * DIS-INT-RATE) / 1200` | Calculation | Hard-coded literal (1200) | No `ROUNDED` phrase, so the result is truncated to two decimals, not rounded |
| BR-11 | Interest accumulates per account | The interest computed for each of an account's category balances is added to that account's interest total for the run. | `1300-COMPUTE-INTEREST`: `ADD WS-MONTHLY-INT TO WS-TOTAL-INT` | Calculation | Business-derived | — |
| BR-12 | Each interest amount is recorded as a transaction | Every non-zero interest amount is recorded as its own transaction against the account's card. | `1300-B-WRITE-TX` | Control Flow | Business-derived | — |
| BR-13 | Interest transaction identifier | The transaction identifier is the run date followed by a counter that increases by one for every interest transaction written in the run. | `1300-B-WRITE-TX`: `ADD 1 TO WS-TRANID-SUFFIX`, `STRING PARM-DATE, WS-TRANID-SUFFIX … INTO TRAN-ID` | Derivation/Default | Business-derived | The counter is `9(6)`, so a run producing more than 999 999 interest transactions would repeat identifiers |
| BR-14 | Fixed interest transaction classification | Every interest transaction is recorded as type `01`, category `5`, source `System`, described as "Int. for a/c" followed by the account number, with no merchant. | `1300-B-WRITE-TX`: `MOVE '01' TO TRAN-TYPE-CD`, `MOVE '05' TO TRAN-CAT-CD`, `MOVE 'System' TO TRAN-SOURCE`, `STRING 'Int. for a/c ' …`, `MOVE 0 TO TRAN-MERCHANT-ID`, `MOVE SPACES` to name/city/zip | Derivation/Default | Hard-coded literal (`'01'`, `'05'`, `'System'`, the description text, merchant 0/spaces) | Hard-coded classification values; merchant fields populated only with placeholders |
| BR-15 | Interest transactions are stamped with the current time | The origination and processing times of an interest transaction are both the moment it is written. | `1300-B-WRITE-TX`: `PERFORM Z-GET-DB2-FORMAT-TIMESTAMP`, `MOVE DB2-FORMAT-TS TO TRAN-ORIG-TS` and `TRAN-PROC-TS` | Formatting/Conversion | Business-derived | The run date of BR-01 and the timestamp here can disagree, because one is supplied and the other is taken from the clock |
| BR-16 | Settlement adds the interest and clears the cycle | Settling an account adds its interest total to the current balance and resets the current cycle credit and debit amounts to zero. | `1050-UPDATE-ACCOUNT`: `ADD WS-TOTAL-INT TO ACCT-CURR-BAL`, `MOVE 0 TO ACCT-CURR-CYC-CREDIT`, `MOVE 0 TO ACCT-CURR-CYC-DEBIT`, `REWRITE` | Calculation | Business-derived | The cycle amounts are cleared even when the interest total is zero, so the run is also the cycle reset |
| BR-17 | The last account is settled when the input ends | When the category balance file ends, the account the last balance belonged to is settled. | main loop `ELSE PERFORM 1050-UPDATE-ACCOUNT` on the end-of-file branch | Control Flow | Business-derived | On an empty input file this rewrites whatever account record happens to be in the work area — see §4 |
| BR-18 | Fees are not calculated | The programme reserves a step for fee calculation but calculates no fees. | `1400-COMPUTE-FEES`: `* To be implemented`, `EXIT` | Control Flow | Business-derived | Declared business step never implemented — REVIEW REQUIRED |
| BR-19 | Every balance read is logged | Each category balance read and the number of records read are recorded in the job log. | `DISPLAY TRAN-CAT-BAL-RECORD`, `ADD 1 TO WS-RECORD-COUNT` | Control Flow | Business-derived | Operator log only, no business effect |
| BR-20 | A write or rewrite failure ends the run abnormally | A failure writing an interest transaction or rewriting an account is reported with the file status and the run ends abnormally, leaving the accounts settled so far already updated. | `1300-B-WRITE-TX` and `1050-UPDATE-ACCOUNT` status checks → `9910-DISPLAY-IO-STATUS`, `9999-ABEND-PROGRAM` (`CEE3ABD`, code 999) | Exception Handling | Hard-coded literal (999) | Hard-coded abend code; partial settlement is visible after an abend |

## 2. Exception scenarios (file and I/O)

| Condition | Source treatment | Target treatment |
|---|---|---|
| TCATBALF status `'00'` | continue | reader returns the next row |
| TCATBALF status `'10'` | end of input, last account settled, run completes | reader returns `null`, `afterStep` settles the last account |
| TCATBALF any other status | display the status, `CEE3ABD` code 999 | the step fails, job status `FAILED` |
| ACCTFILE keyed read, invalid key | `DISPLAY 'ACCOUNT NOT FOUND: '` then abend, because status `'23'` fails the `= '00'` test | `CardholderView.accountFound()` is false, a warning is logged and the account's balances are skipped — a deliberate deviation, see §4 |
| DISCGRP status `'00'` or `'23'` | acceptable; `'23'` triggers the DEFAULT-group retry | `findById` on the account's group, then on `DEFAULT` |
| DISCGRP any other status | display the status, `CEE3ABD` code 999 | repository exception fails the step |
| DEFAULT group read, any status but `'00'` | display the status, `CEE3ABD` code 999 | absent `DEFAULT` row yields rate zero, so BR-09 skips the balance — deliberate deviation, see §4 |
| TRANSACT write status not `'00'` | display the status, `CEE3ABD` code 999 | persistence exception fails the chunk |
| ACCTFILE rewrite status not `'00'` | display the status, `CEE3ABD` code 999 | a failed settlement call fails the step |

## 3. Target implementation

`InterestCalculationJobConfig` (job `interestCalculationJob`, step `interestCalculationStep`) with
`categoryBalanceReader` — a paged query ordered by account, type and category, which is the key
order BR-03 and BR-04 depend on — and `InterestAccrualWriter`, which holds the per-account state.
The run date of BR-01 is the `parmDate` job parameter. The account and cross-reference reads of
BR-06 become one `AccountServiceClient.byAccountId` call per account; the settlement of BR-16
becomes `POST /api/accounts/{id}/balance-adjustments` with an interest-settlement adjustment keyed
`INTCALC-<parmDate>-<accountId>`, so a retried or re-run step cannot add the interest twice. The
end-of-file settlement of BR-17 is the writer's `afterStep`. Tests:
`InterestAccrualWriterTest`, `TransactionBatchIntegrationTest`.

## 4. Deliberate deviations and risk register

Three source behaviours are **not** reproduced. Each would be a defect in the target and each is
registered for business sign-off:

1. **`OPEN OUTPUT TRANSACT` (BR-02).** The JCL allocates TRANSACT as a new data set and the
   programme opens it as output, so a run of CBACT04C produces a file containing *only* the interest
   transactions, and the posted transactions of `CBTRN02C` are not in it. The target appends interest
   transactions to the same `transaction` table the posting job writes, keyed by the identifier of
   BR-13.
2. **The end-of-file settlement on an empty input (BR-17).** With no balance read, the source still
   performs `1050-UPDATE-ACCOUNT`, which rewrites the account record left in the work area — on the
   first run of the programme an all-spaces record. The target settles nothing when no balance was
   read, because `currentAccountId` is null.
3. **A missing account or `DEFAULT` disclosure group (BR-06, BR-08).** The source displays a message
   and then abends on the file-status check that follows. The target logs the same message and skips
   the account's balances, so one bad account does not stop the interest run.

Preserved as-is, and therefore requiring sign-off rather than correction: the hard-coded run date of
BR-01, the truncation of BR-10, the hard-coded classification and placeholder merchant of BR-14,
the unimplemented fee step of BR-18, and the cycle reset of BR-16 happening even for a zero interest
total. The consistency delta of the settlement call is CMD-18 in
`12-consistency-model-delta-register.md`.
