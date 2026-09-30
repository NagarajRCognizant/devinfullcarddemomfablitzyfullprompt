# Consolidated Business Rule Report — CardDemo Credit Card Authorizations

Cross-program view of the 135 rules extracted from the authorization closure
(`app/app-authorization-ims-db2-mq/`). The per-program catalogues hold the full rows (statement,
technical basis, classification, origin, risk flags):

| Program | Role | Rules | Catalogue |
|---|---|---|---|
| `COPAUA0C` | CICS trans CP00, MQ-triggered authorization request processor | BR-01 … BR-38 (38) | `BRD-COPAUA0C.md` |
| `COPAUS0C` | CICS trans CPVS, pending authorization summary | BR-01 … BR-30 (30) | `BRD-COPAUS0C.md` |
| `COPAUS1C` | CICS trans CPVD, authorization detail and fraud marking | BR-01 … BR-21 (21) | `BRD-COPAUS1C.md` |
| `COPAUS2C` | Fraud report recording into DB2 `AUTHFRDS` | BR-01 … BR-09 (9) | `BRD-COPAUS2C.md` |
| `CBPAUP0C` | BMP job `CBPAUP0J`, expired authorization purge | BR-01 … BR-24 (24) | `BRD-CBPAUP0C.md` |
| `PAUDBLOD`, `PAUDBUNL`, `DBUNLDGS` | IMS load and unload utilities | BR-01 … BR-13 (13) | `BRD-PAUDBLOD-PAUDBUNL-DBUNLDGS.md` |

Rule IDs are sequential **per program**, so a rule is cited as `COPAUA0C BR-21`.

## 1. Business capability → FR → BR

| Capability | FR | Requirement | Rules |
|---|---|---|---|
| Authorization decisioning | FR-19 | An authorization request received asynchronously is decisioned and answered to the requester | COPAUA0C BR-01, BR-03 … BR-07, BR-09, BR-22 … BR-25 |
| Authorization decisioning | FR-20 | The cardholder closure (card → account → customer) is resolved before the decision | COPAUA0C BR-11, BR-12, BR-13, BR-14 |
| Authorization decisioning | FR-21 | Available credit is the summary's credit limit less credit balance, or the account's credit limit less current balance when there is no summary | COPAUA0C BR-15, BR-16 |
| Authorization decisioning | FR-22 | A request above available credit, or without a cardholder closure, is declined | COPAUA0C BR-10, BR-17, BR-18 |
| Authorization decisioning | FR-23 | Response code, approved amount and decline reason follow the source code table | COPAUA0C BR-19, BR-20, BR-21, BR-23 |
| Authorization accounting | FR-24 | Every decision updates the account's pending authorization summary counters and balances | COPAUA0C BR-26 … BR-30 |
| Authorization accounting | FR-25 | Every decision is stored as an authorization under its account, keyed newest-first | COPAUA0C BR-31 … BR-35 |
| Authorization enquiry | FR-26 | An operator may list an account's pending authorizations with its authorization metrics | COPAUS0C BR-01 … BR-08 |
| Authorization enquiry | FR-27 | The list is paged five at a time, newest first, with the source paging messages | COPAUS0C BR-09 … BR-15, BR-20 … BR-25 |
| Authorization enquiry | FR-28 | An operator may select one listed authorization and see its full detail | COPAUS0C BR-16 … BR-19; COPAUS1C BR-01 … BR-08, BR-16, BR-17 |
| Authorization enquiry | FR-29 | Displayed dates, times, expiry, approval indicator and decline reason follow the source formats | COPAUS0C BR-13, BR-14; COPAUS1C BR-04 … BR-08 |
| Fraud marking | FR-30 | An operator may mark an authorization as fraud and clear the marking again | COPAUS1C BR-09, BR-13, BR-18 |
| Fraud marking | FR-31 | The fraud report is recorded in the fraud store and the authorization is updated only if that succeeded, all or nothing | COPAUS1C BR-10 … BR-12, BR-14, BR-15; COPAUS2C BR-06 … BR-09 |
| Fraud marking | FR-32 | A fraud report is identified by card number and authorization timestamp and carries the whole authorization | COPAUS2C BR-01 … BR-05 |
| Authorization housekeeping | FR-33 | Authorizations older than the retention period are purged, account by account, restartably | CBPAUP0C BR-01 … BR-12, BR-15, BR-18 … BR-24 |
| Authorization housekeeping | FR-34 | Purging releases the authorization's contribution to the account's approved or declined totals | CBPAUP0C BR-13, BR-14, BR-17 |
| Authorization housekeeping | FR-35 | An account with no approved authorizations left loses its pending authorization summary | CBPAUP0C BR-16 |
| Authorization data migration | FR-36 | The pending authorization hierarchy can be loaded and unloaded, summaries before authorizations, tolerant of records already present | PAUDBLOD BR-01 … BR-13 |

