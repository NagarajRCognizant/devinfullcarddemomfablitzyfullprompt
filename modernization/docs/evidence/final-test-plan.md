# Final continuous CardDemo test execution

Baseline: 323a764; prior verified application fixes unchanged. Existing Compose
infrastructure and host Java/Vite services reused. Native PostgreSQL contains
carddemo_test, carddemo_identity_test, carddemo_authorization_test,
carddemo_transaction_test on 5432, isolated from runtime Compose ports 5433–5436.
Credentials and both roles already verified. Before recording, restored disposable
account 11 balance 212, credit limit 4998, original customer FICO/phones/ZIP and
card name from seed. Existing unrelated transaction/report history is retained.

Sources: ui-test-plan.md contains the screen source paths and line ranges;
frontend/src/App.tsx:73-116,134-260 wires menus to those screens.
e2e/tests/realm.ts provides password/TOTP semantics.
All */src/test/resources/application-test.yml use separate test JDBC URLs.
.github/workflows/modernization-ci.yml:51-73 adds the fourth CI test database.
docs/11-unsupported-construct-register.md:916-931 documents UCR-41 corrections.

## Single continuous execution

Use one recording, with a read-only terminal displaying live logs from commands
executed via shell tools, then switch to the browser without stopping recording.
Each suite must exit 0 before continuing. No failure is to be omitted or hidden.
The temporary driver waits for network completion after clicks and verifies fetched
input values before editing. For User Update, wait for Last Name = REED after Fetch,
then type VERIFIED. Reopen the screen before the persistence check.
Before the recorded run, execute the whole UI driver once without recording.
Use the absolute Node executable path. Delete User's fetched values are read-only
definition-list entries (`UserDeleteScreen.tsx:74-80`), so assert visible VERIFIED
text, not an input value. Reset the account/card fixtures and remove FINAL001
before both passes. The same driver and selectors must be used for the recording.

1. Execute `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn39 -o verify` in
   modernization; expect BUILD SUCCESS, 634 tests, no failures/errors.
2. Execute `npm test -- --run` in frontend; expect 21 files / 103 tests passed.
3. Execute e2e `npm run test` with existing process-only credentials;
   expect 10 passed. Preserve full logs and HTML.
4. Demonstrate both already-established sign-on transitions as user-requested evidence.
   Admin menu must display COADM01C. Click User List: ADMIN001.
   User Add: FINAL001 / EVELYN / REED / temporary password / U; Enter → added.
   User Update: fetch FINAL001, surname VERIFIED, Enter → updated.
   Re-fetch must show VERIFIED. User Delete: fetch, F5 Delete → deleted.
   Re-fetch must show User ID NOT found. Sign off.
5. Regular menu must show USER0001 / COMEN01C. Account View: search 00000000011:
   balance 212.00, credit 4998.00. Account Update: fetch same account, credit 5001.00.
   Before Process, correct documented UCR-41 fields visibly: FICO 700, area codes
   212/212, ZIP 98101. Process then Save → saved. Separate Account View must show 5001.00.
6. Credit Card List: search same account; card 7427684863423209. S + Apply action
   opens Card View with Hayden Pfannerstill. Card Update: fetch, name HAYDEN FINAL
   VERIFIED, Validate changes then Save changes. Separate Card View fetch must show
   HAYDEN FINAL VERIFIED.
7. Transaction List: seed 0000000000683580, 504.77. S + View opens Purchase at
   Abshire-Lowe. Transaction Add: account/card above, type 01, category 0001,
   source UITEST, description FINAL UI VERIFIED, amount +00000012.34, dates
   2026-09-24, merchant 800000000 / UI MERCHANT / TEST CITY / 12345, Y.
   Expect added and generated ID; separate Transaction View must show description
   FINAL UI VERIFIED and amount 12.34.
8. Bill Payment: Get balance for account 11 → 212.00. Blank + Send must refuse
   without paying (expected negative assertion, not a failed test). Y + Send →
   Payment successful and transaction ID. Separate Account View must show 0.00.
9. Transaction Reports: Custom, 06/01/2022–06/30/2022, Y, Submit → accepted,
   matching range and generated request ID. Read-only database query for that ID
   must show USER0001, length 8. Does not assert downstream report printing.

Capture visible key states with final- filenames. Stop and report any unexpected
failure; do not continue to portray the run as wholly passing.
