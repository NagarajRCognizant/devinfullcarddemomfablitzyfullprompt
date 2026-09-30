# Test Strategy and Quality-Gate Matrix

Playbook §8.9 and §8.12 item 7. The parity evidence itself is in `10-testing-and-parity.md`; this
document is the strategy and the gate matrix — what is tested at which layer, with what tool, and
which gate blocks delivery.

## 1. Layers

| Layer | Purpose | Scope | Tooling | Examples |
|---|---|---|---|---|
| Unit | one business rule in isolation, no Spring context | domain rules, decision engines, key arithmetic, formatting, validation | JUnit 5, AssertJ, Mockito | `AuthorizationDecisionEngineTest`, `AuthorizationKeyTest`, `DeclineReasonTest`, `ExpiredAuthorizationProcessorTest`, `AuthPurgePropertiesTest` |
| Integration (persistence) | the rule against real PostgreSQL, real Flyway, real locking | services, repositories, transactions | Spring Boot Test + PostgreSQL | `AuthorizationRequestProcessorIntegrationTest`, `AuthorizationConcurrencyIntegrationTest`, `PurgeAuthJobIntegrationTest` |
| Integration (messaging) | consumer/producer behaviour, headers, retry, DLT, idempotency | Kafka listener and outbox relay | Spring Kafka `@EmbeddedKafka` | `AuthorizationRequestListenerIntegrationTest` (`aMalformedRequestIsDeadLetteredWithoutRetrying`, `anAccountServiceFailureIsRetriedAndThenDeadLettered`) |
| API / contract | the HTTP contract each screen and each consumer sees, including error bodies | controllers, exception handlers, security | `@SpringBootTest` + `TestRestTemplate`, Redocly lint on the OpenAPI 3.1 documents | `AuthorizationApiIntegrationTest`, `AccountApiIntegrationTest` |
| Cross-service client | the boundary that replaced a direct file read | `CardholderLookupClient` against a stubbed account service | MockWebServer-style stub | `CardholderLookupClientTest` |
| Batch regression | job restartability, checkpointing, counters, report totals | Spring Batch job | Spring Batch Test | `PurgeAuthJobIntegrationTest` |
| Frontend unit/screen | field edits, paging, selection, message text, fraud toggle | React screens | Vitest + Testing Library | `AuthorizationSummaryScreen.test.tsx`, `AuthorizationDetailScreen.test.tsx` |
| Frontend build | type safety and bundling | whole app | `tsc`/Vite via `npm run build` | CI `frontend` job |
| Data migration reconciliation | every migrated row equals the decoded source record | authorization seed data | `tools/reconcile_authorization_data.py` | `docs/evidence/` |
| Security | authentication enforced, roles enforced, no anonymous access | all services and the gateway | integration tests plus the checklist in `18-coding-standards-checklist.md` §6 | `AuthorizationApiIntegrationTest` unauthenticated cases |
| Token validation | every way a token can be wrong is refused: `alg:none`, HS256 forged with a shared secret, another issuer, another audience, expired beyond the skew, no MFA evidence, a role not granted | the shared `security` module, used by all four processes | JUnit 5 with a generated RSA key pair (`TokenFixtures`) | `CardDemoJwtDecodersTest`, `AudienceValidatorTest`, `SecondFactorValidatorTest`, `RealmRoleAuthoritiesTest`, `SignedOnUserTest` |
| Provisioning | the realm account created, updated, deleted and role-assigned with the USRSEC row, and compensated when one of the two writes fails | identity-service against a stubbed realm | Spring Boot Test + a stub admin API | `UserAdminProvisioningIntegrationTest`, `UserReconciliationTest`, `KeycloakUserDirectoryTest` |
| Browser end-to-end (MFA) | both factors, enrolment, refused and replayed codes, lockout, the role journeys, provisioning a user into the realm through `COUSR01`, deep links, reload and sign-off at the realm | the whole Compose stack including Keycloak | Playwright (`e2e/`) | `signon.spec.ts`, `journeys.spec.ts`, evidence in `docs/evidence/e2e-playwright.log` |
| Performance | not executed — no production volumes are available | — | — | gap, see §6 |

A stub answers whatever the client asks it, so no provisioning test at that level can show that the realm
itself accepts the call: whether the roles a service account has been granted reach its token is a property
of the realm's client configuration, and only the browser end-to-end row covers it (`journeys.spec.ts`,
"an administrator adds a user, which the realm is given one too").

## 2. Functional-parity method

Parity is demonstrated, not asserted:

1. Each business rule is catalogued `BR-nn` per program in the `BRD-*.md` documents with its source
   paragraph.
2. `14-rtm.md` maps every `BR-nn` to the target component and the test that proves it.
3. The test asserts the *observable* output of the source rule — message text, response code, reason
   code, amount, counter, ordering, page size — not the target's internal structure.
