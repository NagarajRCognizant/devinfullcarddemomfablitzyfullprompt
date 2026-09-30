# Known Limitations, REVIEW REQUIRED Items and Scope Exclusions

## 1. Production-readiness exclusion

This application is designed for local functional-equivalence testing and demonstration. Production
deployment, cloud integration, production identity, encryption, secrets management, infrastructure
security, scalability, high availability, disaster recovery and production operations are outside the
current scope.

The Compose stack and the Helm chart under `deploy/helm/carddemo` are deployment scaffolding for that
local and demonstration purpose, not a production deployment: neither was applied to a cluster here.
The build contains no cloud service, no KMS or cloud secret manager, no cloud storage URI, no
production TLS certificate and no production connection string.

Authentication is no longer the source's own check: a Keycloak realm (`carddemo`) authenticates with a
password **and** a TOTP second factor, the SPA obtains its token with Authorization Code + PKCE, and the
services verify RS256 signatures against the realm's JWKS with issuer, audience, expiry and MFA
validation — `25-authentication-and-mfa-architecture.md` through
`30-signing-on-with-mfa-user-guide.md`. The realm is deployed here for local and demonstration purposes
(HTTP on loopback, `start-dev`, an in-image bootstrap administrator); a production deployment needs TLS,
a production Keycloak topology and externally managed secrets, which remains out of scope (§2, L-15,
L-21).

The clear-text `SEC-USR-PWD` column of USRSEC still exists and is still written by the
user-administration screens, which is UCR-14 and unchanged; what changed is that nothing compares it at
sign-on in the default mode. `carddemo.security.mode=legacy`, retained for the `COSGN00C` parity
evidence and as the rollback path, does reinstate the clear-text comparison, one factor and a shared
HS256 secret — it is not a supported deployment mode. Every credential in the repository is a throwaway
local development value (`carddemo`/`carddemo` against a local PostgreSQL); no client secret, TOTP
secret or signing key is committed at all — the realm JSON omits them and Compose requires each from
the environment.
No real customer data is committed — the only data is the sample data already supplied in `app/data/`.

## 2. Known limitations