`14-rtm.md` carries the same rows down to target class, API operation, PostgreSQL object and
validation test.

## 2. Rules by classification

| Classification | Count | Examples |
|---|---|---|
| Control Flow | 47 | COPAUA0C BR-01, BR-04 … BR-06, BR-12, BR-25, BR-30; COPAUS0C BR-15, BR-17, BR-19 … BR-21; CBPAUP0C BR-15, BR-16, BR-18 |
| Formatting/Conversion | 21 | COPAUA0C BR-07, BR-08, BR-22, BR-32; COPAUS0C BR-05, BR-06, BR-12, BR-13; COPAUS1C BR-03, BR-04, BR-07, BR-08 |
| Derivation/Default | 22 | COPAUA0C BR-02, BR-10, BR-19, BR-21, BR-26, BR-33, BR-34; CBPAUP0C BR-02 … BR-05, BR-19 |
| Exception Handling | 16 | COPAUA0C BR-37; COPAUS0C BR-22, BR-23, BR-26; COPAUS1C BR-12, BR-14, BR-17; CBPAUP0C BR-08, BR-10, BR-21 |
| Data Selection | 14 | COPAUA0C BR-11, BR-13; COPAUS0C BR-04, BR-07, BR-09 … BR-11; COPAUS1C BR-02, BR-16; CBPAUP0C BR-06 |
| Calculation | 8 | COPAUA0C BR-15, BR-16, BR-20, BR-28, BR-29, BR-31; COPAUS2C BR-03; CBPAUP0C BR-11, BR-13, BR-14, BR-17 |
| Validation | 7 | COPAUA0C BR-17, BR-18; COPAUS0C BR-01, BR-02, BR-16, BR-18; COPAUS1C BR-01; CBPAUP0C BR-12 |

Total 135. Unlike the account closure, this one **does** compute: available credit, the nines
complement authorization key, the summary counters and the purge reversals are genuine arithmetic
and are the rules with the highest parity risk. They are pinned by
`AuthorizationDecisionEngineTest`, `AuthorizationKeyTest` and `ExpiredAuthorizationProcessorTest`.

## 3. Cross-program rule agreement (corroboration)

Rules corroborated by more than one artifact carry **High** confidence:

| Behaviour | Corroborating artifacts |
|---|---|
| Nines-complement authorization key gives newest-first order | `COPAUA0C` BR-31 (writes it), `COPAUS0C` BR-11 (reads in that order), `COPAUS2C` BR-03 and `CBPAUP0C` BR-11 (both reverse it), `CIPAUDTY` `PA-AUTHORIZATION-KEY`, `ims/DBPAUTP0.dbd` child key `PAUT9CTS` |
| Response code `00` means approved | `COPAUA0C` BR-19, `COPAUS0C` BR-14, `COPAUS1C` BR-05, `CBPAUP0C` BR-13, `README.md` reply contract |
| Decline reason code table | `COPAUA0C` BR-21 (producer), `COPAUS1C` BR-06 (display table), module `README.md` |
| Request and reply message layouts | `COPAUA0C` BR-07, BR-22, copybooks `CCPAURQY`/`CCPAURLY`, module `README.md` request/response contracts, queues `AWS.M2.CARDDEMO.PAUTH.REQUEST`/`.REPLY` in `CRDDEMO2.csd` |
| Summary keyed by account, authorizations beneath it | `COPAUA0C` BR-30, BR-35, `COPAUS0C` BR-07, BR-11, `CBPAUP0C` BR-06, `ims/DBPAUTP0.dbd`, `PAUDBLOD` BR-06 |
| Fraud row keyed by card number + authorization timestamp | `COPAUS2C` BR-02, `ddl/AUTHFRDS.ddl`, `ddl/XAUTHFRD.ddl` unique index, `dcl/AUTHFRDS.dcl` |
| Fraud marking is a toggle | `COPAUS1C` BR-09 (toggles the segment), `COPAUS2C` BR-07 (duplicate key → update) |
| Match status `P` pending / `D` declined | `COPAUA0C` BR-33, `CIPAUDTY` 88-levels, `COPAUS0C` BR-12 (displays it) |
| CP00 is MQ-triggered | `COPAUA0C` BR-01, `CRDDEMO2.csd` transaction `CP00`, module `README.md` trigger description |
| Purge is a BMP against the update PSB | `CBPAUP0C` BR-06, `CBPAUP0J.jcl` `PARM='BMP,CBPAUP0C,PSBPAUTB'`, `ims/PSBPAUTB.psb` |

