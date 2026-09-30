# CardDemo UI runtime test plan

Environment: local Compose PostgreSQL/Keycloak/Kafka; Java 21 packaged services;
Vite development server http://127.0.0.1:5173, commit 8cf5684.
Access setup complete for ADMIN001 and USER0001 with password/TOTP.
The fixed dev callback reaches Admin Menu without reload.

## Source-grounded navigation and expectations

- `frontend/src/App.tsx:73-116,134-260`: session routing and menu/screen transitions.
- `frontend/src/screens/AdminMenuScreen.tsx:33-89`: numbered admin actions.
- `frontend/src/screens/MenuScreen.tsx:32-61`: regular-user numbered menu.
- `frontend/src/screens/UserUpdateScreen.tsx:30-69,85-121`: fetch/update fields.
- `frontend/src/screens/AccountUpdateScreen.tsx:28-68,90-109,232-248`: fetch,
  Process validation, then Save; Exit returns to menu.
- `frontend/src/screens/CardListScreen.tsx:96-116,127-202`: account filter and S/U row actions.
- `frontend/src/screens/CardUpdateScreen.tsx:117-205`: Fetch, Validate changes, Save changes.
- `frontend/src/screens/TransactionListScreen.tsx:82-168`: S selection and View.
- `frontend/src/screens/TransactionViewScreen.tsx:28-43,54-115`: explicit transaction lookup.
- `frontend/src/screens/TransactionAddScreen.tsx:72-129`: input fields and Add.
- `frontend/src/screens/BillPaymentScreen.tsx:24-55,59-122`: balance lookup, Y/N, Send.
- `frontend/src/screens/ReportRequestScreen.tsx:36-48,53-145`: Monthly/Yearly/Custom request.
- `account-service/src/main/resources/db/migration/V2__load_sample_data.sql:18,150`:
  account 11 balance 212, card 7427684863423209.
- `account-service/src/main/resources/db/migration/V4__load_card_sample_data.sql:45`:
  Hayden Pfannerstill card name.
- `transaction-service/src/main/resources/db/migration/V2__load_sample_data.sql:11`:
  transaction 0000000000683580, Abshire-Lowe purchase 504.77.
- `e2e/tests/signon.spec.ts` and `journeys.spec.ts`: supplied 10 browser tests.

## 1. Administrator journey

Capture sign-on UI and the password/TOTP transition as evidence (credentials already verified).
1. At Admin Menu assert `Prog: COADM01C`; click 01 User List and assert ADMIN001.
2. Back, click 02 User Add; enter UI092401 / EVELYN / REED / local disposable password /
   U, click Enter. Expect message containing UI092401 and added.
3. Back, click 03 User Update; fetch UI092401, change Last Name to VERIFIED and Enter.
   Expect updated message. Fetch again: Last Name must be VERIFIED, not REED.
4. Back, click 04 User Delete; fetch UI092401, expect VERIFIED, click F5 Delete.
   Expect deleted message; fetch again must report not found.

## 2. Regular-user financial journey

Capture sign-on transition as evidence; Main Menu must show USER0001 - Regular user and COMEN01C.
1. Click 01 Account View; Search 00000000011. Expect balance 212.00 and account 11.
2. Exit, click 02 Account Update, Fetch 00000000011. Change Credit Limit from 4998.00
   to 5001.00 using the form's fixed decimal format. Process then Save. Expect saved
   confirmation. Exit and re-search Account View; credit limit must be 5001.00.
3. Exit, click 03 Credit Card List, filter account 00000000011, Search.
   Expect card 7427684863423209. S + Apply action must open View Credit Card and
   show Hayden Pfannerstill. Return, open 05 Credit Card Update, Fetch same account/card.
   Change Name on Card to HAYDEN UI VERIFIED; Validate changes, Save changes.
   Reopen 04 Credit Card View and Fetch; expect HAYDEN UI VERIFIED.
4. Open 06 Transaction List. Expect transaction 0000000000683580 and 504.77.
   Enter S in its Selection field and click row View. Expect Abshire-Lowe and 504.77.
5. Open 08 Transaction Add. Enter account 00000000011, its card, type 01, category 0001,
   source UITEST, description UI EVIDENCE 0924, amount +00000012.34, dates 2026-09-24,
   merchant 800000000 / UI MERCHANT / TEST CITY / 12345, Confirm Y; click Add.
   Expect `Transaction added successfully` and a generated ID. Reopen 07 Transaction View,
   lookup that ID: description UI EVIDENCE 0924 and amount 12.34 must persist.
6. Open 10 Bill Payment, account 00000000011, Get balance: expect 212.00.
   Blank confirmation + Send must not pay; Y + Send must show Payment successful and ID.
   Reopen Account View: current balance must be 0.00, not 212.00.
7. Open 09 Transaction Reports, select Custom, enter 06/01/2022 through 06/30/2022,
   Confirm Y, Submit. Expect report name, request ID, From 2022-06-01, To 2022-06-30.
   This asserts queued request acceptance, not batch report generation.

## Supplied automated suite

Run `npm run test` in e2e with the same in-memory local credentials. Expected 10 passed,
0 failed. Save console output and complete Playwright HTML report. Keep automated runs
separate from manual UI role sessions to avoid TOTP replay collisions.

Capture each key screen and meaningful persistence assertion. No screenshot alone of a
filled form counts as a passed write. Fail or mark incomplete any inaccessible flow.

Runtime adjustment: account 11 is seeded with FICO 209, while
`common/src/main/java/com/carddemo/domain/validation/FieldEditor.java:237-238`
requires 300–850. Capture that validation refusal, change FICO to 700 as well as
Credit Limit to 5001.00, then validate/save and re-fetch both values.

Report retest at 637af37: reuse regular user session and Custom 06/01/2022–06/30/2022
request with Confirm Y. Expect successful report name, request ID and matching dates.
Read the report_request row by the returned request ID: requested_by must be USER0001,
length 8, not the realm UUID. Source: transaction-service ReportRequestController.java
now resolves SignedOnUser from preferred_username; V1 schema keeps VARCHAR(8).
Card/transaction/bill-payment persistence already passed on 486406b; do not repeat
state-changing payments against the now-zero account balance.