| ID | Limitation | Impact | Mitigation |
|---|---|---|---|
| L-01 | *Closed for Compose.* `docker compose build` and `up` were executed with the Keycloak modernization: Keycloak, its database, the bootstrap job, the three services and the gateway all reached `healthy`/exit 0 (`evidence/compose-health.log`). The Helm chart is still unapplied (L-15) | none for Compose | a registry rate limit still recurs on a cold cache; `MAVEN_MIRROR_URL` and the BuildKit dependency cache work around the Maven side (UCR-09) |
| L-02 | Integration tests use a local PostgreSQL database instead of Testcontainers — the Docker Engine API here is older than the Testcontainers client supports | the suite needs `carddemo_test` and `carddemo_identity_test` to exist | documented in the guide §2; restore Testcontainers on a newer daemon (AF-07) |
| L-03 | No comparison against a live mainframe execution | parity rests on source-derived expectations plus the supplied sample data, not on captured mainframe output | byte-level record assertions and the three-way data reconciliation narrow the gap |
| L-04 | No automated accessibility audit (axe/Lighthouse) was run | UI accessibility is implemented (labels, `aria-invalid`, `role="alert"`, keyboard-reachable actions) but unverified | run an accessibility audit; browser-driven testing itself is now in place — the Playwright suite drives the realm's pages and the screens (`evidence/e2e-playwright.log`) |
| L-05 | No dependency-vulnerability scanner was available | no CVE posture is claimed | run `mvn dependency-check` / `npm audit` in an environment with the feeds available |
| L-06 | The batch job restarts from the last committed chunk when relaunched with the same parameters, unlike the source's all-or-nothing data-set rewrite | a relaunch with an unchanged `run.id` appends rather than rewriting | writers recreate the files on a new run, reproducing PREDEL; the guide instructs incrementing `run.id` (CMD-07) |
| L-07 | The update baseline snapshot travels in the request body instead of server-held terminal state | a client could tamper with the snapshot and defeat the record-changed check | acceptable locally; production would need a server-side snapshot or a version column (CMD-04) |
| L-08 | `COBDATFT` is absent from the repository and was reconstructed from its copybook and its caller's usage | the reissue date in the 107-byte extract may differ for inputs outside the observed pattern | supply the real module and re-verify (UCR-04) |
| L-09 | `CEEDAYS` feedback conditions that only IBM Language Environment can raise cannot be produced locally | four of the ten `CSUTLDTC` result strings are unreachable for the single date mask in use | all reachable results are tested (UCR-06) |
| L-10 | Authorisation is the source's single operator-type flag, now carried as a realm role in a Keycloak-issued token — still no finer-grained scopes, and a token already issued is not revoked when the user record changes | a 15-minute window in which a role or user change is not yet reflected in tokens already held | the access-token lifespan is 15 minutes and the realm supports refresh and session revocation; a permission model finer than `SEC-USR-TYPE` would be a business change (UCR-14) |
| L-11 | The inventoried artifacts belonging to capabilities outside Account Management, User Administration and Credit Card Authorizations were classified but not converted | those capabilities remain on the mainframe | each one carries a disposition and rationale in `13-artifact-disposition.md` |
| L-12 | No scheduler, runbook, design specification or downstream-consumer inventory exists in the repository | the batch schedule and the consumers of the three extracts are unknown | registered as UCR-12; no behaviour was inferred from absent artifacts |
| L-13 | Branch coverage in the mapper/detector and decision-flag packages is the lowest in the build | null-guard and flag permutations dominate the branch count | every module is above both 80% floors (`10-testing-and-parity.md` §2); every business outcome is asserted |
| L-14 | *Closed.* Maven 3.9.9 is installed at `/usr/local/bin/mvn39` and the whole reactor was verified with it (`evidence/authorization-mvn-verify.txt`) | none | — |
| L-15 | The CI workflow (`.github/workflows/modernization-ci.yml`), the Helm chart and the seven Dockerfiles were validated statically only — `helm lint`/`helm template` and a YAML parse — never executed on a runner or a cluster | packaging and pipeline assets are unverified | first push exercises the workflow; the chart needs a cluster (L-01, UCR-09) |
| L-16 | Kafka behaviour is proven against an **embedded single-broker** cluster only (`AuthorizationRequestListenerIntegrationTest`, `AuthorizationConcurrencyIntegrationTest`) | consumer-group rebalance, broker loss, replication, retention and consumer-lag behaviour are unverified; the partition count (3), listener concurrency and `replication-factor` (1) are defaults, not tuned values | run the same tests against a multi-broker cluster and set replication ≥ 3 before production (`24-dr-bcp.md` §2) |
| L-17 | No performance, load or volume testing of the authorizer, the screens or the purge job | the repository supplies no MQ statistics, transaction volumes or batch window, so throughput, partition count and chunk size cannot be sized from evidence | capture production volumes and run a load test before cutover (`19-test-strategy-and-quality-gates.md` §6) |
| L-18 | The IMS status-code abend paths (`COPAUA0C`, `COPAUS0C`, `COPAUS1C`, `CBPAUP0C`) cannot be exercised — there is no IMS, and the target's relational reads cannot return a DL/I status | three RTM Part B rows are traced to code but not to a test asserting the original status path | inspection only; the equivalent failure (data-access exception → rollback/step failure) is tested |
| L-19 | The authorization service reads cardholder data over HTTP from the account service with a `client_credentials` token of its own realm client; there is no circuit breaker, bulkhead or cache in front of that call | an account-service outage makes every authorization request retry and then dead-letter rather than decline | bounded retry and the DLT are implemented (CMD-12); add a circuit breaker and decide the outage policy (approve, decline or park) before production — a business decision, so REVIEW REQUIRED |
| L-20 | `carddemo_authorization` is seeded from the supplied EBCDIC unload, in which one root segment has a blank (non-numeric) account key and several declared fields are LOW-VALUES | one supplied authorization root and its children are not loaded | reported, not skipped silently (UCR-31, UCR-33); `evidence/authorization-data-reconciliation.txt` reconciles what was loaded |
| L-21 | The realm is run here as `start-dev` over HTTP on loopback, with hostname strictness off and an in-image bootstrap administrator | the TLS, proxy-header, cookie and hostname configuration a real deployment needs is documented but unverified | `29-keycloak-deployment-and-operations.md` §5 states the requirements; the realm already sets `sslRequired=external`, so anything but loopback demands TLS |
| L-22 | The Keycloak-backed integration tests use the running Compose realm rather than a Testcontainers Keycloak, for the same daemon reason as L-02 | the realm-dependent paths are covered by the Playwright suite against Compose, not by a self-contained JVM test | `e2e/` covers enrolment, both factors, refusals, lockout, logout and the role journeys; restore Testcontainers on a newer daemon (L-02) |

