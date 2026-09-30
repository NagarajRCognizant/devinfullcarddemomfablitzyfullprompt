# CardDemo Java modernization — browser runtime evidence

Executed the requested browser procedure against real local services on 2026-09-24.
This is golden-path runtime evidence, not proof of complete parity for all 31 programs.

## Environment and scope

- Compose PostgreSQL, Keycloak and Kafka; packaged Java 21 services running on the host;
  React/Vite development server at `http://127.0.0.1:5173`.
- Admin and regular-user authentication used Keycloak password plus TOTP.
- Evidence spans fixes `8cf5684` (callback), `486406b` (routing/audience) and
  `637af37` (report requester). Affected jars were rebuilt/restarted before retesting;
  the live realm mapper was updated and a fresh token obtained.
- Recordings are sequential parts, not one uninterrupted run. Earlier parts retain
  the failures that stopped progress; later evidence demonstrates their targeted retests.
- Full image-based Compose application startup was not tested: the explicitly
  permitted host-JVM/Vite fallback was used.

## Issues encountered and disposition

1. **Dev sign-on callback race:** React StrictMode repeated a single-use authorization
   code exchange. After the lead's memoization fix, fresh admin and regular-user
   sessions reached the correct menus without a callback reload.
2. **Card list HTTP 404:** gateway omitted card routes. Retested after `486406b`;
   list, view, update and separate re-fetch passed.
3. **Transactions HTTP 401:** UI token lacked the transaction-service audience.
   After the realm mapper fix and fresh sign-on, transaction list/view/add passed.
4. **Report request HTTP 500:** realm UUID exceeded `requested_by VARCHAR(8)`.
   After `637af37`, the browser accepted request **1** for **Custom,
   2022-06-01 through 2022-06-30**. A read-only database query by returned request ID
   confirmed **USER0001**, length **8**.
5. **Seed-data caveat remains:** account 11's original FICO **209**, phone area
   codes **002/553**, and Washington ZIP **24984** fail edit validation. The credit
   limit update could not pass with the untouched seed. Via UI, explicitly corrected
   these to FICO **700**, area codes **212/212**, ZIP **98101**, then saved/re-fetched.
   This proves a valid-data update, not that the original seed is valid.
6. **Test setup caveats:** display/window manager required startup; host JVM Kafka
   needed advertised hostname resolution. An initial manual OTP helper argument was
   wrong and Keycloak correctly rejected it; the correct regular-user code worked.

## Runtime assertions

- **Passed:** admin password/TOTP sign-on reaches `COADM01C`; user list includes ADMIN001.
- **Passed:** temporary `UI092401` added; surname changed to VERIFIED and separately
  re-fetched; deleted, then fetch reports `User ID NOT found`.
- **Passed:** regular-user password/TOTP sign-on reaches `USER0001 - Regular user`,
  `COMEN01C`.
- **Passed:** account `00000000011` initially shows balance **212.00**.
- **Passed with seed corrections above:** credit limit **4,998.00 → 5,001.00** persists
  on a separate Account View lookup.
- **Passed:** card list/view finds `7427684863423209`, originally Hayden Pfannerstill.
- **Passed:** card name **HAYDEN UI VERIFIED** persists after Validate, Save and a
  separate Card View lookup.
- **Passed:** transaction list/view finds `0000000000683580`,
  **Purchase at Abshire-Lowe**, **504.77**.
- **Passed:** new transaction `0000000996722788`, **UI EVIDENCE 0924**, **12.34**,
  persists on a separate Transaction View lookup.
- **Passed:** blank bill-payment confirmation does not pay; explicit Y pays account
  11, creates `0000000996722789`, and a separate Account View shows **0.00** balance.
- **Passed:** custom report request displays the requested dates and request ID 1;
  database confirms the eight-character signed-on requester USER0001.
- **Untested/out of scope:** report file generation/printing, batch jobs, exhaustive
  input permutations, concurrency, non-Chromium browsers, and complete COBOL parity.
  Report acceptance proves the stored request only, not downstream batch execution.

## Automated browser coverage

The supplied Playwright suite completed **10 passed (1.6m)** in the initial run.
Its scope is sign-on, MFA enrollment/lockout, role separation, session reload/sign-off,
user provisioning/deletion mirrored to the realm, and account viewing. It does not
cover all financial writes; the manual journeys above supply that evidence.

