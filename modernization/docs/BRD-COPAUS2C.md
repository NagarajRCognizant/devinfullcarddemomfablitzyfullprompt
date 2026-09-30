# Business Rules — COPAUS2C (Fraud report recording, DB2 table AUTHFRDS)

Source: `app/app-authorization-ims-db2-mq/cbl/COPAUS2C.cbl` (244 lines). Called by `COPAUS1C`
through `EXEC CICS LINK` with the fraud COMMAREA. Copybooks: `CIPAUDTY` (authorization segment
carried in the COMMAREA), `AUTHFRDS` DCLGEN (`dcl/AUTHFRDS.dcl`). Data: DB2 table
`CARDDEMO.AUTHFRDS` (`ddl/AUTHFRDS.ddl`) with unique index `XAUTHFRD` on card number and
authorization timestamp. CICS resources: DB2ENTRY `DB201PLN`, DB2TRAN `CPVDTRAN`.

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | Fraud report date is the current date | The fraud report date recorded on the authorization is today's date in MM/DD/YY form with separators. | `MAIN-PARA` (89) lines 91-101 `EXEC CICS FORMATTIME MMDDYY DATESEP` | Derivation/Default | Business-derived | Two-digit year (UCR-23) |
| BR-02 | The fraud row is keyed by card number and authorization timestamp | A fraud report is identified by the card number and the authorization's own timestamp, rebuilt from the original authorization date and the stored authorization time. | `MAIN-PARA` lines 103-112, `ddl/XAUTHFRD.ddl` unique index | Data Selection | Business-derived | — |
| BR-03 | The authorization timestamp is recovered from the complemented time | The authorization time is recovered by subtracting the stored complemented time from 999999999, giving hours, minutes, seconds and milliseconds. | `MAIN-PARA` line 107 `COMPUTE WS-AUTH-TIME = 999999999 - PA-AUTH-TIME-9C` | Calculation | Hard-coded literal (999999999) | Same complement device as the authorization key (COPAUA0C BR-31) |
| BR-04 | Fraud row content | The fraud row carries the card number, authorization timestamp, authorization type, card expiry date, message type and source, authorization id code, response code and reason, processing code, transaction amount, approved amount, merchant category code, acquirer country code, POS entry mode, merchant id, name, city, state and zip, transaction id, match status, the fraud action, the fraud report date, the account id and the customer id. | `MAIN-PARA` lines 113-197 | Formatting/Conversion | Business-derived | — |
| BR-05 | The stored fraud report date is the database's current date | The fraud report date stored in the fraud table is the database server's current date, not the formatted date shown on the screen. | `MAIN-PARA` line 194 `,CURRENT DATE` | Derivation/Default | Business-derived | Two dates for the same event: screen and table can disagree — REVIEW REQUIRED |
| BR-06 | A first fraud report is inserted | The first fraud report for an authorization is inserted into the fraud table and reported as `ADD SUCCESS`. | `MAIN-PARA` lines 141-200, `IF SQLCODE = ZERO` | Control Flow | Business-derived | — |
| BR-07 | A repeated fraud report updates the existing row | When a fraud report already exists for the card number and authorization timestamp, its fraud action and report date are updated instead, and the result is reported as `UPDT SUCCESS`. | `MAIN-PARA` lines 203-206 (`SQLCODE = -803`), `FRAUD-UPDATE` (221) | Control Flow | Business-derived | Duplicate-key handling is the toggle path used by COPAUS1C BR-09 |
| BR-08 | Any other database failure is reported as a failure | Any other database outcome is reported as a failure carrying the database code and state, in the form ` SYSTEM ERROR DB2: CODE:nnnn, STATE: nnnnn` for an insert and ` UPDT ERROR DB2: CODE:nnnn, STATE: nnnnn` for an update. | `MAIN-PARA` lines 207-215, `FRAUD-UPDATE` lines 234-243 | Exception Handling | Business-derived | Technical codes surfaced to the user by COPAUS1C BR-11 |
| BR-09 | Nothing is committed by this program | The program does not commit; the caller's unit of work decides whether the fraud row survives. | no `SYNCPOINT` in the program; `EXEC CICS RETURN` at line 217 | Control Flow | Business-derived | The rollback path of COPAUS1C BR-14 depends on this — register entry CMD-13 |

## Exception scenarios (DB2)

| Condition | Source treatment | Target treatment |
|---|---|---|
| `SQLCODE = 0` on insert | success, `ADD SUCCESS` | `FraudReportRepository.save` on a new key |
| `SQLCODE = -803` (duplicate key) | update the existing row, `UPDT SUCCESS` | `save` on the existing key (JPA merge) |
| `SQLCODE = 0` on update | success, `UPDT SUCCESS` | same |
| any other `SQLCODE` | failure message with code and state | `DataAccessException` → transaction rollback, `ApiError` |

## Target implementation

`FraudMarkingService.toggleFraud` (the insert/update decision becomes a single upsert on the
`auth_fraud` primary key), `FraudReportEntity`, `FraudReportId`, `FraudStatus`,
`AuthorizationKey.fraudTimestamp`. The DB2 table becomes table `auth_fraud` in database
`carddemo_authorization` — column-by-column mapping in `05-data-mapping.md`.
