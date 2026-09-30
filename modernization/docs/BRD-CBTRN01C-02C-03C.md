# Business Rules — CBTRN01C / CBTRN02C / CBTRN03C (daily transaction batch)

Sources: `app/cbl/CBTRN01C.cbl` (494 lines), `app/cbl/CBTRN02C.cbl` (731 lines),
`app/cbl/CBTRN03C.cbl` (649 lines). Layouts `app/cpy/CVTRA06Y.cpy` (daily transaction),
`app/cpy/CVTRA05Y.cpy` (transaction), `app/cpy/CVTRA01Y.cpy` (category balance),
`app/cpy/CVTRA03Y.cpy` (type), `app/cpy/CVTRA04Y.cpy` (category), `app/cpy/CVTRA07Y.cpy` (reject),
`app/cpy/CVACT01Y.cpy` (account), `app/cpy/CVACT03Y.cpy` (cross-reference). Jobs
`app/jcl/TRANBKP.jcl`, `app/jcl/POSTTRAN.jcl`, `app/jcl/TRANREPT.jcl`.

## CBTRN01C — daily transaction verification

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | Every daily transaction is examined once | The whole daily transaction file is read in sequence until end of file. | `MAIN-PARA` `PERFORM UNTIL END-OF-DAILY-TRANS-FILE = 'Y'`, `1000-DALYTRAN-GET-NEXT` | Control Flow | Business-derived | — |
| BR-02 | The card must be known | A transaction whose card number is not in the cross-reference is skipped and reported as "CARD NUMBER <n> COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-<id>". | `MAIN-PARA` `IF WS-XREF-READ-STATUS = 0 … ELSE`, `2000-LOOKUP-XREF` `INVALID KEY` | Validation | Business-derived | The transaction is only reported, never written anywhere; a downstream reconciliation has nothing to work from |
| BR-03 | The account behind the card must exist | When the card is known, the account it points to is read and a missing one is reported as "ACCOUNT <id> NOT FOUND". | `MAIN-PARA` `IF WS-ACCT-READ-STATUS NOT = 0`, `3000-READ-ACCOUNT` | Validation | Business-derived | Reported only; the run still ends normally |
| BR-04 | Verification changes nothing | The run is read-only: every file is opened for input and no record is written or updated. | all `OPEN INPUT` paragraphs; no `WRITE`/`REWRITE` in the program | Control Flow | Business-derived | The customer, card and transaction files are opened and closed but never read — dead I/O |
| BR-05 | End of file is a normal outcome | File status "10" on the daily file ends the run; status "00" is a successful read. | `1000-DALYTRAN-GET-NEXT` (`'00'` → 0, `'10'` → 16, other → 12) | Exception Handling | Business-derived | — |
| BR-06 | Any other file failure stops the run | Any open, read or close status other than "00" (or "10" on the read) displays the status and abends with code 999. | every open/close paragraph, `Z-ABEND-PROGRAM` (`CALL 'CEE3ABD' USING ABCODE, TIMING` with `ABCODE` 999) | Exception Handling | Business-derived | Abend code 999 is a hard-coded literal; the daily-file close paragraph reports "ERROR CLOSING CUSTOMER FILE" and displays the customer status — a copy-paste defect recorded, not fixed |