4. Where the target deliberately differs (retired terminal mechanics, remediated defects), the delta
   is registered (UCR / CMD) and the test pins the *new* behaviour with the register entry named in
   the test or the code comment.
5. Migrated data parity is proven by re-decoding the source image and comparing field by field, not
   by spot checks.

## 3. Quality-gate matrix

| Gate | Command | Blocking | Threshold / pass condition | Where |
|---|---|---|---|---|
| Backend compile | `mvn -B clean verify` | yes | zero errors | CI `backend` |
| Unit + integration tests | same | yes | zero failures, zero errors, zero skipped in the authorization modules | CI `backend` |
| Coverage — line | JaCoCo `check-business-logic-coverage` | yes (`haltOnFailure`) | ≥ 0.80 per bundle, excluding applications, `config/**`, `api/dto/**`, `persistence/entity/**` | CI `backend` |
| Coverage — branch | same | yes | ≥ 0.80 per bundle | CI `backend` |
| Flyway migration on a clean database | service start-up in the integration tests; `scripts/` for local | yes | all migrations apply to an empty database; checksums stable | CI `backend` |
| Frontend lint | `npm run lint` | yes | zero errors | CI `frontend` |
| Frontend tests | `npm test` | yes | zero failures | CI `frontend` |
| Frontend build | `npm run build` | yes | build succeeds | CI `frontend` |
| OpenAPI 3.1 validation | `npx @redocly/cli@1.25.11 lint` | yes | zero errors | CI `contracts` |
| Helm chart | `helm lint` and `helm template` | yes | zero errors | CI `charts` |
| Data reconciliation | `python3 tools/reconcile_authorization_data.py` | yes, before a data cutover | `RECONCILIATION PASSED`, non-zero exit on any mismatch | cutover step, evidence in `docs/evidence/` |
| Browser end-to-end | `cd e2e && npm run test` | yes, before a release of the authentication path | 9 of 9 pass against the Compose stack with the realm imported and bootstrapped | `docs/evidence/e2e-playwright.log` |
| Container image build and stack start | `docker compose build` then `up` | not enforced in CI | every service `healthy`, `keycloak-bootstrap` exits 0 | `docs/evidence/keycloak-compose-health.log`; a cold cache can still hit a registry rate limit (UCR-09) |
| Dependency vulnerability scan | `mvn dependency:tree` review, `npm audit` | advisory | no Critical/High left unrecorded | see §6 |

## 4. Defect classification and exit criteria

| Severity | Definition | Exit criterion |
|---|---|---|
| P1 | a preserved business rule produces a different observable result than the source | zero open |
| P2 | a screen or API contract deviates (message text, field, status code, page size) | zero open |
| P3 | operational or non-functional shortfall with a documented workaround | recorded in `17-limitations-and-review-required.md` |
| P4 | cosmetic or documentation-only | recorded |

Delivery requires: zero open P1/P2, all blocking gates green, every source artifact dispositioned,
every UCR entry resolved or marked REVIEW REQUIRED with an owner, and every CMD entry resolved to
accepted-with-rationale or compensated-with-test.

## 5. Test data

| Data | Origin | Use |
|---|---|---|
| `carddemo` account/customer/xref seed | supplied EBCDIC files, decoded by the account migration | account and cardholder lookups |
| `carddemo_authorization` seed | `V2__load_authorization_sample_data.sql`, generated from the supplied IMS unload | summary/detail screens, purge, reconciliation |
| Authorization request messages | fixed-width payloads built from `CCPAURQY` in the tests | messaging tests |
| Fabricated edge cases | tests only, never seeded | boundary amounts, expiry window edges, duplicate deliveries |

No production data is used anywhere, and the seed migration is deterministic, so a test failure is
reproducible from a clean database.

## 6. Gaps (playbook §8.9, honest reporting)

| Gap | Impact | Handling |
|---|---|---|
| No performance or load test | throughput, consumer lag under load and batch elapsed time are unmeasured | production volumes, MQ statistics and the `CBPAUP0J` schedule are absent from the repository (E-16, UCR-12); partition count, listener concurrency and chunk size are documented defaults to be tuned in a performance environment before go-live |
| Container images not built | Compose and Kubernetes assets are syntax-reviewed but not executed | Docker Hub returned HTTP 429 on this VM (UCR-09); build in CI or with a mirrored registry before cutover |
| No automated dependency/CVE scanner in CI | a new Critical/High advisory would not fail the build | recommended follow-up: add an OWASP dependency check or equivalent stage to the `backend` job |
| No end-to-end test across gateway + services + frontend | integration is proven per boundary, not as one flow | recommended follow-up once images can be built; the Compose stack is the intended harness |
| Mainframe side-by-side dual run not possible here | parity is proven against source evidence and the supplied data, not against a live IMS/DB2 region | dual-run procedure is in `21-cutover-runbook.md` §4 for the engagement to execute |
