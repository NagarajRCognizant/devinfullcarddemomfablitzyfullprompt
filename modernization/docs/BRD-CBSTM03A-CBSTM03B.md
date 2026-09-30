# Business Rules — CBSTM03A and CBSTM03B (account statements, batch job CREASTMT)

Source: `app/cbl/CBSTM03A.CBL` (924 lines, statement writer) and `app/cbl/CBSTM03B.CBL` (230
lines, the file handler it calls), driven by `app/jcl/CREASTMT.JCL`. Copybooks: CVACT03Y
(cross-reference), CVCUS01Y (customer), CVACT01Y (account), CVTRA05Y (transaction), COSTM01
(statement lines), CSSTM01/CSSTM02 (fixed HTML lines), CSUTLDWY. Inputs: TRNXFILE, XREFFILE,
CUSTFILE, ACCTFILE. Outputs: STMTFILE (80 columns, plain text) and HTMLFILE (100 columns, HTML).

## 1. CBSTM03A — statement production

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | One statement per cross-reference entry | A statement is produced for every card cross-reference entry, in the order the cross-reference holds them. | `1000-MAINLINE` `PERFORM UNTIL END-OF-FILE = 'Y'` with `1000-XREFFILE-GET-NEXT` | Data Selection | Business-derived | A customer holding several cards receives one statement per card, not one per account |
| BR-02 | Statement parties are read per entry | For each cross-reference entry the customer and the account it names are read by key; either read failing ends the run abnormally. | `2000-CUSTFILE-GET`, `3000-ACCTFILE-GET` (`M03B-READ-K` via CBSTM03B), `EVALUATE WS-M03B-RC WHEN OTHER` → `9999-ABEND-PROGRAM` | Exception Handling | Business-derived | A cross-reference entry naming a missing customer or account stops the whole run |
| BR-03 | Statement header content | Each statement shows the customer's full name, the three address lines with state, country and postcode, the account number, the current balance and the customer's credit score. | `5000-CREATE-STATEMENT` `STRING` of `CUST-FIRST-NAME`/`CUST-MIDDLE-NAME`/`CUST-LAST-NAME`, `MOVE CUST-ADDR-LINE-…`, `MOVE ACCT-ID TO ST-ACCT-ID`, `MOVE ACCT-CURR-BAL TO ST-CURR-BAL`, `MOVE CUST-FICO-CREDIT-SCORE TO ST-FICO-SCORE` | Formatting/Conversion | Business-derived | The name and address are built with `DELIMITED BY ' '`, so a name containing a space is truncated at that space |
| BR-04 | Every statement is produced in both forms | Each statement is written both as the plain 80-column form and as the HTML form. | `5000-CREATE-STATEMENT` writes `ST-LINE0…ST-LINE13` to STMTFILE and performs `5100-WRITE-HTML-HEADER` / `5200-WRITE-HTML-NMADBS` against HTMLFILE | Control Flow | Business-derived | — |
| BR-05 | Transactions are selected by card | A statement lists the transactions of the card the cross-reference entry names, and no others. | `4000-TRNXFILE-GET`: `IF XREF-CARD-NUM = WS-CARD-NUM (CR-JMP)` | Data Selection | Business-derived | — |
| BR-06 | Transaction search stops once the card is passed | The search through the loaded transactions stops as soon as a card number higher than the one wanted is reached. | `4000-TRNXFILE-GET`: `PERFORM VARYING CR-JMP … UNTIL CR-JMP > CR-CNT OR (WS-CARD-NUM (CR-JMP) > XREF-CARD-NUM)` | Control Flow | Business-derived | Depends on both the cross-reference and the transaction file being in card-number order; out-of-order input silently omits transactions |
| BR-07 | Statement total | The statement total is the sum of the amounts of the transactions listed on it. | `4000-TRNXFILE-GET`: `ADD TRNX-AMT TO WS-TOTAL-AMT`, `MOVE WS-TOTAL-AMT TO ST-TOTAL-TRAMT` | Calculation | Business-derived | The total is reset per statement in `1000-MAINLINE` (`MOVE ZERO TO WS-TOTAL-AMT`) |
| BR-08 | Transaction line content | Each listed transaction shows its identifier and the remaining transaction detail as recorded. | `6000-WRITE-TRANS` with `TRNX-ID` / `TRNX-REST` | Formatting/Conversion | Business-derived | — |
| BR-09 | The statement is closed off with a total and a footer | After the transactions, the statement is closed with the total line and the closing lines in both forms. | `4000-TRNXFILE-GET` writes `ST-LINE12`, `ST-LINE14A`, `ST-LINE15` and the closing HTML lines | Formatting/Conversion | Business-derived | — |
| BR-10 | Transactions are loaded before the statements are written | The transactions are read once into a working table grouped by card, and the statements are then produced from that table. | `8500-READTRNX-READ` loading `WS-TRNX-TABLE`; `0000-START` `EVALUATE WS-FL-DD … WHEN 'READTRNX'` | Data Selection | Business-derived | The table holds at most **51 cards × 10 transactions**; there is no overflow check, so a 52nd card or an 11th transaction writes outside the table |
| BR-11 | Transactions are grouped by card as they are loaded | Consecutive transactions with the same card number are counted against that card; a change of card number starts a new card group. | `8500-READTRNX-READ`: `IF WS-SAVE-CARD = TRNX-CARD-NUM ADD 1 TO TR-CNT ELSE MOVE TR-CNT TO WS-TRCT (CR-CNT), ADD 1 TO CR-CNT, MOVE 1 TO TR-CNT` | Control Flow | Business-derived | The count of the **last** card group is never stored in `WS-TRCT`, so the last card's transactions are not listed — see §4 |
| BR-12 | End of the cross-reference ends the run | When the cross-reference is exhausted all four inputs and both outputs are closed and the run completes. | `1000-XREFFILE-GET-NEXT` `WHEN '10' MOVE 'Y' TO END-OF-FILE`; `9100`–`9400` closes, `CLOSE STMT-FILE HTML-FILE` | Control Flow | Business-derived | File status treated as business logic |
| BR-13 | Any other read outcome ends the run abnormally | A read outcome other than success or end of file is reported with the handler's return code and the run ends abnormally. | `EVALUATE WS-M03B-RC WHEN OTHER` in `1000`/`2000`/`3000`/`4000` paragraphs → `9999-ABEND-PROGRAM` | Exception Handling | Business-derived | — |
| BR-14 | The job environment is reported | The job name, the step name and the data-set names allocated to the step are recorded in the job log before processing starts. | `PROCEDURE DIVISION` prologue: `SET ADDRESS OF PSA-BLOCK`, `TCB-BLOCK`, `TIOT-BLOCK`, the `PERFORM UNTIL END-OF-TIOT` loop over the allocated DD names | Control Flow | Business-derived | Operator diagnostics only; pure MVS control-block navigation with no business meaning — see §3 |