## 3. REVIEW REQUIRED items

Each needs a business or platform decision that source evidence cannot settle.

| Ref | Item | Impact | Risk | Recommended action | Confidence |
|---|---|---|---|---|---|
| UCR-01 | The ASCII and EBCDIC copies of the account file disagree on account 49 (`ACCT-ADDR-ZIP`, `ACCT-GROUP-ID`) | the seeded group id for one account | Low | data owner confirms the authoritative copy; the ASCII copy was used | High |
| UCR-02 | `Address Line 2` validation is commented out, so the field is entirely unvalidated | any characters can be stored | Medium | confirm whether the optional-alphanumeric edit should apply; preserved AS-IS meanwhile | High |
| UCR-04 | `COBDATFT` is missing | extract date formatting | Medium | supply the module or its specification | Medium |
| UCR-05.1 | The `COBDATFT` status is never checked by `CBACT01C` | a formatting failure passes unnoticed | Medium | decide the intended error behaviour | High |
| UCR-05.3 | The current-cycle-debit output field is populated on one branch only, so records can repeat the previous account's value | corrupt extract field | Medium | confirm whether a consumer depends on it before correcting | High |
| UCR-07 | Hard-coded amounts (`2525.00`, `1005.00`, `1525.00`, `-1025.00`, `-2500.00`), the FICO `300`-`850` range and the `1582` year bound | extract content and acceptance rules | Medium | business sign-off; each is a named constant ready to change | High |
| UCR-09 | Docker build / Compose startup unverified | packaging path only | Low | run where registry access exists | High |
| UCR-10.2 | When the account read fails after a successful cross-reference read, stale account fields stay on screen | misleading display | Medium | confirm the intended behaviour; preserved AS-IS | High |
| UCR-10.3 | The record-changed check compares date-of-birth using different offsets from those used to build the record | a lost-update window for one field | Medium | confirm and correct with a test; preserved AS-IS | High |
| UCR-12 | No operational, scheduler or specification evidence | production planning | Medium | obtain the scheduler definitions and the extract consumer list | High |
| CMD-04 | Client-supplied concurrency baseline | tamperable outside a local demo | Low locally | server-side snapshot or a version column for production | High |
| L-04 | Accessibility and browser validation not performed | UI compliance unknown | Low | run an audit and a browser pass | High |
| UCR-14 | Passwords are stored and compared in clear text, are capped at eight characters, never expire and cannot lock out; sign-on tells an unknown user apart from a wrong password | credential exposure and user-existence disclosure | **High** | hash the stored password and merge the two sign-on messages before any production use; preserved AS-IS meanwhile | High |
| UCR-15 | `COUSR03C` deletes without testing the outcome of its read, and the browse failures of `COUSR00C` only reach `DISPLAY` | a concurrent delete produces a different message in the converted service than on the mainframe | Low | confirm the intended behaviour; the converted transaction reports the failure rather than losing it | High |
| UCR-27 | The purge job's counter reversals are now written back (the source computed and discarded them), which releases available credit as the job's documented purpose requires | subsequent authorization decisions change: more credit is available than the AS-IS system allowed | **High** | business sign-off on the remediation, and a decision on historical IMS-migrated totals that carry the old defect; `persist-summary-adjustments=false` restores the AS-IS behaviour | High |
| UCR-30 | The declined-authorization total now accrues the current request's amount (the source accrued the previous message's) | the declined total on CPVS changes for existing accounts | Medium | business sign-off; migrated historical totals carry the old defect and may need restatement | High |
| UCR-28 | `PAUDBLOD` silently drops non-numeric records and loops on a read failure; the migration pipeline fails and reports instead | a historical load would report records the mainframe swallowed | Low | confirm before re-running a historical load | High |
| UCR-31 | A root segment in the supplied unload has a blank account key | one authorization root and its children are not migrated | Low | data owner confirms whether the record is real | High |
| L-19 | No outage policy for the cardholder lookup (approve, decline or park an authorization when the account service is unavailable) | availability vs. credit risk | **High** | business decision before production | High |
| L-16 | Single-broker Kafka proof only | delivery guarantees at scale unverified | Medium | rerun against a multi-broker cluster; set replication ≥ 3 | High |
| UCR-33 | `CBTRN03C`'s end-of-file branch adds the last transaction's amount to the page total, the account total and the grand total a second time, so a printed report's totals do not add up | the closing page total, the last account total and the grand total are each overstated by one amount | **High** | business sign-off on the correction: `TransactionReportWriter` prints totals that equal the printed detail lines, so a converted report will not tie to an archived mainframe report | High |
| UCR-34 | `CBSTM03A` stores a card's transaction count only when the card number changes, so the last card on the file is given a statement with no transactions; its working-storage table also caps at 51 cards × 10 transactions with no overflow check | the last cardholder's statement, and any statement beyond the cap, are wrong on the mainframe | **High** | business sign-off on the correction: the converted job reads a card's transactions from the database, so every cardholder gets a complete statement and statements can exceed ten lines | High |
| UCR-35 | `CBIMPORT`'s `3000-VALIDATE-IMPORT` reports that validation completed with no errors without examining anything | an import is declared valid whatever it contains | Medium | supply the intended validation rules; the empty step is preserved AS-IS meanwhile, because inventing rules would reject records the mainframe accepted | High |
| UCR-36 | `CBEXPORT` writes social security number, government-issued id, date of birth and the card security code to a flat file in clear, and the master-file print programs write whole card and customer record images to the job log | personal data and card security codes in files and logs | **High** | decide encryption or masking for the export file and whether the print jobs may run outside a controlled environment; preserved AS-IS so an export stays readable by the receiving branch | High |
| UCR-37 | `CBEXPORT` hard-codes branch `0001` and region `NORTH` on every exported record | a second branch cannot use the programme unchanged | Medium | business sign-off and a decision on parameterising both values; preserved AS-IS as named constants | High |
| UCR-38 | `CBACT04C`'s `1400-COMPUTE-FEES` is an empty paragraph, so no fee is ever calculated although the interest run is documented as calculating interest and fees | fees are silently not charged | Medium | supply the fee rules; the converted job likewise calculates no fee | High |
| UCR-39 | `CBACT04C` truncates monthly interest (no `ROUNDED`), and `CBTRN02C` can leave a transaction's category balance updated when the account rewrite fails (reason 109) | fractions of a cent lost per accrual; a partial posting on the mainframe | Medium | confirm the truncation is intended; the posting inconsistency cannot occur in the converted job, whose chunk transaction and compensated account adjustment are all-or-nothing (CMD-18) | High |
| UCR-41 | The shipped sample customer data does not satisfy the edits of `COACTUPC`: customer 11, for instance, carries FICO score `209` (below the 300 floor), area codes `002` and `553` (a first digit of `0` and a third of `3` are refused) and ZIP `24984` against state `WA` | an account read from the file cannot be rewritten until unrelated fields are corrected, which is a data problem, not a screen one | Medium | decide whether the sample file or the edits are authoritative; both the edits and the data are preserved AS-IS, so the converted screen refuses the same rows the mainframe screen refused | High |
| UCR-16 | A failed delete reports `Unable to Update User...`, and the user list acts on the first selected row while ignoring the rest | misleading operator messages | Low | confirm the wording before correcting; preserved AS-IS | High |

