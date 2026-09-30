# Screen Migration Matrix — BMS to React

Four BMS maps are in scope. The two account maps are covered in §1-§7; the two authorization maps
(`COPAU00`, `COPAU01`) are covered in §8.

The account maps are `app/bms/COACTVW.bms` (mapset `COACTVW`, map `CACTVWA`, 37 fields) and
`app/bms/COACTUP.bms` (mapset `COACTUP`, map `CACTUPA`, 53 fields). Their symbolic structures are
`app/cpy-bms/COACTVW.CPY` and `COACTUP.CPY`.

Terminal mechanics that are **not** reproduced (platform delivery, playbook §6.2): 3270 attribute
bytes (`DFHBMFSE`, `DFHRED`, `DFHNEUTR`, `DFHBMDAR`), cursor positioning by `-1` in the length field,
AID codes and `CSSTRPFY`, `SEND`/`RECEIVE MAP`, mapset/map names in the COMMAREA, screen size 24x80.
Their business intent is preserved as listed below.

## 1. Map-level mapping

| BMS map | React component | REST operations | Notes |
|---|---|---|---|
| `CACTVWA` (COACTVW) | `AccountViewScreen.tsx` | `GET /api/accounts/view`, `GET /api/accounts/search?accountId=…` | all data fields read-only; only the account filter is keyable |
| `CACTUPA` (COACTUP) | `AccountUpdateScreen.tsx` | `GET /api/accounts/update?accountId=…`, `POST …/update/validate`, `POST …/update/confirm` | editable fields per `3320-UNPROTECT-FEW-ATTRS` |
| `COMEN01` (menu) | `MenuScreen.tsx` | `GET /api/menu` | only the two in-scope options are offered |
| — | `ScreenFrame.tsx` | — | reproduces the common header: titles from `COTTL01Y`, transaction name, program name, date and time |
| — | `Field.tsx`, `Messages.tsx` | — | reproduce label/value layout, error highlighting and the single-message + info-message pair |

## 2. Common header fields (both maps)

| BMS field | Source of value | Target |
|---|---|---|
| `TITLE01`, `TITLE02` | `COTTL01Y` literals | `ScreenFrame` constants |
| `TRNNAME` | `LIT-THISTRANID` (`CAVW` / `CAUP`) | `ScreenFrame` prop |
| `PGMNAME` | `LIT-THISPGM` (`COACTVWC` / `COACTUPC`) | `ScreenFrame` prop — kept so the screens stay recognisable against the source |
| `CURDATE`, `CURTIME` | `1100-SCREEN-INIT` / `3100-SCREEN-INIT` | browser clock, `MM/DD/YY` and `HH:MM:SS` |
| `ERRMSG` | `WS-RETURN-MSG` | `Messages` error line, red |
| `INFOMSG` | `WS-INFO-MSG` | `Messages` info line, dimmed when empty (`WS-NO-INFO-MESSAGE` → `DFHBMDAR`) |
| `FKEYS`, `FKEY05`, `FKEY12` (COACTUP) | literal key legend | action buttons: "Save changes" (F5), "Reload" (F12), "Exit" (F3) |

## 3. Account View — field mapping

