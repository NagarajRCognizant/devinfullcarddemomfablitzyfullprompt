# Business Rules — COTRN00C / COTRN01C / COTRN02C (transaction list, view and add)

Sources: `app/cbl/COTRN00C.cbl` (699 lines), `app/cbl/COTRN01C.cbl` (330 lines),
`app/cbl/COTRN02C.cbl` (783 lines) with maps `COTRN00.bms`, `COTRN01.bms`, `COTRN02.bms`, the
transaction layout `app/cpy/CVTRA05Y.cpy` and the cross-reference `app/cpy/CVACT03Y.cpy`.
Transactions `CT00`, `CT01`, `CT02`. Reached from menu options 6, 7 and 8
(`BRD-COMEN01C.md`, BR-01).

## COTRN00C — transaction list

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | A page shows ten transactions | The list presents at most ten transactions at a time, in transaction-id order. | `PROCESS-PAGE-FORWARD` (`WS-IDX` 1-10), `POPULATE-TRAN-DATA` `EVALUATE WS-IDX WHEN 1 … WHEN 10` | Data Selection | Business-derived | Ten is a literal repeated in ten branches |
| BR-02 | Each row shows id, date, description and amount | For every listed transaction the operator sees its id, its origination date, its description and its amount. | `POPULATE-TRAN-DATA` moves to `TRNID00n`, `TDT00n`, `TDESC00n`, `TAMT00n` | Formatting/Conversion | Business-derived | — |
| BR-03 | Empty rows are cleared | Rows with no transaction are blanked rather than left with the previous page's content. | `INITIALIZE-TRAN-DATA` `EVALUATE WS-IDX` | Formatting/Conversion | Business-derived | — |
| BR-04 | Browsing starts at the keyed transaction id | If the operator keys a transaction id the list starts there; a blank id starts at the lowest id. | `PROCESS-ENTER-KEY` (`MOVE LOW-VALUES TO TRAN-ID` when blank, else `MOVE TRNIDINI TO TRAN-ID`) | Data Selection | Business-derived | — |
| BR-05 | The keyed transaction id must be numeric | A non-numeric transaction id is refused with "Tran ID must be Numeric ...". | `PROCESS-ENTER-KEY` (`IF TRNIDINI IS NUMERIC … ELSE`) | Validation | Business-derived | — |
| BR-06 | Only "S" selects a transaction | Marking a row with "S" or "s" opens the transaction; any other mark is refused with "Invalid selection. Valid value is S". | `PROCESS-ENTER-KEY` `EVALUATE CDEMO-CT00-TRN-SEL-FLG WHEN 'S' WHEN 's' … WHEN OTHER` | Validation | Business-derived | The refusal branch has its `SEND` and its EOF set commented out, so the message is shown only on the next send — dead code recorded, not removed |
| BR-07 | The first marked row wins | When several rows are marked, the topmost marked row is the one acted on. | `PROCESS-ENTER-KEY` first `EVALUATE TRUE` over `SEL0001I`…`SEL0010I` in row order | Control Flow | Business-derived | — |
| BR-08 | Selecting a transaction opens the view screen | A selected transaction is passed to the transaction view program, which is recorded as called from this list. | `PROCESS-ENTER-KEY` `XCTL PROGRAM('COTRN01C')` with the COMMAREA | Control Flow | Business-derived | — |
| BR-09 | Page forward stops at the last page | Paging forward past the last transaction reports "You are already at the bottom of the page...". | `PROCESS-PF8-KEY` (`IF NEXT-PAGE-YES … ELSE`) | Control Flow | Business-derived | — |
| BR-10 | Page backward stops at the first page | Paging backward from page one reports "You are already at the top of the page...". | `PROCESS-PF7-KEY` (`IF CDEMO-CT00-PAGE-NUM > 1 … ELSE`) | Control Flow | Business-derived | — |
| BR-11 | The page number follows the direction of browsing | Paging forward increases the page number by one, paging backward decreases it. | `PROCESS-PAGE-FORWARD` / `PROCESS-PAGE-BACKWARD` `COMPUTE CDEMO-CT00-PAGE-NUM = …` | Calculation | Business-derived | — |
| BR-12 | The first and last id of the page are remembered | The first and last transaction ids shown are kept so the next page starts after the last one and the previous page ends before the first one. | `CDEMO-CT00-TRNID-FIRST` / `CDEMO-CT00-TRNID-LAST` set in the paging paragraphs | Derivation/Default | Business-derived | Browsing is by key, so transactions inserted between two requests change what the next page shows |
| BR-13 | End of file ends the browse | Reaching the end of the transaction file stops the page; a read failure other than end of file reports "Unable to lookup Transaction...". | `READNEXT-TRANSACT-FILE` / `READPREV-TRANSACT-FILE` `EVALUATE WS-RESP-CD` | Exception Handling | Business-derived | — |
| BR-14 | Exit returns to the caller | The exit key returns to the calling program, or to the main menu when there is none. | `MAIN-PARA` `WHEN DFHPF3`, `RETURN-TO-PREV-SCREEN` | Control Flow | Business-derived | — |
| BR-15 | Any other key is refused | A key other than Enter, exit, page forward or page backward redisplays the list with the standard invalid-key message. | `MAIN-PARA` `WHEN OTHER` | Exception Handling | Business-derived | — |

