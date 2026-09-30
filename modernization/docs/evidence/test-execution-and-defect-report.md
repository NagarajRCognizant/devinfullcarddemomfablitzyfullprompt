# CardDemo Java conversion — test execution and defect report

Covers every defect found while building, testing, validating functional parity and validating the
deployment of the converted application, the fix applied to each, and the result of re-testing it.
All artefacts named here are in this directory unless stated otherwise.

## 1. Test execution summary

| Suite | Command | Result | Log |
|---|---|---|---|
| Backend unit + integration + parity | `mvn39 clean verify` (JaCoCo ≥ 0.80 per module) | 634 tests, 0 failures | `backend-build-test.log`, `final-backend-verify.log` |
| Frontend | `npm test -- --run` | 103 tests in 21 files, 0 failures | `frontend-test.log`, `final-frontend-test.log` |
| Frontend lint | `npm run lint` | pass | `frontend-lint.log` |
| Frontend build | `npm run build` | pass | `frontend-build.log` |
| OpenAPI contracts | `npx @redocly/cli lint` | pass | `openapi-lint.log` |
| Helm chart | `helm lint` / `helm template` | pass, 17 resources | `helm-lint.log` |
| Compose deployment definition | `docker compose config` | pass | `compose-config.log` |
| Browser end-to-end | `npm run test` (Playwright) | 10 passed | `final-playwright.log`, `final-playwright-report/` |
| Browser golden paths (driven) | journey per `final-test-plan.md` | all assertions passed | `final-test-execution-run.md`, `final-browser-driver.log` |
| CI (GitHub Actions, PR #30) | Backend / Frontend / OpenAPI / Helm | 4 passed, 0 failed | PR #30 checks |

## 2. Defects found, root cause, fix and re-test

Every defect below was found by executing the converted system, was fixed, and was re-tested by
re-running the failing scenario plus the full suites above.

| # | Defect and where it was found | Root cause | Fix | Re-test result |
|---|---|---|---|---|
| D-01 | Sign-on never completed in the browser: the callback reported "Unable to reach the sign-on service" | React StrictMode invokes the initialisation effect twice; both invocations exchanged the same single-use OIDC authorization code, and the realm rejects the second exchange | `frontend/src/auth/session.ts` memoizes the in-flight restore promise and clears it when it settles | Fresh admin and regular-user sign-on reach the correct menus; unit test `exchanges the code of the redirect once when asked twice at the same time` (commit `8cf5684`) |
| D-02 | Card list returned HTTP 404 through the gateway | The gateway account route matched only `/api/accounts/**` and `/api/menu`, but the card, cardholder and statement-party endpoints live on the account service under other prefixes | Route predicate extended with `/api/cards`, `/api/cards/**`, `/api/cardholders/**`, `/api/statement-parties/**` | Card list, view, update and a separate re-fetch pass in the browser; `GatewayRouteTest` asserts the route of every screen's call (19 tests, commit `486406b`) |
| D-03 | Transaction list returned HTTP 401 | The transaction service requires audience `carddemo-transaction-service`; the realm's `carddemo-ui` client issued tokens without that audience | Added the `audience-transaction-service` mapper to `keycloak/realm-carddemo.json` and documented it in `26-keycloak-realm-configuration.md` | Transaction list/view/add, bill payment and report request all pass with a freshly issued token (commit `486406b`) |
| D-04 | Report request returned HTTP 500 | The controller stored `Principal.getName()`, which is the realm's UUID subject, in `report_request.requested_by VARCHAR(8)` | `ReportRequestController` derives the eight-character CardDemo user id from the token's `preferred_username` through `SignedOnUser` | Request accepted in the browser; the stored row is `USER0001`, length 8 (`ui-report-request-row.txt`); integration test uses a UUID subject and asserts `USER0001` (commit `637af37`) |
| D-05 | CI "Backend (Maven)" failed: 16 errors in `TransactionApiIntegrationTest` | The workflow provisioned a database per service but was never extended with one for the transaction service, so `carddemo_transaction_test` did not exist (`SQLState 3D000`) | Added the `transaction-db` service on port 5435 and `CARDDEMO_TRANSACTION_TEST_JDBC_URL` to `.github/workflows/modernization-ci.yml` | All four CI checks pass on PR #30 (commit `323a764`) |

Defects found earlier, during the build of the conversion itself, were fixed the same way and are
kept in the parity record rather than repeated here: the transaction batch coverage gap below the
JaCoCo gate, the statement reader retaining paged state between runs, the empty statement fixture,
and the report/statement assertions that did not match the fixed-width edited output
(`10-testing-and-parity.md`).

## 3. Findings that are not application defects

| Finding | Classification | Disposition |
|---|---|---|
| Account 11's shipped sample data (FICO `209`, area codes `002`/`553`, ZIP `24984` against `WA`) is refused by the account update edits | Test data — the source data does not satisfy the source program's own edits | Preserved AS-IS on both sides and recorded as **UCR-41** in `11-unsupported-construct-register.md`; the browser evidence corrects the offending fields on screen before updating the credit limit |
| A user update was refused with "Please modify to update" during the final recording | Test setup — the driver typed into the form before the asynchronous fetch had populated it, so it submitted an unchanged record | Driver given explicit waits for the fetched values; no application change. The screen's refusal is correct behaviour |
| The final recording's delete step asserted `getByLabel('Last Name')` on the Delete User screen | Test setup — that screen renders the read-only definition list, not inputs | Assertion reads the rendered text; no application change |
| A supplemental verification `SELECT` used column `report_type` | Test setup — the column is `report_name` | Query corrected; it confirmed requester `USER0001`, length 8 (`final-report-request-row.txt`) |
| Full image-based Compose startup not exercised | Environment — container registry rate limits | Deployment definition validated with `docker compose config` and the Helm chart rendered; the services were run as packaged jars against Compose PostgreSQL, Keycloak and Kafka |
| No comparison against a live mainframe execution | External system — no CICS/IMS/Db2/MQ region available | Parity is asserted against the source programs and their fixed-width output; recorded as a limitation in `17-limitations-and-review-required.md` |

## 4. Final re-test after all fixes

The whole procedure was re-executed once more, end to end, after every fix above, and captured in a
single uninterrupted screen recording (`final-complete-test-execution.mp4`, 7m07s): the three
automated suites on screen, then admin sign-on, user add/update/persistence/delete, regular-user
sign-on, account credit-limit update, card update, transaction add, the refused blank bill-payment
confirmation, the confirmed payment and the accepted report request — every step passing, with no
failure inside the capture. Details and per-assertion results are in `final-test-execution-run.md`.

## 5. Before and after

| Scenario | Before the fix | After the fix |
|---|---|---|
| Browser sign-on | callback error, no session | admin and regular-user sessions reach `COADM01C` / `COMEN01C` |
| Card list | HTTP 404 from the gateway | card list/view/update persist (`HAYDEN UI VERIFIED`) |
| Transaction list | HTTP 401 | list, view and a new transaction `0000000996722788` persist |
| Report request | HTTP 500 | request 1 accepted and stored under `USER0001` |
| CI backend job | 16 errors, job failed | 634 tests, job passed |

## 6. Outstanding

Nothing found during testing remains unfixed. What was not exercised at all — downstream report
printing, the batch jobs through the UI, exhaustive input permutations, concurrency and non-Chromium
browsers — is listed in `ui-runtime-test-report.md` and `17-limitations-and-review-required.md`, and
the items needing business sign-off are the REVIEW REQUIRED entries of that register.