| BMS field | Label | Source field | Editable | Target field |
|---|---|---|---|---|
| `ACCTSID` | Account Number | `CC-ACCT-ID` / `CDEMO-ACCT-ID` | yes (the only input) | `details.accountId`; asterisk redisplay per BR-20 |
| `ACSTTUS` | Active Y/N | `ACCT-ACTIVE-STATUS` | no | `details.activeStatus` |
| `ADTOPEN` | Opened | `ACCT-OPEN-DATE` | no | `details.openDate` |
| `ACRDLIM` | Credit Limit | `ACCT-CREDIT-LIMIT` | no | `details.creditLimit` (`MoneyValue`) |
| `AEXPDT` | Expiry | `ACCT-EXPIRAION-DATE` | no | `details.expirationDate` |
| `ACSHLIM` | Cash credit Limit | `ACCT-CASH-CREDIT-LIMIT` | no | `details.cashCreditLimit` |
| `AREISDT` | Reissue | `ACCT-REISSUE-DATE` | no | `details.reissueDate` |
| `ACURBAL` | Current Balance | `ACCT-CURR-BAL` | no | `details.currentBalance` |
| `ACRCYCR` | Current Cycle Credit | `ACCT-CURR-CYC-CREDIT` | no | `details.currentCycleCredit` |
| `ACRCYDB` | Current Cycle Debit | `ACCT-CURR-CYC-DEBIT` | no | `details.currentCycleDebit` |
| `AADDGRP` | Account Group | `ACCT-GROUP-ID` | no | `details.groupId` |
| `ACSTNUM` | Customer id | `CUST-ID` | no | `details.customerId` |
| `ACSTSSN` | SSN | `CUST-SSN` formatted 3-2-4 | no | `details.ssnFormatted` (BR-18) |
| `ACSTDOB` | Date of birth | `CUST-DOB-YYYY-MM-DD` | no | `details.dateOfBirth` |
| `ACSTFCO` | FICO Score | `CUST-FICO-CREDIT-SCORE` | no | `details.ficoScore` |
| `ACSFNAM`, `ACSMNAM`, `ACSLNAM` | First/Middle/Last Name | `CUST-*-NAME` | no | `details.firstName` / `middleName` / `lastName` |
| `ACSADL1`, `ACSADL2` | Address lines | `CUST-ADDR-LINE-1/2` | no | `details.addressLine1` / `addressLine2` |
| `ACSCITY` | City | `CUST-ADDR-LINE-3` | no | `details.city` |
| `ACSSTTE` | State | `CUST-ADDR-STATE-CD` | no | `details.state` |
| `ACSZIPC` | Zip | `CUST-ADDR-ZIP` | no | `details.zip` |
| `ACSCTRY` | Country | `CUST-ADDR-COUNTRY-CD` | no | `details.country` |
| `ACSPHN1`, `ACSPHN2` | Phone 1 / 2 | `CUST-PHONE-NUM-1/2` | no | `details.phone1` / `phone2` |
| `ACSGOVT` | Government Issued Id | `CUST-GOVT-ISSUED-ID` | no | `details.governmentIssuedId` |
| `ACSEFTC` | EFT Account Id | `CUST-EFT-ACCOUNT-ID` | no | `details.eftAccountId` |
| `ACSPFLG` | Primary Card Holder | `CUST-PRI-CARD-HOLDER-IND` | no | `details.primaryCardHolder` |

## 4. Account Update — field mapping

Read-only on the map (`3310`/`3320`): `ACCTSID` after the fetch, `ACSTNUM`. Everything else in the
table below is unprotected and therefore editable, which is what `AccountUpdateForm` accepts.