## CBTRN02C — daily transaction posting

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | Every daily transaction is validated then posted or rejected | Each transaction is counted, validated, and either posted or written to the reject file. | main paragraph `PERFORM UNTIL END-OF-FILE = 'Y'`, `1500-VALIDATE-TRAN`, `2000-POST-TRANSACTION`, `2500-WRITE-REJECT-REC` | Control Flow | Business-derived | — |
| BR-02 | An unknown card is rejected | A card number not in the cross-reference is rejected with reason 100, "INVALID CARD NUMBER FOUND". | `1500-A-LOOKUP-XREF` `INVALID KEY` | Validation | Business-derived | Reason codes 100-103 and 109 are hard-coded literals |
| BR-03 | A missing account is rejected | A transaction whose account is not on the account master is rejected with reason 101, "ACCOUNT RECORD NOT FOUND". | `1500-B-LOOKUP-ACCT` `INVALID KEY` | Validation | Business-derived | — |
| BR-04 | A transaction that would breach the credit limit is rejected | The cycle credit less the cycle debit plus the transaction amount must not exceed the credit limit, otherwise the transaction is rejected with reason 102, "OVERLIMIT TRANSACTION". | `1500-B-LOOKUP-ACCT` `COMPUTE WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT`, `IF ACCT-CREDIT-LIMIT >= WS-TEMP-BAL` | Calculation | Business-derived | The current balance is not part of the test; only the cycle figures are |
| BR-05 | A transaction after the account expiry is rejected | A transaction whose origination date is later than the account expiry date is rejected with reason 103, "TRANSACTION RECEIVED AFTER ACCT EXPIRATION". | `1500-B-LOOKUP-ACCT` `IF ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS (1:10)` | Validation | Business-derived | The two rejection tests are not mutually exclusive: an over-limit transaction that is also expired ends up with reason 103, because the later `MOVE` wins |
| BR-06 | A rejected transaction is written with its reason | A rejected transaction is written to the reject file as the original record plus the reason code and description. | `2500-WRITE-REJECT-REC` (`MOVE DALYTRAN-RECORD TO REJECT-TRAN-DATA`, `MOVE WS-VALIDATION-TRAILER TO VALIDATION-TRAILER`) | Formatting/Conversion | Business-derived | — |
| BR-07 | A posted transaction keeps its daily values | The posted transaction carries the id, type, category, source, description, amount, merchant details, card number and origination timestamp of the daily record unchanged. | `2000-POST-TRANSACTION` `MOVE`s | Derivation/Default | Business-derived | — |
| BR-08 | The processing timestamp is the time of posting | The posted transaction's processing timestamp is the run's current date and time in database format. | `2000-POST-TRANSACTION` `PERFORM Z-GET-DB2-FORMAT-TIMESTAMP`, `MOVE DB2-FORMAT-TS TO TRAN-PROC-TS` | Derivation/Default | Business-derived | — |
| BR-09 | The category balance accumulates the amount | The transaction amount is added to the balance held for the account, transaction type and category. | `2700-B-UPDATE-TCATBAL-REC` `ADD DALYTRAN-AMT TO TRAN-CAT-BAL` | Calculation | Business-derived | — |
| BR-10 | A missing category balance is created | The first transaction for an account, type and category combination creates the balance record with the transaction amount, after reporting "TCATBAL record not found for key : <key>.. Creating.". | `2700-UPDATE-TCATBAL` (`MOVE 'Y' TO WS-CREATE-TRANCAT-REC`), `2700-A-CREATE-TCATBAL-REC` | Derivation/Default | Business-derived | Status "23" is accepted alongside "00" on the read, so a not-found is a normal outcome |
| BR-11 | The account balance moves by the transaction amount | The transaction amount is added to the account's current balance. | `2800-UPDATE-ACCOUNT-REC` `ADD DALYTRAN-AMT TO ACCT-CURR-BAL` | Calculation | Business-derived | — |
| BR-12 | Positive amounts are cycle credits and negative ones cycle debits | An amount of zero or more is added to the current cycle credit; a negative amount is added to the current cycle debit. | `2800-UPDATE-ACCOUNT-REC` `IF DALYTRAN-AMT >= 0` | Calculation | Business-derived | A negative amount is *added* to the debit field, so the cycle debit accumulates negatively; BR-04 reads it back in the same sense, so the pair is self-consistent |
| BR-13 | A rewrite of a missing account is a reason-109 failure | If the account cannot be rewritten the transaction is marked with reason 109, "ACCOUNT RECORD NOT FOUND". | `2800-UPDATE-ACCOUNT-REC` `REWRITE … INVALID KEY` | Exception Handling | Business-derived | The reason is set after the category balance has already been updated and the transaction is still written, so the run can leave a posted transaction whose account was not updated |
| BR-14 | The posted transaction is written to the transaction file | Every posted transaction is written to the transaction file. | `2900-WRITE-TRANSACTION-FILE` | Control Flow | Business-derived | The file is opened `OUTPUT`, so each run replaces the transaction file rather than adding to it |
| BR-15 | The run reports its counts | The run reports the number of transactions processed and rejected. | main paragraph `DISPLAY 'TRANSACTIONS PROCESSED :'` / `'TRANSACTIONS REJECTED  :'` | Formatting/Conversion | Business-derived | — |
| BR-16 | Any rejection makes the run a warning | If at least one transaction was rejected the run ends with return code 4. | main paragraph `IF WS-REJECT-COUNT > 0 MOVE 4 TO RETURN-CODE` | Control Flow | Business-derived | — |
| BR-17 | Any file failure stops the run | Any open, read, write, rewrite or close status other than the accepted ones displays the status and abends with code 999. | all open/close paragraphs, `9999-ABEND-PROGRAM` | Exception Handling | Business-derived | Abend code 999 hard-coded |

