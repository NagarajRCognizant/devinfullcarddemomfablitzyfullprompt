# Business Rules — COPAUS1C (Pending authorization detail and fraud marking, CICS trans CPVD)

Source: `app/app-authorization-ims-db2-mq/cbl/COPAUS1C.cbl` (604 lines). Map: `COPAU01`
(`COPAU1A`), symbolic map `cpy-bms/COPAU01.cpy`. Copybooks: `CIPAUDTY`, `PAUTBPCB`, `COCOM01Y`
(COMMAREA, CPVD section), `CSMSG01Y`, `COTTL01Y`, `IMSFUNCS`. Linked program: `COPAUS2C` (DB2
fraud table update). Data: IMS HIDAM `DBPAUTP0` (PSB `PSBPAUTL`).

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | An authorization is identified by account and key | The detail screen needs a numeric account id and a non-blank authorization key; without both, nothing is read and the screen shows no data. | `PROCESS-ENTER-KEY` (208) lines 208-225 | Validation | Business-derived | The error flag is set with no message: the user sees an empty screen (UCR-24) |
| BR-02 | The authorization is read by its key under its account | The authorization is read as the child of the account's pending authorization summary whose key matches the selected key. | `READ-AUTH-RECORD` (431) `EXEC DLI GU SEGMENT(PAUTSUM0) WHERE(ACCNTID=…) SEGMENT(PAUTDTL1) WHERE(PAUT9CTS=…)` | Data Selection | Business-derived | — |
| BR-03 | Displayed fields | The screen shows card number, authorization date, authorization time, approved amount, approval status, decline reason, processing code, POS entry mode, message source, merchant category code, card expiry date, authorization type, transaction id, match status, fraud status, merchant name, id, city, state and zip. | `POPULATE-AUTH-DETAILS` (291) lines 291-359 | Formatting/Conversion | Business-derived | — |
| BR-04 | Date and time display format | The authorization date is shown as MM/DD/YY and the time as HH:MM:SS, taken from the original request values. | `POPULATE-AUTH-DETAILS` lines 297-306 | Formatting/Conversion | Business-derived | Two-digit year (UCR-23) |
| BR-05 | Approval status | An authorization whose response code is `00` is shown as approved (`A`, green), any other response code as declined (`D`, red). | `POPULATE-AUTH-DETAILS` lines 311-317 | Derivation/Default | Business-derived | Colour is presentation only |
| BR-06 | Decline reason text | The reason code is shown with its description: `0000 APPROVED`, `3100 INVALID CARD`, `4100 INSUFFCNT FUND`, `4200 CARD NOT ACTIVE`, `4300 ACCOUNT CLOSED`, `4400 EXCED DAILY LMT`, `5100 CARD FRAUD`, `5200 MERCHANT FRAUD`, `5300 LOST CARD`, `9000 UNKNOWN`; an unlisted code is shown as `9999-ERROR`. | `POPULATE-AUTH-DETAILS` lines 319-328 `SEARCH ALL WS-DECLINE-REASON-TAB`, table `WS-DECLINE-REASON-TAB` (lines 96-120) | Derivation/Default | Hard-coded literal (reason table) | `4400` and `5300` are never produced by COPAUA0C; `SEARCH ALL` requires the table to stay sorted |
| BR-07 | Card expiry display format | The card expiry date is shown as MM/YY. | `POPULATE-AUTH-DETAILS` lines 336-338 | Formatting/Conversion | Business-derived | — |
| BR-08 | Fraud status display | An authorization marked or cleared of fraud is shown as the fraud indicator, a hyphen and the fraud report date; an authorization never marked is shown as a hyphen. | `POPULATE-AUTH-DETAILS` lines 344-350 | Formatting/Conversion | Business-derived | — |
| BR-09 | Fraud marking toggles | Marking fraud on an authorization already confirmed as fraud clears the marking; marking fraud on any other authorization confirms it. | `MARK-AUTH-FRAUD` (230) lines 236-243 | Control Flow | Business-derived | Any value other than confirmed is treated as not-fraud, including the cleared value |
| BR-10 | The fraud report is written to the fraud table first | Marking fraud sends the whole authorization, the account id and the customer id to the fraud reporting program, which records it in the fraud table. | `MARK-AUTH-FRAUD` lines 245-252, `EXEC CICS LINK PROGRAM('COPAUS2C') COMMAREA(WS-FRAUD-DATA)` | Control Flow | Business-derived | — |
| BR-11 | The authorization is updated only if the fraud table was updated | The authorization's fraud marking is stored only when the fraud table update succeeded; otherwise the fraud program's message is shown and all database work is rolled back. | `MARK-AUTH-FRAUD` lines 253-263 | Control Flow | Business-derived | — |
| BR-12 | A failed link rolls back | If the fraud reporting program cannot be called at all, all database work is rolled back. | `MARK-AUTH-FRAUD` lines 260-263 | Exception Handling | Business-derived | No message is issued in this path — REVIEW REQUIRED |
| BR-13 | Fraud marking messages | A successful clearing answers `AUTH FRAUD REMOVED...` and a successful marking answers `AUTH MARKED FRAUD...`. | `UPDATE-AUTH-DETAILS` (520) lines 531-538 | Derivation/Default | Business-derived | — |
| BR-14 | A failed authorization update rolls back | If the authorization cannot be updated, the whole change is rolled back and a system error carrying the database status is shown. | `UPDATE-AUTH-DETAILS` lines 539-550 | Exception Handling | Business-derived | The fraud table row was already committed by the linked program in the failure path — atomicity gap, register entry CMD-13 |
| BR-15 | Successful marking is committed | A successful authorization update is committed immediately. | `UPDATE-AUTH-DETAILS` lines 531-532, `TAKE-SYNCPOINT` (557) | Control Flow | Business-derived | — |
| BR-16 | The next authorization can be shown | Moving to the next authorization reads the authorization that follows the current one under the same account and shows it. | `PROCESS-PF8-KEY` (268) lines 268-289, `READ-NEXT-AUTH-RECORD` (493) | Data Selection | Business-derived | — |
| BR-17 | End of the chain message | Moving past the last authorization of the account answers `Already at the last Authorization...` and leaves the current authorization displayed. | `PROCESS-PF8-KEY` lines 281-286 | Exception Handling | Business-derived | — |
| BR-18 | The screen is re-read after marking | After marking fraud the displayed authorization is the one that was marked, redisplayed from the stored record. | `MARK-AUTH-FRAUD` lines 264-266 | Control Flow | Business-derived | — |
| BR-19 | Read work is committed when the screen is sent | The IMS position is released and the unit of work committed after each read and whenever the screen is sent. | `PROCESS-ENTER-KEY` lines 219-222, `PROCESS-PF8-KEY` lines 276-279, `SEND-AUTHVIEW-SCREEN` (373) | Control Flow | Business-derived | — |
| BR-20 | Entering with no context returns to the summary | Entering the screen without any context returns to the authorization summary screen. | `MAIN-PARA` lines 165-170 | Control Flow | Business-derived | — |
| BR-21 | An unrecognised key is rejected | Any key other than Enter, PF3, PF5 or PF8 redisplays the authorization with the common invalid-key message. | `MAIN-PARA` lines 193-198 | Exception Handling | Business-derived | Terminal-specific mechanism, retired per playbook §8.4.5 |

## Exception scenarios (screen and database)

| Condition | Source treatment | Target treatment |
|---|---|---|
| IMS status blank | continue | detail returned |
| `GE` / `GB` on the next-authorization read | `Already at the last Authorization...` | `nextAuthKey = null` and the same message |
| any other IMS status on read | `System error while reading next Auth: Code:nn` | `ApiError` HTTP 500 with the same message |
| fraud table update failed | fraud program's message, `SYNCPOINT ROLLBACK` | transaction rolled back, `ApiError` carrying the message |
| authorization update failed | rollback and `System error while FRAUD Tagging, ROLLBACK||nn` | transaction rolled back, same message |

## Target implementation

`AuthorizationController.detail` (`GET /api/authorizations/{accountId}/{authKey}`) and
`AuthorizationController.toggleFraud` (`POST /api/authorizations/{accountId}/{authKey}/fraud`),
`AuthorizationInquiryService.detail`, `FraudMarkingService.toggleFraud`, `DeclineReason`,
`FraudStatus`, `AuthorizationDetailResponse`; React `AuthorizationDetailScreen.tsx`. The PF3/PF5/PF8
mechanics are replaced by explicit Back, Mark/Clear fraud and Next authorization actions
(`06-screen-mapping.md`).