| BMS field(s) | Source field | Target form field | Edit rule |
|---|---|---|---|
| `ACCTSID` | `CC-ACCT-ID` | `accountId` (query parameter on the fetch, then the form key) | BR-11, BR-12; keyable only while `ACUP-DETAILS-NOT-FETCHED`, protected once the details are shown |
| `ACSTTUS` | `ACCT-ACTIVE-STATUS` | `activeStatus` | BR-20 Y/N |
| `OPNYEAR`/`OPNMON`/`OPNDAY` | `ACCT-OPEN-DATE` | `openYear` / `openMonth` / `openDay` | BR-35..39 |
| `EXPYEAR`/`EXPMON`/`EXPDAY` | `ACCT-EXPIRAION-DATE` | `expiryYear` / `expiryMonth` / `expiryDay` | BR-35..39 |
| `RISYEAR`/`RISMON`/`RISDAY` | `ACCT-REISSUE-DATE` | `reissueYear` / `reissueMonth` / `reissueDay` | BR-35..39 |
| `ACRDLIM` | `ACCT-CREDIT-LIMIT` | `creditLimit` | BR-25 signed 2dp |
| `ACSHLIM` | `ACCT-CASH-CREDIT-LIMIT` | `cashCreditLimit` | BR-25 |
| `ACURBAL` | `ACCT-CURR-BAL` | `currentBalance` | BR-25 |
| `ACRCYCR` | `ACCT-CURR-CYC-CREDIT` | `currentCycleCredit` | BR-25 |
| `ACRCYDB` | `ACCT-CURR-CYC-DEBIT` | `currentCycleDebit` | BR-25 |
| `AADDGRP` | `ACCT-GROUP-ID` | `groupId` | optional text, case-insensitive compare |
| `ACSTNUM` | `CUST-ID` | displayed only | BR-54 |
| `ACTSSN1`/`ACTSSN2`/`ACTSSN3` | `CUST-SSN` | `ssnPart1` / `ssnPart2` / `ssnPart3` | BR-30 |
| `DOBYEAR`/`DOBMON`/`DOBDAY` | `CUST-DOB-YYYY-MM-DD` | `dobYear` / `dobMonth` / `dobDay` | BR-35..40 |
| `ACSTFCO` | `CUST-FICO-CREDIT-SCORE` | `ficoScore` | BR-24, BR-32 (300-850) |
| `ACSFNAM`/`ACSMNAM`/`ACSLNAM` | `CUST-*-NAME` | `firstName` / `middleName` / `lastName` | BR-21, BR-22 |
| `ACSADL1` | `CUST-ADDR-LINE-1` | `addressLine1` | BR-19 mandatory |
| `ACSADL2` | `CUST-ADDR-LINE-2` | `addressLine2` | accepted unvalidated (BR-34) |
| `ACSCITY` | `CUST-ADDR-LINE-3` | `city` | BR-21 required alphabetic |
| `ACSSTTE` | `CUST-ADDR-STATE-CD` | `state` | BR-21, BR-31 |
| `ACSZIPC` | `CUST-ADDR-ZIP` | `zip` | BR-24, BR-33 |
| `ACSCTRY` | `CUST-ADDR-COUNTRY-CD` | `country` | BR-21 |
| `ACSPH1A`/`ACSPH1B`/`ACSPH1C` | `CUST-PHONE-NUM-1` | `phone1Area` / `phone1Prefix` / `phone1Line` | BR-26..29 |
| `ACSPH2A`/`ACSPH2B`/`ACSPH2C` | `CUST-PHONE-NUM-2` | `phone2Area` / `phone2Prefix` / `phone2Line` | BR-26..29 |
| `ACSGOVT` | `CUST-GOVT-ISSUED-ID` | `governmentIssuedId` | optional text |
| `ACSEFTC` | `CUST-EFT-ACCOUNT-ID` | `eftAccountId` | BR-24 |
| `ACSPFLG` | `CUST-PRI-CARD-HOLDER-IND` | `primaryCardHolder` | BR-20 Y/N |

## 5. Function keys → business actions

| Key | Source meaning | Target |
|---|---|---|
| Enter | display / validate keyed values | `GET …/{id}` (view) or `POST …/update/validate` |
| F3 | exit to the caller or the menu | UI "Exit" → back to `MenuScreen` |
| F5 (CAUP only, only when changes are validated) | commit the changes | `POST …/update/confirm`, enabled only in `CHANGES_VALIDATED` |
| F12 (CAUP only, only after a fetch) | discard keyed changes and reload | UI "Reload" → `GET …/{id}/update` |
| any other key | treated as Enter | the UI offers no other action; the silent re-interpretation has no user-visible effect |

## 6. Message and highlighting semantics

The source shows exactly one error message (`ERRMSG`) even when several fields fail, and highlights
every failing field independently (`3300-SETUP-SCREEN-ATTRS`). The API returns the same single
message plus the map of failing fields to their highlight flags (`fieldFlags`); `Field.tsx` highlights each of them and
`Messages.tsx` renders the one message. The asterisk redisplay of an empty account filter (BR-20) is
reproduced in `AccountViewScreen`.

## 7. Accessibility

The React screens use labelled inputs (`<label htmlFor>`), `aria-invalid` on failing fields,
`role="alert"` on the message line and keyboard-reachable action buttons. Automated accessibility
auditing (axe/Lighthouse) was **not** run — see `17-limitations-and-review-required.md` (L-04).

## 8. Authorization maps (`COPAU00`, `COPAU01`)

Two further BMS maps are in scope: `app/app-authorization-ims-db2-mq/bms/COPAU00.bms` (mapset
`COPAU00`, map `COPAU0A`, authorization summary and list) and `COPAU01.bms` (mapset `COPAU01`, map
`COPAU1A`, authorization detail). Their symbolic structures are
`app/app-authorization-ims-db2-mq/cpy-bms/COPAU00.cpy` and `COPAU01.cpy`.