Initial log: `ui-e2e-playwright.log`; initial HTML: `ui-playwright-report/index.html`.
The final post-fix rerun also completed **10 passed (1.6m)**, with no retries:
`ui-e2e-playwright-final.log` and `ui-playwright-report-final/index.html`.

## Visual evidence

### Account credit limit

| 🔴 Before: 4,998.00 | 🟢 After: persisted 5,001.00 |
|---|---|
| ![Account before](https://cognizant-demo.devinenterprise.com/attachments/e83ad4b4-81ed-4c05-87fd-614c018e63b2/ui-10-account-before.png) | ![Account after](https://cognizant-demo.devinenterprise.com/attachments/c13642f2-415b-484e-aa2c-e40650ebd795/ui-13-account-after.png) |

### Card name

| 🔴 Before: original card name | 🟢 After: persisted HAYDEN UI VERIFIED |
|---|---|
| ![Card before](https://cognizant-demo.devinenterprise.com/attachments/fe83d86b-f1bc-46b3-8863-a526a22cdd26/ui-20-card-before.png) | ![Card after](https://cognizant-demo.devinenterprise.com/attachments/5983bd3b-3b7b-45ea-a8b8-cb436534b6d8/ui-22-card-after.png) |

### Bill payment

| 🔴 Before: balance 212.00 | 🟢 After: Account View balance 0.00 |
|---|---|
| ![Payment before](https://cognizant-demo.devinenterprise.com/attachments/9a89feac-d020-4b13-9baf-c7d7ca950469/ui-27-bill-before.png) | ![Payment after](https://cognizant-demo.devinenterprise.com/attachments/311fda3c-f446-4932-8cbb-82a6c978f3ae/ui-30-account-paid.png) |

### Transaction and report

| Persisted transaction lookup | Accepted custom report request |
|---|---|
| ![Transaction persisted](https://cognizant-demo.devinenterprise.com/attachments/32168cb8-3081-47a8-9033-b57a7c1a106f/ui-26-transaction-persisted.png) | ![Report accepted](https://cognizant-demo.devinenterprise.com/attachments/f86bc477-d47f-4779-92d5-edcaf303b3d6/ui-33-report-accepted.png) |

## Recordings and supporting files

All files below are in
`/home/ubuntu/repos/Aws-Card-Demo2/modernization/docs/evidence/`.

- `ui-admin-account.mp4`: administrator CRUD, regular-user sign-on, account update;
  ends at the initial integration blockers.
- `ui-financial-paths.mp4`: fixed card/transaction paths and bill payment;
  ends at the report-request defect subsequently fixed.
- `ui-report-request.mp4`: successful report retest after the requester fix.
- `ui-report-request-row.txt`: database persistence/identity evidence for request 1.
- `ui-test-plan.md`: source-grounded procedure and runtime adjustments.
- `ui-gateway-rebuild.log`, `ui-transaction-rebuild.log`: targeted runtime rebuilds.
- `ui-runtime-gateway-fixed.log`, `ui-runtime-transaction-fixed.log`: startup evidence.

Key-screen screenshots: `ui-01-signon.png` through `ui-14-card-list-failure.png`,
then `ui-18-transactions-list.png` through `ui-33-report-accepted.png` (no 15–17).
`ui-14-card-list-failure.png`, `ui-18-transactions-list.png` and
`ui-31-report-request-failure.png` are **historical failure evidence**, not the final state.
The superseded setup callback screenshot is excluded from this report.

## Remaining actions and reproducibility

- No further user credentials or intervention are needed for the tested flows.
- Seed-data validity remains a fixture issue; no seed migration was changed by testing.
- Test data is deliberately mutated: credit limit/card name changed, transaction added,
  balance paid, report request retained; temporary manual user was deleted.
- Effective blueprint was checked. Suggested additions: document the hybrid local
  JDBC ports (5433–5436), host-accessible Kafka advertised listener/name resolution,
  process-only secret injection and live realm mapper updates after JSON changes.
- Suggested reusable guide:
  `/home/ubuntu/repos/Aws-Card-Demo2/.agents/skills/carddemo-ui-testing/SKILL.md`.
  It covers local setup, TOTP, persistent-write verification and evidence boundaries.