## 4. AS-IS defects deliberately preserved

Playbook §6.3 allows correcting an AS-IS defect only when the intended behaviour is corroborated by
source evidence. None of the following is corroborated, so each is preserved and covered by a test that
asserts the AS-IS outcome: UCR-02, UCR-05.1, UCR-05.3, UCR-05.4, UCR-10.2, UCR-10.3, the hard-coded
values of UCR-07, and the identity items UCR-14 (the two distinct sign-on messages), UCR-15 and
UCR-16. In the authorization scope the preserved items are UCR-20 … UCR-26 and UCR-29: the cash
balance reset to zero on approval, the unreachable decline-reason codes, the duplicated
summary-delete condition, the Julian-day expiry arithmetic across a year boundary, and the absent
index database. **No AS-IS defect was corrected in the account, user or identity scope.**

**Two AS-IS defects were corrected in the authorization scope**, both corroborated by a second
artifact and both recorded with before/after behaviour in `10-testing-and-parity.md` §7: UCR-30 (the
declined total accruing the previous message's amount) and UCR-27 (the purge computing the
available-credit adjustment and never writing it back). Both remain REVIEW REQUIRED: they change
subsequent business outcomes, and the AS-IS behaviour of UCR-27 is still reachable by configuration
for comparison runs.

**Three AS-IS defects were corrected in the transactions, cards and statements scope**, each
corroborated by the artifact that states the intended outcome and each recorded with before/after
behaviour in `10-testing-and-parity.md`: UCR-33 (report totals that do not add up), UCR-34 (the last
cardholder's statement left empty and the 51 × 10 cap) and the posting inconsistency of UCR-39,
which the converted job's transaction boundary removes. All three remain REVIEW REQUIRED because a
converted report or statement will not tie line for line to an archived mainframe one. Everything
else in this scope is preserved AS-IS, including UCR-35, UCR-36, UCR-37, UCR-38 and the hard-coded
bill-payment and interest classifications of UCR-07.

## 5. Authorization-scope operational exclusions

- No IMS, DB2, MQ or CICS region was available, so nothing was compared against a source execution.
- No multi-broker Kafka cluster, no schema registry and no topic-level ACLs: the topics are created
  by the service's `KafkaAdmin` with `replication-factor: 1` for local use, and the payload contract
  is enforced by the message records and their tests rather than by a registry (`23-messaging-modernization-mapping.md` §8).
- The reply outbox is drained by a scheduled publisher in-process; no outbox relay, no partitioned
  claim and no poison-message quarantine beyond the DLT.
- The purge job has no scheduler definition, because the repository supplies none: its window,
  frequency and expiry-days value are documented assumptions (`21-cutover-runbook.md`).
- No production tuning of partition count, listener concurrency, connection pool sizes or chunk size
  (L-17).

## 6. What could not be automated

- Docker image build and Compose startup (registry limits).
- Testcontainers-provisioned databases (Docker Engine API version).
- Accessibility auditing and browser-driven UI validation.
- Dependency vulnerability scanning.
- Container-image builds (registry rate limits).
- Any comparison against real mainframe output, including IMS/DB2/MQ behaviour and the DL/I status
  abend paths (L-18).
- Multi-broker Kafka verification and any performance or volume testing (L-16, L-17).
- Confirmation of the intended behaviour behind the REVIEW REQUIRED items — these need people, not
  tooling.