## 2. CBSTM03B — the file handler

`CBSTM03B` is called by `CBSTM03A` for every file operation. It is a mechanism, not a place where
business decisions are made; its two rules are:

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | The caller names the file and the operation | The caller states which of the four files to act on and whether to open it, read the next record, read a record by key, or close it; an unrecognised file name is ignored and control returns. | `0000-START` `EVALUATE LK-M03B-DD … WHEN OTHER GO TO 9999-GOBACK`; `M03B-OPEN` / `M03B-READ` / `M03B-READ-K` / `M03B-CLOSE` 88-levels | Control Flow | Business-derived | An unrecognised file name returns with the previous return code left in the interface area — the caller cannot distinguish it from success |
| BR-02 | The outcome is returned to the caller | Every operation returns the file status it produced, and the caller decides what it means. | `1900-EXIT` / `2900-EXIT` / `3900-EXIT` / `4900-EXIT`: `MOVE …-STATUS TO LK-M03B-RC` | Exception Handling | Business-derived | The keyed reads of `3000`/`4000` have no `INVALID KEY` phrase, so a missing record is reported only as status `'23'` |

## 3. Retired mechanics

- The MVS control-block walk of BR-14 (PSA → TCB → TIOT) has no target equivalent: the target logs
  the job and step name from the Spring Batch execution context. Recorded in
  `11-unsupported-construct-register.md` as UCR-40.
- `ALTER 8100-FILE-OPEN TO PROCEED TO …` and the `GO TO` dispatch of `0000-START` are the source's
  way of parameterising one open routine over four files; the target has one repository or client
  call per file, so the altered `GO TO` disappears. Recorded as UCR-40, not as a business rule.
- `CBSTM03B` itself is Consolidated in `13-artifact-disposition.md`: its four file operations are
  the repository and account-service calls the statement job makes directly.

## 4. Deliberate deviations and risk register

- **BR-11, the last card group is dropped.** The loader stores a card's transaction count only when
  the card number *changes*, so the final group's count is left at its initialised value and the
  last card on the file is listed with no transactions. The target reads a card's transactions from
  the database by card number, so every card is listed with all of its transactions. This is a
  corrected source defect and is REVIEW REQUIRED as UCR-34.
- **BR-10, the 51 × 10 table.** The target has no table and no limit: the transactions of a card are
  a query. An account with more than ten transactions therefore produces a longer statement than the
  mainframe would have produced — a difference that is visible in any parity comparison and is
  recorded with the deviation above.
- **BR-03, `DELIMITED BY ' '`.** Preserved: the target's `StatementLines` truncates a name
  component at an embedded space exactly as the `STRING` did.
- **BR-01, one statement per card.** Preserved: `StatementPartyReader` pages the cross-reference,
  not the account.

## 5. Target implementation

`StatementJobConfig` (job `accountStatementJob`, step `accountStatementStep`), `StatementPartyReader`
(the cross-reference walk of BR-01 and the customer/account reads of BR-02, as one paged call on
`GET /api/statement-parties` in the account service), `StatementRenderer` (BR-03 … BR-09),
`StatementLines` (the 80-column form), `StatementHtmlLines` (the HTML form) and `StatementWriter`,
which writes both forms for each statement inside the same chunk transaction. Tests:
`StatementRendererTest`, `StatementLinesTest`, `TransactionBatchIntegrationTest`.