## COTRN01C — transaction view

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | A transaction id is required | An empty transaction id is refused with "Tran ID can NOT be empty...". | `PROCESS-ENTER-KEY` `EVALUATE TRUE WHEN TRNIDINI = SPACES OR LOW-VALUES` | Validation | Business-derived | — |
| BR-02 | A selected transaction is shown without re-keying | When the list passed a selected transaction, it is displayed on entry without the operator keying it again. | `MAIN-PARA` (`IF CDEMO-CT01-TRN-SELECTED NOT = SPACES AND LOW-VALUES`) | Control Flow | Business-derived | — |
| BR-03 | An unknown transaction is reported | A transaction id that is not on file is refused with "Transaction ID NOT found...". | `READ-TRANSACT-FILE` `WHEN DFHRESP(NOTFND)` | Exception Handling | Business-derived | — |
| BR-04 | Any other read failure is reported | Any other failure reading the transaction reports "Unable to lookup Transaction...". | `READ-TRANSACT-FILE` `WHEN OTHER` | Exception Handling | Business-derived | The response code is only displayed to the operator console, not shown to the operator |
| BR-05 | The whole transaction is displayed | The screen shows id, card number, type, category, source, description, amount, origination and processing timestamps and the merchant id, name, city and zip. | `PROCESS-ENTER-KEY` moves from `TRAN-RECORD` to the map fields | Formatting/Conversion | Business-derived | — |
| BR-06 | Clear empties the screen | The clear key blanks every field, including the keyed transaction id. | `MAIN-PARA` `WHEN DFHPF4`, `CLEAR-CURRENT-SCREEN`, `INITIALIZE-ALL-FIELDS` | Control Flow | Business-derived | — |
| BR-07 | Browse returns to the list | The browse key returns to the transaction list. | `MAIN-PARA` `WHEN DFHPF5` | Control Flow | Business-derived | — |