| BMS map | Transaction | React component | REST operations | Notes |
|---|---|---|---|---|
| `COPAU0A` (COPAU00) | `CPVS` | `AuthorizationSummaryScreen.tsx` | `GET /api/authorizations?accountId=…&afterAuthKey=…` | only the account filter and the five selection fields are keyable in the source; the target keeps the filter as an input and turns each selection field into a row action |
| `COPAU1A` (COPAU01) | `CPVD` | `AuthorizationDetailScreen.tsx` | `GET /api/authorizations/{accountId}/{authKey}`, `POST /api/authorizations/{accountId}/{authKey}/fraud` | every field is display-only in both source and target; the two actions are "Next authorization" and "Mark/Remove fraud" |

### 8.1 `COPAU0A` — field mapping

| BMS field | Source of value | Target field | Notes |
|---|---|---|---|
| `TITLE01`, `TITLE02`, `TRNNAME`, `PGMNAME`, `CURDATE`, `CURTIME` | `COTTL01Y`, `LIT-THISTRANID` (`CPVS`), `LIT-THISPGM` (`COPAUS0C`) | `ScreenFrame` | the same header contract as the account screens |
| `ACCTSID` | keyed account filter | `accountId` input | numeric edit per `COPAUS0C` BR-01, BR-02 |
| `CUSTSID` | `CUST-ID` of the customer read through the xref | `summary.customerId` | from the account service, not the account database |
| `CUSTNAME` | `GATHER-ACCOUNT-DETAILS` name composition | `summary.customerName` | BR-05 |
| `CUSTADL1`, `CUSTADL2` | address composition | `summary.addressLine1`, `addressLine2` | BR-06 |
| `CUSTPHN1` | `CUST-PHONE-NUM-1` | `summary.phoneNumber` | — |
| `ACRDLIM`, `ACSHLIM` | `ACCT-CREDIT-LIMIT`, `ACCT-CASH-CREDIT-LIMIT` | `summary.creditLimit`, `cashCreditLimit` | — |
| `APPRAUTH`, `DECLAUTH` | `PA-APPROVED-AUTH-CNT`, `PA-DECLINED-AUTH-CNT` | `summary.approvedCount`, `declinedCount` | zero when no summary exists (BR-08) |
| `CRDBAL`, `CSHBAL` | `PA-CREDIT-BALANCE`, `PA-CASH-BALANCE` | `summary.creditBalance`, `cashBalance` | — |
| `APPRAMT`, `DECLAMT` | `PA-APPROVED-AUTH-AMT`, `PA-DECLINED-AUTH-AMT` | `summary.approvedAmount`, `declinedAmount` | — |
| `SEL0001`…`SEL0005` | keyed selection | row "Select" action | `S`/`s` only (BR-18); offered only for a row that exists (BR-16) |
| `TRNID000n` | `PA-TRANSACTION-ID` | `rows[n].transactionId` | — |
| `ADATE000n`, `ATIME000n` | edited `PA-AUTH-ORIG-DATE`/`TIME` | `rows[n].authDate`, `authTime` | `MM/DD/YY`, `HH:MM:SS` (BR-13) |
| `ATYPE000n` | `PA-AUTH-TYPE` | `rows[n].authType` | — |
| `APPRV000n` | derived from the response code | `rows[n].approvalIndicator` | `A` only for `00` (BR-14) |
| `MSTAT000n` | `PA-MATCH-STATUS` | `rows[n].matchStatus` | — |
| `AAMT000n` | `PA-APPROVED-AMT` | `rows[n].approvedAmount` | — |
| `ERRMSG` | `WS-RETURN-MSG` | `Messages` error line | carries the paging and selection messages verbatim |

### 8.2 `COPAU1A` — field mapping

