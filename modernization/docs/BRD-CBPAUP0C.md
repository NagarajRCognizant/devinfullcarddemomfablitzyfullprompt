# Business Rules — CBPAUP0C (Expired authorization purge, BMP job CBPAUP0J)

Source: `app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl` (386 lines), driven by
`app/app-authorization-ims-db2-mq/jcl/CBPAUP0J.jcl` (`PGM=DFSRRC00,PARM='BMP,CBPAUP0C,PSBPAUTB'`).
Copybooks: `CIPAUSMY`, `CIPAUDTY`, `PAUTBPCB`, `IMSFUNCS`. Data: IMS HIDAM `DBPAUTP0` through PSB
`PSBPAUTB` (update PCB). Parameter card: `SYSIN` = `00,00001,00001,Y` — expiry days, checkpoint
frequency, checkpoint display frequency, debug flag.

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | Run parameters come from the job's parameter card | The retention period in days, the checkpoint frequency, the checkpoint reporting frequency and the debug switch are supplied by the job. | `1000-INITIALIZE` (183) `ACCEPT PRM-INFO FROM SYSIN`, `CBPAUP0J.jcl` `SYSIN DD *` | Control Flow | Business-derived | Positional card with no validation beyond the defaults below |
| BR-02 | Retention period defaults to five days | A non-numeric retention period is treated as five days. | `1000-INITIALIZE` lines 198-202 | Derivation/Default | Hard-coded literal (5) | The shipped card has `00`, so nothing older than today is retained — REVIEW REQUIRED |
| BR-03 | Checkpoint frequency defaults to five summaries | A blank, zero or unset checkpoint frequency is treated as five summaries. | `1000-INITIALIZE` lines 203-205 | Derivation/Default | Hard-coded literal (5) | — |
| BR-04 | Checkpoint reporting frequency defaults to ten checkpoints | A blank, zero or unset reporting frequency is treated as ten checkpoints. | `1000-INITIALIZE` lines 206-208 | Derivation/Default | Hard-coded literal (10) | — |
| BR-05 | Debug reporting is off unless explicitly requested | Debug reporting is on only when the parameter card carries `Y`; any other value turns it off. | `1000-INITIALIZE` lines 209-211 | Derivation/Default | Business-derived | — |
| BR-06 | Every account's authorizations are examined once | The purge walks every pending authorization summary in the database in order and examines each of its authorizations. | `MAIN-PARA` lines 138-167, `2000-FIND-NEXT-AUTH-SUMMARY` (216) `EXEC DLI GN SEGMENT(PAUTSUM0)`, `3000-FIND-NEXT-AUTH-DTL` (248) `EXEC DLI GNP SEGMENT(PAUTDTL1)` | Data Selection | Business-derived | — |
| BR-07 | The walk ends at the end of the database | Reaching the end of the database ends the run normally. | `2000-FIND-NEXT-AUTH-SUMMARY` lines 231-232 (`WHEN 'GB'`) | Control Flow | Business-derived | Status treated as business logic |
| BR-08 | A summary read failure ends the run abnormally | Any other outcome reading a summary is reported with the database status and the number of summaries read, and the run ends abnormally with code 16. | `2000-FIND-NEXT-AUTH-SUMMARY` lines 233-238, `9999-ABEND` (377) | Exception Handling | Hard-coded literal (16) | — |
| BR-09 | The end of an account's authorizations moves on to the next account | Not finding a further authorization for the account, or reaching the end of the database, moves the walk on to the next account. | `3000-FIND-NEXT-AUTH-DTL` lines 264-266 (`WHEN 'GE' WHEN 'GB'`) | Control Flow | Business-derived | — |
| BR-10 | An authorization read failure ends the run abnormally | Any other outcome reading an authorization is reported with the database status, the account id and the number of authorizations read, and the run ends abnormally with code 16. | `3000-FIND-NEXT-AUTH-DTL` lines 267-272 | Exception Handling | Hard-coded literal (16) | — |
| BR-11 | Authorization age | The age of an authorization is today's day of the year less its authorization date, recovered by subtracting the stored complemented date from 99999. | `4000-CHECK-IF-EXPIRED` (277) lines 280-282 | Calculation | Hard-coded literal (99999) | Julian day arithmetic across a year boundary gives a negative age — REVIEW REQUIRED (UCR-25) |
| BR-12 | An authorization at or beyond the retention period is purged | An authorization whose age is at least the retention period is deleted; a younger one is kept. | `4000-CHECK-IF-EXPIRED` lines 284-297 | Validation | Business-derived | — |
| BR-13 | Purging an approved authorization releases approved credit | Purging an authorization whose response code is `00` reduces the account's approved authorization count by one and its approved authorization total by the approved amount. | `4000-CHECK-IF-EXPIRED` lines 287-289 | Calculation | Business-derived | The summary is never rewritten, so the reversal is lost (BR-17) |
| BR-14 | Purging a declined authorization releases declined totals | Purging an authorization with any other response code reduces the declined authorization count by one and the declined authorization total by the transaction amount. | `4000-CHECK-IF-EXPIRED` lines 290-292 | Calculation | Business-derived | — |
| BR-15 | Purged authorizations are deleted | An authorization qualified for purge is deleted; a failed delete is reported with the database status and the account id and ends the run abnormally with code 16. | `5000-DELETE-AUTH-DTL` (303) | Control Flow | Business-derived | — |
| BR-16 | An account with no approved authorizations left loses its summary | When the account's approved authorization count has fallen to zero or below, its pending authorization summary is deleted; a failed delete ends the run abnormally with code 16. | `MAIN-PARA` lines 156-158, `6000-DELETE-AUTH-SUMMARY` (328) | Control Flow | Business-derived | **Defect**: the condition tests the approved count twice (`PA-APPROVED-AUTH-CNT <= 0 AND PA-APPROVED-AUTH-CNT <= 0`) where the declined count was evidently intended; a summary with declined authorizations left can be deleted. Carried forward unchanged, recorded as UCR-26 |
| BR-17 | Counter reversals are never stored | The approved and declined counters recomputed while purging are not written back: only the deletes survive the run. | absence of any `REPL SEGMENT(PAUTSUM0)` in the program | Calculation | Business-derived | **Defect**: available-credit adjustment computed and discarded — carried forward as the default and remediable behind a switch, UCR-27 |
| BR-18 | Work is checkpointed by account count | After the configured number of summaries a checkpoint is taken, so a restart resumes from the last checkpoint rather than the start of the database. | `MAIN-PARA` lines 160-164, `9000-TAKE-CHECKPOINT` (352) `EXEC DLI CHKP ID(WK-CHKPT-ID)` | Control Flow | Business-derived | Counter is compared with `>` so the interval is the frequency plus one |
| BR-19 | Checkpoint identifier | Checkpoints are identified by a fixed prefix `RMAD` followed by the checkpoint sequence. | `WK-CHKPT-ID` working storage (`'RMAD'`) | Derivation/Default | Hard-coded literal (`RMAD`) | — |
| BR-20 | Checkpoints are reported periodically | Every configured number of checkpoints, the checkpoint is reported with the number of summaries read and the account id reached. | `9000-TAKE-CHECKPOINT` lines 359-365 | Control Flow | Business-derived | Operator log only |
| BR-21 | A checkpoint failure ends the run abnormally | A failed checkpoint is reported with the database status, the summary count and the account id, and the run ends abnormally with code 16. | `9000-TAKE-CHECKPOINT` lines 366-371 | Exception Handling | Hard-coded literal (16) | — |
| BR-22 | A final checkpoint closes the run | A checkpoint is taken after the last account has been processed. | `MAIN-PARA` line 170 | Control Flow | Business-derived | — |
| BR-23 | Run totals are reported | The run reports the number of summaries read, summaries deleted, authorizations read and authorizations deleted. | `MAIN-PARA` lines 172-180 | Control Flow | Business-derived | — |
| BR-24 | Debug reporting | With debug on, each summary read, authorization read, authorization delete and summary delete is reported with its counter or account id. | `2000`/`3000`/`5000`/`6000` opening `IF DEBUG-ON` blocks | Control Flow | Business-derived | — |