Rules with **Medium** confidence and their reason:

| Rule | Reason |
|---|---|
| `COPAUA0C` BR-08 (`NUMVAL` of the amount) | behaviour for non-numeric text is implementation-defined; no validation in the source (UCR-19) |
| `COPAUA0C` BR-21 (branches for inactive card, closed account, card/merchant fraud) | the reason codes exist but no in-scope program sets those conditions — unreachable (UCR-18) |
| `COPAUA0C` BR-27 (limits refreshed from the account) | on a failed account read the values moved are uninitialised |
| `COPAUA0C` BR-28 (cash balance reset to zero on approval) | no source evidence explains it; carried forward as-is (UCR-22) |
| `COPAUA0C` BR-29 (declined total accrual) | the field added is loaded by a later paragraph, so the source accrues the previous message's amount (UCR-30) |
| `COPAUS1C` BR-06 (reasons `4400`, `5300`) | present in the display table, never produced (UCR-18) |
| `COPAUS2C` BR-05 (two fraud report dates) | the screen date and the stored `CURRENT DATE` can differ |
| `CBPAUP0C` BR-02 (retention period) | the shipped parameter card supplies `00`, which purges everything up to today |
| `CBPAUP0C` BR-11 (age arithmetic) | Julian day subtraction is wrong across a year boundary (UCR-25) |
| `CBPAUP0C` BR-16 (summary delete condition) | duplicated approved-count test, declined count evidently intended (UCR-26) |
| `CBPAUP0C` BR-17 (counter reversals discarded) | computed and never written back (UCR-27) |
| `PAUDBLOD` BR-05 (non-numeric account id skipped) | missing `ELSE`, silent data loss (UCR-28) |

## 4. Mandatory risk flags — consolidated

| Flag | Occurrences |
|---|---|
| Hard-coded literal amounts / limits / codes | `COPAUA0C` BR-02 (wait `5000`), BR-05 (limit `500`), BR-19 (`00`/`05`), BR-21 (reason codes), BR-24 (expiry `50`), BR-31 (`99999`, `999999999`); `COPAUS0C` BR-10 (five rows); `COPAUS1C` BR-06 (reason table); `COPAUS2C` BR-03 (`999999999`); `CBPAUP0C` BR-02 … BR-04 (`5`, `5`, `10`), BR-08 … BR-21 (`16`), BR-11 (`99999`), BR-19 (`RMAD`) → UCR-07 |
| Unchecked return code | `COPAUA0C` BR-01 (`RETRIEVE … NOHANDLE`, queue name left blank), BR-36 (PSB schedule failure logged, processing continues) → UCR-18 |
| Declared but never populated / never reached | `COPAUA0C` BR-14 (empty profile paragraph), BR-21 (four unreachable reason branches); `COPAUS1C` BR-06 (`4400`, `5300` never produced) → UCR-18, UCR-20 |
| Duplicate / contradictory conditions | `CBPAUP0C` BR-16 (`PA-APPROVED-AUTH-CNT <= 0 AND PA-APPROVED-AUTH-CNT <= 0`) → UCR-26 |
| Missing ELSE / WHEN OTHER | `PAUDBLOD` BR-05 (non-numeric account id, no `ELSE`), BR-09 (read failure only displayed); `COPAUS1C` BR-01 (error flag set with no message) → UCR-24, UCR-28 |
| Computed-and-discarded business values | `CBPAUP0C` BR-17 (summary counter reversals never stored) → UCR-27 |
| Reply sent before the data is committed | `COPAUA0C` BR-24, BR-25 (non-persistent reply, `MQGMO-NO-SYNCPOINT` get) → UCR-21, register entries CMD-10 … CMD-12 |
| Technical codes exposed to the user | `COPAUS0C` BR-26, `COPAUS1C` BR-14, `COPAUS2C` BR-08 (database status/SQLCODE in the screen message) — preserved for parity, noted in `17-limitations-and-review-required.md` |
| Two-digit years | `COPAUS0C` BR-13, `COPAUS1C` BR-04, `COPAUS2C` BR-01 → UCR-23 |