## COTRN02C — transaction add

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | Either an account or a card must be given | The operator must enter an account id or a card number; entering neither is refused with "Account or Card Number must be entered...". | `VALIDATE-INPUT-KEY-FIELDS` `EVALUATE TRUE … WHEN OTHER` | Validation | Business-derived | Entering both is accepted and the account id is used |
| BR-02 | The account id must be numeric | A non-numeric account id is refused with "Account ID must be Numeric...". | `VALIDATE-INPUT-KEY-FIELDS` (`IF ACTIDINI IS NOT NUMERIC`) | Validation | Business-derived | — |
| BR-03 | The card number must be numeric | A non-numeric card number is refused with "Card Number must be Numeric...". | `VALIDATE-INPUT-KEY-FIELDS` (`IF CARDNINI IS NOT NUMERIC`) | Validation | Business-derived | — |
| BR-04 | The key must exist on the cross-reference | The account id is resolved to its card through the account cross-reference, and the card number through the card cross-reference; a key that is not there is refused. | `READ-CXACAIX-FILE` / `READ-CXREF-FILE` `WHEN DFHRESP(NOTFND)` | Validation | Business-derived | — |
| BR-05 | Every transaction field is required | Type code, category code, source, description, amount, origination date, processing date, merchant id, merchant name, merchant city and merchant zip must all be entered; each missing field has its own message in that order. | `VALIDATE-INPUT-DATA-FIELDS` first `EVALUATE TRUE` (eleven `WHEN` branches) | Validation | Business-derived | The order of the branches is the order the operator is told about the errors |
| BR-06 | Type and category codes are numeric | A non-numeric type code or category code is refused with "Type CD must be Numeric..." or "Category CD must be Numeric...". | `VALIDATE-INPUT-DATA-FIELDS` second `EVALUATE TRUE` | Validation | Business-derived | — |
| BR-07 | The amount has a fixed signed format | The amount must be keyed as a sign, eight digits, a decimal point and two digits (-99999999.99). | `VALIDATE-INPUT-DATA-FIELDS` third `EVALUATE TRUE` (positions 1, 2:8, 10, 11:2) | Validation | Business-derived | The format, not a range, is checked: any amount that fits the mask is accepted |
| BR-08 | Both dates have a fixed format | Origination and processing dates must be keyed as YYYY-MM-DD. | `VALIDATE-INPUT-DATA-FIELDS` fourth and fifth `EVALUATE TRUE` (positions 1:4, 5, 6:2, 8, 9:2) | Validation | Business-derived | — |
| BR-09 | Both dates must be real dates | Each date is checked by the common date routine; a date the routine rejects is refused with "Orig Date - Not a valid date..." or "Proc Date - Not a valid date...". | `VALIDATE-INPUT-DATA-FIELDS` `CALL 'CSUTLDTC'`, `IF CSUTLDTC-RESULT-SEV-CD = '0000'` | Validation | Business-derived | Severity `0000` with message `2513` is treated as valid, so one non-zero outcome of the routine is deliberately accepted |
| BR-10 | The merchant id is numeric | A non-numeric merchant id is refused with "Merchant ID must be Numeric...". | `VALIDATE-INPUT-DATA-FIELDS` (`IF MIDI IS NOT NUMERIC`) | Validation | Business-derived | — |
| BR-11 | Adding is confirmed before it happens | The transaction is written only after the operator answers "Y"; "N" abandons it, a blank asks "Confirm to add this transaction...", anything else is refused with "Invalid value. Valid values are (Y/N)...". | `PROCESS-ENTER-KEY` `EVALUATE CONFIRMI` | Control Flow | Business-derived | — |
| BR-12 | The new transaction id is the highest on file plus one | The id of the new transaction is one more than the highest id currently on the transaction file. | `ADD-TRANSACTION` (backwards browse then `ADD 1 TO WS-TRAN-ID-N`) | Calculation | Business-derived | Two operators adding at the same time can compute the same id |
| BR-13 | The amount is stored as a number | The keyed amount is converted from its edited form to a signed number before it is stored. | `ADD-TRANSACTION` `COMPUTE WS-TRAN-AMT-N = FUNCTION NUMVAL-C(TRNAMTI)` | Formatting/Conversion | Business-derived | — |
| BR-14 | The card of the resolved cross-reference is stored | The transaction is written against the card number found on the cross-reference, not against a card the operator types independently. | `ADD-TRANSACTION` (`MOVE XREF-CARD-NUM TO TRAN-CARD-NUM`) | Derivation/Default | Business-derived | — |
| BR-15 | A successful add is confirmed and the screen is cleared | After a successful write the operator is told the transaction was added and the input fields are emptied for the next one. | `ADD-TRANSACTION` success path, `CLEAR-CURRENT-SCREEN` | Control Flow | Business-derived | — |
| BR-16 | The previous transaction can be copied | The copy key repopulates the screen from the last transaction the operator added. | `MAIN-PARA` `WHEN DFHPF5`, `COPY-LAST-TRAN-DATA` | Derivation/Default | Business-derived | — |

## Exception scenarios

Reads and writes use CICS responses rather than file status: `DFHRESP(NORMAL)` is success,
`DFHRESP(NOTFND)` produces the not-found message of the paragraph, `DFHRESP(ENDFILE)` ends a browse,
and every other response produces the "Unable to …" message of that paragraph after displaying the
response and reason codes on the console. No path abends: the operator always gets the screen back
with a message.

## Target implementation

| Source | Target |
|---|---|
| COTRN00C | `TransactionListService`, `GET /api/transactions`, React `TransactionListScreen` |
| COTRN01C | `TransactionViewService`, `GET /api/transactions/{tranId}`, React `TransactionViewScreen` |
| COTRN02C | `TransactionAddService`, `POST /api/transactions`, React `TransactionAddScreen` |

The COMMAREA page state of BR-12 (list) becomes explicit `direction`, `firstTranId`, `lastTranId`
and `pageNumber` request parameters and a `nextPageAvailable` flag on the response, so PF7 and PF8 are two ordinary requests
(`08-api.md` §8.4.5). The ten-row page, the row content, the selection rule, the numeric edits, the
amount and date masks, the date-routine outcome of BR-09 and every message text are preserved
verbatim in `TransactionMessages` and asserted by `TransactionListServiceTest`,
`TransactionViewServiceTest` and `TransactionAddServiceTest`. BR-12 (add) is implemented as
`max(tranId) + 1` inside the same database transaction as the insert, so the source's race is
closed by the unique key rather than by extra logic; the difference is recorded in
`12-consistency-model-delta-register.md`.
