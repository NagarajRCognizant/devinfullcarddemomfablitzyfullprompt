# Final CardDemo test execution

Executed 2026-09-24 against local Java 21 services, Vite, PostgreSQL, Keycloak and
Kafka using the hybrid setup in `carddemo-ui-testing`. Baseline includes 323a764
and the previously validated application fixes. All requested UI assertions and
automated commands passed in the final continuous capture.

## Caveats and coverage boundary

- One supplemental read-only SQL query initially used `report_type`, a nonexistent
  column. PostgreSQL suggested `report_name`; the corrected query passed and
  confirmed request 4 belongs to USER0001 (eight characters). This was a tester
  query typo, not an application defect. It occurred through the shell tool and
  was not displayed in the recording. No failure was cut from the video.
- Services were already running from setup; this recording reuses the stack,
  rather than demonstrating a cold full-stack startup.
- The account's invalid seed data remains documented by UCR-41. This run visibly
  corrects it before saving; it does not change the seed migration.
- Report acceptance and persistence were verified, not downstream batch printing.
  These browser paths do not exhaustively prove all 31 COBOL programs or a full
  application-image Compose deployment.
- A complete dry run passed before recording. Account/card fixtures were reset
  before the recorded pass. Existing unrelated transaction/report history remains.

## Commands and results

| Directory under modernization | Command | Recorded result |
|---|---|---|
| root | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn39 -o verify` | BUILD SUCCESS; 634 tests, 0 failures/errors/skips; exit 0 |
| frontend | `npm test -- --run` | 21 files / 103 tests passed; exit 0 |
| e2e | `npm run test` | 10 passed, one worker, about 1.5 minutes; exit 0 |

Credentials were supplied through process environment. A terminal displayed live
command output; the same continuous capture then switched to the browser.

## Browser assertions

- Passed: administrator password/TOTP login reaches COADM01C; user list shows ADMIN001.
- Passed: FINAL001 added, surname changed from REED to VERIFIED, persisted on
  separate fetch, deleted, and subsequent fetch shows User ID NOT found.
- Passed: regular-user password/TOTP login reaches USER0001 / COMEN01C.
- Passed: account 00000000011 initially shows balance 212.00 and credit 4,998.00.
- Passed: UCR-41 fields visibly corrected: FICO 209→700, phone area codes
  002/553→212/212, ZIP 24984→98101. Credit limit 5,001.00 saved and verified
  through a separate Account View.
- Passed: card 7427684863423209 listed and viewed; name changed from
  Hayden Pfannerstill to HAYDEN FINAL VERIFIED, saved, and separately re-fetched.
- Passed: seed transaction 0000000000683580 listed/viewed with Purchase at
  Abshire-Lowe and amount 504.77.
- Passed: transaction 0000000996722794 added, then separately re-fetched with
  FINAL UI VERIFIED and amount 12.34.
- Passed: blank bill confirmation refuses without payment; re-fetch retains
  balance 212.00 and no transaction ID. This is an expected negative assertion.
- Passed: Y pays, creates transaction 0000000996722795, and a separate account
  lookup confirms balance 0.00.
- Passed: Custom report request 4 accepted for 2022-06-01 through 2022-06-30.
- Passed: corrected read-only database query confirms requester USER0001, length 8.

## Visual evidence

| 🔴 Account before | 🟢 Persisted account update |
|---|---|
| ![Original account fixture](https://cognizant-demo.devinenterprise.com/attachments/21a3bb71-5984-4424-88de-5d7eb5df57ae/final-12-account-before.png) | ![Saved account separately re-fetched](https://cognizant-demo.devinenterprise.com/attachments/e5e1a896-345a-45d8-80a6-24ee9d931255/final-15-account-persisted.png) |

| Confirmed bill payment | Accepted report request |
|---|---|
| ![Payment successful](https://cognizant-demo.devinenterprise.com/attachments/9232a0e6-3930-4672-b3d6-27083e05c8ad/final-26-bill-paid.png) | ![Report request 4 accepted](https://cognizant-demo.devinenterprise.com/attachments/24bcefac-9359-49eb-927f-85e9b128b858/final-29-report-accepted.png) |

## Artifact paths

All files below are in
`/home/ubuntu/repos/Aws-Card-Demo2/modernization/docs/evidence/`.

- `final-complete-test-execution.mp4` — full 7m07.534s continuous screen capture.
  The recorder internally segmented its raw capture; all four consecutive chunks
  were losslessly joined with no trimming, time compression, or omitted content.
  Use this file, not the recording tool's automatic 32-second highlight.
- `final-test-execution-run.md` — this report.
- `final-test-plan.md` — execution plan.
- `final-backend-verify.log`
- `final-frontend-test.log`
- `final-playwright.log`
- `final-playwright-report/index.html`
- `final-live-output.log`
- `final-browser-driver.log`
- `final-report-request-row.txt`
- `final-ids.json`

Recorded screenshots:

```text
final-01-backend-passed.png
final-02-frontend-passed.png
final-03-playwright-passed.png
final-04-admin-menu.png
final-05-user-list.png
final-06-user-added.png
final-07-user-updated.png
final-08-user-update-persisted.png
final-09-user-deleted.png
final-10-user-delete-verified.png
final-11-main-menu.png
final-12-account-before.png
final-13-account-corrected.png
final-14-account-saved.png
final-15-account-persisted.png
final-16-card-list.png
final-17-card-before.png
final-18-card-saved.png
final-19-card-persisted.png
final-20-transaction-list.png
final-21-transaction-before.png
final-22-transaction-added.png
final-23-transaction-persisted.png
final-24-bill-before.png
final-25-bill-confirm-required.png
final-26-bill-paid.png
final-27-account-paid.png
final-28-report-form.png
final-29-report-accepted.png
```

## Reusable setup recommendations

Existing skill:
`/home/ubuntu/repos/Aws-Card-Demo2/.agents/skills/carddemo-ui-testing/SKILL.md`.
Keep hybrid startup, asynchronous Fetch synchronization, and persistence checks.
No additional skill file is needed.

The effective blueprint was checked. Add missing native authorization/transaction
test databases; document the Compose-infrastructure plus host-JVM/Vite fallback,
runtime database ports 5433–5436, host Kafka advertised-name resolution, process-only
credential injection, and live Keycloak mapper updates. Avoid hard-coded broker IPs.

User actions still needed: none for the tested flows.

Session: https://cognizant-demo.devinenterprise.com/sessions/64fac69e5c624ab1840ded2794105cbb
