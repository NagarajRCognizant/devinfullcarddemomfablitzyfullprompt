# Business Rules — COBIL00C (online bill payment)

Source: `app/cbl/COBIL00C.cbl` (572 lines) with map `app/bms/COBIL00.bms`, the account layout
`app/cpy/CVACT01Y.cpy`, the cross-reference `app/cpy/CVACT03Y.cpy` and the transaction layout
`app/cpy/CVTRA05Y.cpy`. Transaction `CB00`, menu option 10 (`BRD-COMEN01C.md`, BR-01).

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | An account id is required | A bill payment cannot be started without an account id; a blank one is refused with "Acct ID can NOT be empty...". | `PROCESS-ENTER-KEY` `EVALUATE TRUE WHEN ACTIDINI = SPACES OR LOW-VALUES` | Validation | Business-derived | — |
| BR-02 | A selected account is carried in | An account selected on the previous screen is filled in and processed on entry without being re-keyed. | `MAIN-PARA` (`IF CDEMO-CB00-TRN-SELECTED NOT = SPACES AND LOW-VALUES`) | Control Flow | Business-derived | — |
| BR-03 | The account must exist | An account id that is not on the account master is refused with "Account ID NOT found..."; any other read failure reports "Unable to lookup Account...". | `READ-ACCTDAT-FILE` `EVALUATE WS-RESP-CD` | Exception Handling | Business-derived | — |
| BR-04 | The current balance is shown | The account's current balance is displayed on the screen before the operator confirms. | `PROCESS-ENTER-KEY` (`MOVE ACCT-CURR-BAL TO WS-CURR-BAL`, then to `CURBALI`) | Formatting/Conversion | Business-derived | — |
| BR-05 | There must be something to pay | An account whose current balance is zero or negative is refused with "You have nothing to pay...". | `PROCESS-ENTER-KEY` (`IF ACCT-CURR-BAL <= ZEROS AND ACTIDINI NOT = SPACES AND LOW-VALUES`) | Validation | Business-derived | A credit balance is refused with the same message as a zero balance |
| BR-06 | Payment is confirmed before it happens | The payment is made only when the operator answers "Y"; "N" clears the screen, a blank asks "Confirm to make a bill payment...", anything else is refused with "Invalid value. Valid values are (Y/N)...". | `PROCESS-ENTER-KEY` `EVALUATE CONFIRMI` | Control Flow | Business-derived | — |
| BR-07 | The payment is for the whole balance | The payment amount is always the account's full current balance; no partial amount can be entered. | `PROCESS-ENTER-KEY` (`MOVE ACCT-CURR-BAL TO TRAN-AMT`) | Calculation | Business-derived | There is no field for a part payment; changing this is a business decision |
| BR-08 | The payment is charged to the account's card | The payment transaction is written against the card the account cross-reference holds for the account. | `READ-CXACAIX-FILE`, `MOVE XREF-CARD-NUM TO TRAN-CARD-NUM` | Derivation/Default | Business-derived | A missing cross-reference is reported as "Account ID NOT found..." although the account itself exists |
| BR-09 | The new transaction id is the highest on file plus one | The payment is recorded under an id one higher than the highest transaction id on file. | `STARTBR-TRANSACT-FILE` / `READPREV-TRANSACT-FILE` from `HIGH-VALUES`, then `ADD 1 TO WS-TRAN-ID-NUM` | Calculation | Business-derived | Two operators paying at the same time can compute the same id |
| BR-10 | The payment is classified as a POS bill payment | Every payment is written with type code "02", category code 2, source "POS TERM" and description "BILL PAYMENT - ONLINE". | `PROCESS-ENTER-KEY` literal `MOVE`s into `TRAN-RECORD` | Derivation/Default | Hard-coded literal | Four hard-coded classification literals; business sign-off required before the data is used for reporting |
| BR-11 | The payment merchant is a fixed placeholder | Every payment records merchant id 999999999, merchant name "BILL PAYMENT" and merchant city and zip "N/A". | `PROCESS-ENTER-KEY` literal `MOVE`s | Derivation/Default | Hard-coded literal | Placeholder merchant data reaches the transaction file and therefore the statements and reports |
| BR-12 | Both timestamps are the time of the payment | The origination and processing timestamps of the payment are both the current date and time, to the second. | `GET-CURRENT-TIMESTAMP` (`ASKTIME`/`FORMATTIME`, milliseconds set to zero) | Derivation/Default | Business-derived | Milliseconds are always zero |
| BR-13 | The balance is reduced by the payment | After the payment the account's current balance is its previous balance less the payment amount, which leaves it at zero. | `PROCESS-ENTER-KEY` `COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT`, `UPDATE-ACCTDAT-FILE` | Calculation | Business-derived | — |
| BR-14 | The transaction and the balance change belong together | The payment transaction is written and the account balance rewritten in the same unit of work, so either both happen or neither does. | `WRITE-TRANSACT-FILE` then `UPDATE-ACCTDAT-FILE` within one CICS task | Control Flow | Business-derived | A failed rewrite after a successful write is reported as "Unable to Update Account..." and backed out only by the implicit task rollback |
| BR-15 | Clear empties the screen | The clear key blanks the account id, the balance and the confirmation field. | `MAIN-PARA` `WHEN DFHPF4`, `CLEAR-CURRENT-SCREEN` | Control Flow | Business-derived | — |
| BR-16 | Exit returns to the caller | The exit key returns to the calling program, or to the main menu when there is none. | `MAIN-PARA` `WHEN DFHPF3`, `RETURN-TO-PREV-SCREEN` | Control Flow | Business-derived | — |
| BR-17 | Any other key is refused | A key other than Enter, exit or clear redisplays the screen with the standard invalid-key message. | `MAIN-PARA` `WHEN OTHER` | Exception Handling | Business-derived | — |
| BR-18 | Entry without a communication area returns to sign-on | Reaching the program without any prior context returns the operator to sign-on. | `MAIN-PARA` (`IF EIBCALEN = 0`) | Control Flow | Business-derived | — |

## Exception scenarios

`DFHRESP(NORMAL)` is success. `DFHRESP(NOTFND)` on the account, the cross-reference or the
transaction browse produces the not-found message of that paragraph. Any other response is
displayed on the console as `RESP:`/`REAS:` and reported to the operator as "Unable to lookup
Account...", "Unable to lookup XREF AIX file...", "Unable to lookup Transaction..." or "Unable to
Update Account...". No path abends.

## Target implementation

`BillPaymentService` + `POST /api/bill-payments` + React `BillPaymentScreen`. BR-01 to BR-13 and
BR-15 to BR-18 are implemented unchanged, with the literals of BR-10 and BR-11 held as constants
and asserted by `BillPaymentServiceTest`.

BR-14 is the one consistency delta. The transaction belongs to the transaction context and the
balance to the account context, so the target writes the payment transaction in its own local
database transaction and adjusts the balance through
`POST /api/accounts/{id}/balance-adjustments` on the account service, authenticated as the
`carddemo-transaction-svc` service account. The client supplies an idempotency key with the payment and the same key is carried on the
adjustment,
so a retry cannot double-adjust, and a failed adjustment compensates by reversing the payment; both
paths are covered by `BillPaymentServiceTest` and `TransactionApiIntegrationTest`. The delta is
registered as CMD-16 in `12-consistency-model-delta-register.md`.