| BMS field | Source of value | Target field | Notes |
|---|---|---|---|
| header fields | `LIT-THISTRANID` (`CPVD`), `LIT-THISPGM` (`COPAUS1C`) | `ScreenFrame` | — |
| `ACCTSID`, `CARDNUM` | `PA-ACCT-ID`, `PA-CARD-NUM` | `detail.accountId`, `cardNumber` | — |
| `AUTHDATE`, `AUTHTIME` | edited `PA-AUTH-ORIG-DATE`/`TIME` | `detail.authDate`, `authTime` | `MM/DD/YY`, `HH:MM:SS`; empty when the stored value is too short to edit (BR-04) |
| `AUTHTYPE`, `MSGTYPE`, `MSGSRC` | `PA-AUTH-TYPE`, `PA-MESSAGE-TYPE`, `PA-MESSAGE-SOURCE` | same names on `detail` | — |
| `CRDEXPD` | edited `PA-CARD-EXPIRY-DATE` | `detail.cardExpiry` | `MM/YY` (BR-07) |
| `AUTHSTAT` | derived from `PA-AUTH-RESP-CODE` | `detail.approvalStatus` | BR-05 |
| `AUTHRESN` | `SEARCH ALL WS-DECLINE-REASON-TAB` | `detail.declineReason` | hyphenated source text, `9999-ERROR` when the table has no entry (BR-06) |
| `TRANAMT`, `APPRAMT` | `PA-TRANSACTION-AMT`, `PA-APPROVED-AMT` | `detail.transactionAmount`, `approvedAmount` | — |
| `PROCCODE`, `MCC`, `ACQCTRY`, `POSENTRY` | the matching `CIPAUDTY` fields | same names on `detail` | — |
| `MERCHID`, `MERCHNAM`, `MERCHCTY`, `MERCHST`, `MERCHZIP` | merchant fields | `detail.merchant*` | — |
| `TRANID`, `MSTATUS` | `PA-TRANSACTION-ID`, `PA-MATCH-STATUS` | `detail.transactionId`, `matchStatus` | — |
| `AUTHFRD` | `PA-AUTH-FRAUD` + `PA-FRAUD-RPT-DATE` | `detail.fraudStatus` | `F-<date>`, `R-<date>` or `-` (BR-08) |
| `ERRMSG` | `WS-RETURN-MSG` | `Messages` line | carries `Authorization marked as fraud`, `Fraud marking removed`, the end-of-chain message and the read failures |

### 8.3 Function keys → business actions

| Key | Source meaning | Target |
|---|---|---|
| Enter (CPVS) | list the account's authorizations | `GET /api/authorizations?accountId=…` |
| Enter with `S` in a row (CPVS) | open that authorization | row "Select" action → the detail route with the row's authorization key |
| PF7 (CPVS) | page back | "Previous" button; the client re-requests the first key of the page it previously showed, and reports `You are already at the top of the page...` on the first page |
| PF8 (CPVS) | page forward | "Next" button → `afterAuthKey` = the last key shown; `You are already at the bottom of the page...` when nothing follows |
| PF3 (both) | exit to the caller | "Back" — to the summary screen from the detail screen, to the menu from the summary screen; offered on every send of the summary screen, with or without authorizations on display |
| PF5 (CPVD) | mark or unmark fraud | "Mark as fraud" / "Remove fraud marking" button → `POST …/fraud`; the label follows the current fraud state |
| PF8 (CPVD) | show the next authorization of the account | "Next authorization" → `GET …/{accountId}/{nextAuthKey}`; `No more authorizations for this account` at the end of the chain |
| any other key | rejected with `Invalid key pressed...` | the screen offers no other action, so the message is unreachable |

Two send-time behaviours of `COPAUS0C` are reproduced in the client: re-entry from another program
with a numeric `CDEMO-ACCT-ID` redisplays that account's first page (which is how CPVD returns to
CPVS), and a failed account or cross-reference read re-sends an erased map — `GATHER-DETAILS` runs
`INITIALIZE-AUTH-DATA` and protects the `SEL000n` fields — so no row of the account previously shown
stays on display or stays selectable.

Retired terminal mechanics, as for the account maps: attribute bytes (the `SEL000nA` protect/unprotect
cycle of `INITIALIZE-AUTH-DATA` and `POPULATE-AUTH-LIST`), cursor positioning, AID codes, the CPVS →
CPVD `XCTL` with the selected key in the COMMAREA (`CDEMO-CPVS-PAU-SELECTED`), and the IMS paging
state the source kept in working storage. Business intent is preserved as listed above; paging is
keyset-based, so the target holds no server-side cursor (`12-consistency-model-delta-register.md`
CMD-15 covers the batch equivalent).

### 8.4 Accessibility

Both authorization screens follow the same conventions as the account screens: labelled inputs,
`aria-invalid`, `role="alert"` on the message line, a `<table>` with header cells for the five-row
list and keyboard-reachable row actions. No automated accessibility audit was run (L-04).