## 5. Exception scenarios (database and queue status as business logic)

IMS status is treated as business logic throughout: blank success, `GE`/`GB` normal
not-found/end-of-database, anything else fatal. DB2 `SQLCODE 0` success, `-803` duplicate key
(the fraud toggle path), anything else fatal. MQ `MQCC-OK` success,
`MQRC-NO-MSG-AVAILABLE` normal end of work, anything else logged and skipped.

| Scenario | Source behaviour | Rules | Target behaviour |
|---|---|---|---|
| No message on the request queue | run ends normally, trigger restarts it later | COPAUA0C BR-04 | consumer waits on the topic |
| Request queue open/close failure | logged as `M001`/`M002`, run ends | COPAUA0C BR-03, BR-38 | container fails to start / shuts down, surfaced by the health probe |
| Reply put failure | logged as `M004`, request already consumed | COPAUA0C BR-24 | outbox row left unpublished with attempts incremented, republished by the poller (CMD-12) |
| Summary not found | normal: decide from account data, insert a new summary | COPAUA0C BR-13, BR-26 | `Optional.empty()` → `AuthorizationSummaryEntity` created with zero counters |
| Any other IMS status in CP00 | logged as `I002`/`I003`/`I004`, processing continues | COPAUA0C BR-37 | transaction rolls back, bounded retry, then dead-letter topic (CMD-14) |
| Authorization list exhausted | paging message, list ends | COPAUS0C BR-23, BR-25 | empty page, same message text |
| Any other IMS status online | system-error message carrying the status code | COPAUS0C BR-26, COPAUS1C BR-14 | `ApiError` HTTP 500 carrying the same text |
| Fraud store duplicate key | update the existing row | COPAUS2C BR-07 | upsert on the `auth_fraud` primary key |
| Fraud store failure | caller rolls back, message displayed | COPAUS1C BR-11, BR-12; COPAUS2C BR-08 | single local transaction rolled back (CMD-13) |
| Purge read/delete/checkpoint failure | display and `RETURN-CODE 16` | CBPAUP0C BR-08, BR-10, BR-15, BR-21 | step `FAILED`, chunk rolled back, restart from the last committed chunk |
| Load record already present | reported and skipped | PAUDBLOD BR-07 | conflict-tolerant insert, load stays idempotent |

## 6. Deliberate deltas against the source

| Delta | Precedence rule | Where recorded |
|---|---|---|
| PF7/PF8 paging, PF5 fraud toggle, PF3 back and COMMAREA screen state retired | playbook §2.4(2) — platform interaction mechanism, outside the parity bar | `06-screen-mapping.md`, `17-limitations-and-review-required.md` |
| MQ request/reply on two queues becomes Kafka request/reply topics keyed by card number | playbook §3 item 13, §8.8 | `23-messaging-modernization-mapping.md` |
| Reply-before-write (COPAUA0C BR-25) becomes write-then-outbox-publish in one local transaction | §2.4(1) preserved: the requester still receives the same reply for the same request | `12-consistency-model-delta-register.md` CMD-11, CMD-12 |
| `CBPAUP0C` BR-17 counter reversals are persisted (`carddemo.batch.authpurge.persist-summary-adjustments`, default true; set false to reproduce AS-IS) | §2.4(3) AS-IS defect remediated — corroborated by the module `README.md` batch function "Adjustment of available credit when unmatched authorizations are deleted" and by the program computing four reversals it then discards | `11-unsupported-construct-register.md` UCR-27, `10-testing-and-parity.md`, `17-limitations-and-review-required.md` |
| `CBPAUP0C` BR-16 duplicated condition | §2.4(3) not corroborated → carried forward unchanged, REVIEW REQUIRED | UCR-26 |
| `COPAUA0C` BR-29 declined total accrues the *previous* message's amount; the target adds the current request amount | §2.4(3) AS-IS defect **remediated**: corroborated by `CBPAUP0C` BR-14, which subtracts exactly that field when it purges a declined authorization, so the total must be the sum of the declined authorizations' own amounts | UCR-30, `10-testing-and-parity.md` |

## 7. Where each rule is implemented and proved

`14-rtm.md` carries every rule row by row: ticket, rule, source location, target class and method,
React component, API operation, PostgreSQL object, batch job/step, validation test, expected and
actual result, status.