## CBTRN03C — transaction detail report

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | The report covers a requested date range | The report includes only transactions whose processing date falls between the start and end dates read from the parameter file. | `0550-DATEPARM-READ`, main paragraph `IF TRAN-PROC-TS (1:10) >= WS-START-DATE AND <= WS-END-DATE` | Data Selection | Business-derived | The parameter file supplies the range; there is no default and no validation of it |
| BR-02 | Transactions are grouped by card | Transactions are reported in card-number order and a change of card ends the previous card's group. | main paragraph `IF WS-CURR-CARD-NUM NOT= TRAN-CARD-NUM`, `1120-WRITE-ACCOUNT-TOTALS` | Data Selection | Business-derived | Grouping relies on the file being in card order; it is not sorted by the program |
| BR-03 | Each group shows the account behind the card | The account id shown for a group comes from the card cross-reference. | `1500-A-LOOKUP-XREF`, `MOVE XREF-ACCT-ID TO TRAN-REPORT-ACCOUNT-ID` | Derivation/Default | Business-derived | — |
| BR-04 | Each line shows the type and category descriptions | The transaction's type code and category code are resolved to their descriptions for the report line. | `1500-B-LOOKUP-TRANTYPE`, `1500-C-LOOKUP-TRANCATG` | Derivation/Default | Business-derived | — |
| BR-05 | An unknown card, type or category stops the report | A card, type or category that cannot be found displays the key, reports file status 23 and abends. | `1500-A/B/C` `INVALID KEY` → `9910-DISPLAY-IO-STATUS`, `9999-ABEND-PROGRAM` | Exception Handling | Business-derived | Reference-data gaps abend the report rather than printing the code as-is |
| BR-06 | A page holds twenty lines | Every twenty lines the page totals are printed and a new set of headers begins. | `WS-PAGE-SIZE VALUE 20`, `1100-WRITE-TRANSACTION-REPORT` `IF FUNCTION MOD(WS-LINE-COUNTER, WS-PAGE-SIZE) = 0` | Formatting/Conversion | Hard-coded literal | Page size 20 is a literal |
| BR-07 | The first line prints the headers | The header block is printed once before the first detail line. | `1100-WRITE-TRANSACTION-REPORT` `IF WS-FIRST-TIME = 'Y'`, `1120-WRITE-HEADERS` | Formatting/Conversion | Business-derived | — |
| BR-08 | Each amount is accumulated into three totals | Every reported amount is added to the page total, the account total and, through the page total, the grand total. | `1100-WRITE-TRANSACTION-REPORT` `ADD TRAN-AMT TO WS-PAGE-TOTAL`/`WS-ACCOUNT-TOTAL`, `1110-WRITE-PAGE-TOTALS` `ADD WS-PAGE-TOTAL TO WS-GRAND-TOTAL` | Calculation | Business-derived | — |
| BR-09 | The report ends with the last totals | At end of file the last page total, the last account total and the grand total are printed. | main paragraph `ELSE` branch (`1110-WRITE-PAGE-TOTALS`, `1110-WRITE-GRAND-TOTALS`) | Control Flow | Business-derived | The end-of-file branch adds `TRAN-AMT` to the page and account totals once more even though no record was read, so the final page and account totals repeat the last transaction's amount — recorded, not corrected |
| BR-10 | Report lines are 133 characters | Every header, detail and total line is written as a fixed 133-character record. | `WS-BLANK-LINE PIC X(133)`, `1111-WRITE-REPORT-REC` | Formatting/Conversion | Business-derived | — |
| BR-11 | Any file failure stops the report | Any open, read, write or close status other than "00" (or "10" at end of file) displays the status and abends. | `1111-WRITE-REPORT-REC`, the open/close paragraphs, `9999-ABEND-PROGRAM` | Exception Handling | Business-derived | Abend code 999 hard-coded |