## Exception scenarios (IMS)

| Condition | Source treatment | Target treatment |
|---|---|---|
| status blank on `GN`/`GNP` | continue | reader/processor continues |
| `GB` on the root walk | end of run | reader returns `null`, step completes |
| `GE`/`GB` on the child walk | next account | child list exhausted |
| any other read status | display and `RETURN-CODE 16` | exception → step `FAILED`, job exit status non-zero |
| delete failure | display and `RETURN-CODE 16` | `DataAccessException` → chunk rolled back, step `FAILED` |
| checkpoint failure | display and `RETURN-CODE 16` | commit failure → step `FAILED`, restart from the last committed chunk |

## Target implementation

`PurgeAuthJobConfig` (job `purgeAuthJob`, step `purgeExpiredAuthorizationsStep`),
`AuthSummaryKeysetReader` (the `GN` root walk, with the last root key as restart state in place of
the IMS checkpoint id), `ExpiredAuthorizationProcessor` (`GNP` child walk, age arithmetic and
counter reversal), `AuthPurgeWriter` (deletes, the preserved duplicated summary-delete condition
and the switchable counter write-back), `AuthPurgeProperties` (the parameter card). Chunk size is
the checkpoint frequency, so a Spring Batch commit interval takes the place of `EXEC DLI CHKP`.
Batch design is in `07-batch.md`.