## Exception scenarios

All three programs treat file status as business logic: "00" is success, "10" is normal end of file,
"23" is a not-found (accepted by CBTRN02C BR-10, forced by CBTRN03C BR-05) and any other status
displays `FILE STATUS IS: NNNN<status>` and abends through `CEE3ABD` with abend code 999 and
timing 0. `CBTRN01C` BR-06 records a defect: the daily-file close paragraph reports the customer
file and displays the customer status.

## Target implementation

| Source | Target |
|---|---|
| CBTRN01C | Spring Batch job `verifyDailyTransactionsJob` (`batch-transaction`) |
| CBTRN02C | Spring Batch job `postTransactionsJob` |
| CBTRN03C | Spring Batch job `transactionReportJob` with `ReportLines` |

Every rule above is implemented in the corresponding job. The rejection reasons of BR-02 to BR-05
keep their codes and texts, the accumulation of BR-09 to BR-12 is `BigDecimal` arithmetic at scale
2, and the 133-character lines, the twenty-line page, the three totals and the header block of
CBTRN03C are produced by `ReportLines` and asserted character-for-character by `ReportLinesTest`.
`TransactionBatchIntegrationTest` runs the three jobs over the seeded data.

Deltas, all registered in `11-unsupported-construct-register.md` and
`12-consistency-model-delta-register.md`:

- The abends of BR-06 (verify), BR-17 (post) and BR-11 (report) become a failed step with the
  status in the message; the container exit code replaces the abend code, and the JCL condition
  codes are carried by the job's exit status instead.
- BR-14's `OPEN OUTPUT` replacement of the whole transaction file becomes a save per posted
  transaction into the `transaction` table, keyed on the daily transaction id, so re-running a day
  rewrites the same rows instead of replacing the file.
- BR-11/BR-12, the account balance update, is the one cross-context step: the account lives in the
  account service, so `PostedTransactionWriter` calls
  `POST /api/accounts/{id}/balance-adjustments` with an idempotency key derived from the
  transaction id. A chunk retried after a failure between the call and the commit therefore cannot
  add the amount twice, which the source did not need because all three files were in one unit of
  recovery. BR-13's window, where a transaction is written although its account was not updated,
  narrows to a failed adjustment call, which fails the chunk before the transaction row is saved.
- BR-09 of the report is the one rule the target does not reproduce literally. The source's
  end-of-file branch adds `TRAN-AMT` to the page and account totals once more although no record
  was read, so the last page total, the last account total and the grand total are each overstated
  by the last transaction's amount. `TransactionReportWriter` prints the closing page total and
  grand total without that extra add, so its totals equal the sum of the printed detail lines. The
  deviation is deliberate and is registered in `17-limitations-and-review-required.md` for business
  sign-off, because reproducing it would mean shipping a report whose totals do not add up.
- CBTRN03C's date parameter file becomes the `report_request` row written by `CORPT00C`'s target
  (`BRD-CORPT00C.md`, BR-11), and the report is persisted rather than spooled.
