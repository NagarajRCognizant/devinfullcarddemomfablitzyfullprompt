# Auto-Fix Log

Every build, migration, test, frontend or tooling failure encountered during the run, with its root
cause and the fix. No assertion was weakened, no test removed and no failure suppressed.

---

### AF-01 — Hibernate schema validation rejected the numeric key columns

- **Error:** `Schema-validation: wrong column type encountered in column [acct_id] in table [account]; found [numeric], but expecting [bigint]`
- **Root cause:** the first migration modelled `PIC 9(11)` keys as `NUMERIC(11)` while the entities
  mapped them to `Long`. The COBOL picture bounds the value range, not the SQL type.
- **Changed:** `V1__create_account_management_schema.sql`, `AccountEntity`, `CustomerEntity`, `CardXrefEntity`
- **Fix:** identifier columns became `BIGINT` with explicit `CHECK` constraints carrying the source
  ranges (account `0`-`99999999999`, customer `0`-`999999999`), and FICO became `INTEGER` with a
  `0`-`999` check. The range evidence moved from the type into a constraint, so nothing was lost.
- **Result:** startup validation passed. **Remaining limitation:** none.

---

### AF-02 — Fixed-width character columns failed validation

- **Error:** `Schema-validation: wrong column type encountered in column [active_status]; found [bpchar], but expecting [varchar(1)]`
- **Root cause:** the records are fixed-width, so the schema uses `CHAR(n)`; Hibernate 6 maps `String`
  to `VARCHAR` by default.
- **Changed:** every entity with a fixed-width field
- **Fix:** `@JdbcTypeCode(SqlTypes.CHAR)` plus `columnDefinition = "char(n)"`, keeping the trailing-
  space semantics the source relies on rather than switching the schema to `VARCHAR`.
- **Result:** validation passed; `AccountStoreIntegrationTest.theCustomerRecordKeepsTheFixedWidthCharacterFields`
  now guards it. **Remaining limitation:** none.

---

### AF-03 — Update tests failed on records the source's own edits reject

- **Error:** validation errors on state, ZIP, phone and SSN while exercising the *update* path with
  arbitrary seeded accounts.
- **Root cause:** not a defect. Several supplied sample records hold values the COACTUPC edits reject
  (unknown state/ZIP combinations, reserved SSN prefixes, zero FICO). The source has the same
  behaviour: such a record can be viewed but cannot be re-saved unchanged.
- **Changed:** `TestFixtures`, `AccountUpdateServiceTest`, `AccountApiIntegrationTest`
- **Fix:** the update and concurrency tests use accounts 9 and 12 and a fixture whose values pass the
  edits (SSN `123-45-6789`, FICO `700`, `NC`/`27610`, area code `201`). Production validation was
  **not** weakened; the rejecting records are now themselves covered by
  `AccountUpdateValidatorTest`.
- **Result:** green. **Remaining limitation:** recorded as a data observation, not a code change.

---

### AF-04 — A concurrency test asserted no message

- **Error:** the record-changed test expected an empty message.
- **Root cause:** the test expectation was wrong; `COACTUPC 9700-CHECK-CHANGE-IN-REC` sets
  `Record changed by some one else. Please review`.
- **Changed:** `AccountUpdateServiceTest`
- **Fix:** the test was corrected to the source text (a test expectation demonstrably wrong is the
  only case in which a test may change).
- **Result:** green. **Remaining limitation:** none.

---

### AF-05 — Surefire selected no tests

- **Error:** `-Dtest=AccountApiIntegrationTest+ReadAcctJobIntegrationTest` matched nothing.
- **Root cause:** tooling mistake — Surefire separates selectors with commas.
- **Changed:** nothing in the repository.
- **Fix:** `-Dtest=AccountApiIntegrationTest,ReadAcctJobIntegrationTest`.
- **Result:** the intended tests ran. **Remaining limitation:** none.

---

### AF-06 — Offline packaging failed on missing plugin dependencies

- **Error:** `mvn -o … package` could not resolve `plexus-utils`, `commons-io`, `maven-archiver`,
  `plexus-archiver`.
- **Root cause:** the local repository had the compile dependencies but not the jar plugin's own
  transitive dependencies.
- **Changed:** nothing in the repository.
- **Fix:** the build was rerun online once to populate the repository.
- **Result:** `BUILD SUCCESS`. **Remaining limitation:** a first build needs network access to a Maven
  repository; the running application needs none.

---

### AF-07 — Testcontainers could not talk to the local Docker daemon

- **Error:** `client version 1.32 is too old. Minimum supported API version is 1.40`
- **Root cause:** the Docker Engine API available on this machine predates the minimum the
  Testcontainers client requires.
- **Changed:** `PostgresIntegrationTest`, `src/test/resources/application-test.yml`
- **Fix:** integration tests point at the documented local PostgreSQL 16 instance
  (`carddemo_test`) instead of a container, running the same migrations and the same assertions.
- **Result:** all integration tests run. **Remaining limitation:** L-02 — the suite needs a local
  database rather than provisioning one; Testcontainers should be restored where the daemon is newer.

---

### AF-08 — Frontend production build failed on a Node global

- **Error:** `Cannot find name 'process'. Do you need to install type definitions for node?`
- **Root cause:** `vite.config.ts` read `process.env.CARDDEMO_API_URL` without `@types/node`.
- **Changed:** `frontend/vite.config.ts`
- **Fix:** the environment lookup was removed and the dev-server proxy points at
  `http://127.0.0.1:8080` directly — one fewer dependency, and the local target is fixed anyway.
- **Result:** `tsc --noEmit && vite build` clean. **Remaining limitation:** the API base URL is changed
  by editing the config rather than by an environment variable.

---

### AF-09 — Component assertions failed on whitespace

- **Error:** Testing Library could not find padded money values or the double-spaced legacy message.
- **Root cause:** the default text normaliser collapses whitespace, and the exactness of that
  whitespace *is* the parity requirement (the `PIC +ZZZ,ZZZ,ZZZ.99` mask, the source's double space).
- **Changed:** the two screen test files
- **Fix:** the affected queries pass an identity normaliser (`normalizer: (text) => text`) so the
  assertion stays strict rather than being relaxed to a substring match.
- **Result:** 8 tests pass. **Remaining limitation:** none.

---

### AF-10 — OpenAPI lint failed on production-security rules

- **Error:** Redocly reported `security-defined`, a localhost server warning and a missing 4XX response
  on `/api/role`.
- **Root cause:** the default ruleset assumes a production API. Production security is out of scope by
  instruction; the server *is* localhost by requirement; and `/api/role` normalises an unknown type to
  regular rather than rejecting it, which is the source's own behaviour.
- **Changed:** `modernization/redocly.yaml`
- **Fix:** those three rules were disabled with the reason recorded in the config, rather than adding
  a security scheme the build must not have or inventing an error response the API does not return.
- **Result:** lint passes (`evidence/openapi-lint.log`). **Remaining limitation:** the specification is
  intentionally not production-hardened. *Superseded by AF-21: once the contracts carried a bearer
  security scheme and `/api/role` was gone, only the localhost-server rule stayed disabled.*

---

### AF-11 — Docker image build could not pull base images

- **Error:** Docker Hub `429 Too Many Requests`; the Public ECR mirror returned a data-limit error.
- **Root cause:** unauthenticated registry rate limits on this machine.
- **Changed:** nothing.
- **Fix:** none available here. The assets were kept and the failure captured verbatim
  (`evidence/docker-build.log`).
- **Result:** **not verified.** **Remaining limitation:** UCR-09 — Docker build and Compose startup
  remain unverified and must be run where registry access exists.

---

### AF-12 — Reconciliation script crashed on its own SQL wrapper

- **Error:** `TypeError: string indices must be integers`
- **Root cause:** the psql output parser assumed a dict row while the wrapper returned aliased text.
- **Changed:** `tools/reconcile_sample_data.py`
- **Fix:** the aliasing and row parsing were corrected and the helpers typed.
- **Result:** the script runs. **Remaining limitation:** none.

---

### AF-13 — Reconciliation referenced columns that do not exist

- **Error:** `column "current_balance" does not exist` (also `current_cycle_credit`,
  `current_cycle_debit`)
- **Root cause:** the script used descriptive names while the schema keeps the source's abbreviations.
- **Changed:** `tools/reconcile_sample_data.py`
- **Fix:** the real names `curr_bal`, `curr_cyc_credit`, `curr_cyc_debit`, `addr_zip`.
- **Result:** reconciliation completed. **Remaining limitation:** none.

---

### AF-14 — Reconciliation found a credit-limit mismatch against the database

- **Error:** `acct 00000000009 credit_limit: sample=8201.00 database=6000 MISMATCH`
- **Root cause:** the runtime database still held rows written by the earlier update smoke tests — the
  seed load was correct, the database was stale.
- **Changed:** nothing in the repository.
- **Fix:** the runtime database was dropped and recreated, Flyway reapplied, reconciliation rerun.
- **Result:** 550 account field comparisons matched; `RECONCILIATION PASSED`. **Remaining
  limitation:** reconciliation must run against a freshly seeded database — documented in the guide.

---

### AF-15 — One supplied record differs between the ASCII and EBCDIC copies

- **Error:** `account 49: ACCT-ADDR-ZIP / ACCT-GROUP-ID differ`
- **Root cause:** a genuine discrepancy in the *supplied data*, not in the conversion.
- **Changed:** `tools/reconcile_sample_data.py`
- **Fix:** recorded in `KNOWN_DATA_DIFFERENCES` so it is asserted and printed rather than normalised
  away, and registered as UCR-01 with REVIEW REQUIRED.
- **Result:** zero *unexpected* differences, with the known one visible in the evidence.
- **Remaining limitation:** the authoritative copy must be confirmed by the data owner.

---

### AF-16 — The disposition report counted placeholder files

- **Error:** the generator reported 162 artifacts, including `.gitkeep` files.
- **Root cause:** the directory walk did not skip dot files.
- **Changed:** `tools/gen_disposition.py`
- **Fix:** hidden files are skipped; the report regenerated at 159 real artifacts.
- **Result:** the inventory count matches the directory counts. **Remaining limitation:** none.

---

### AF-17 — Mapping documentation named methods that do not exist

- **Error:** no build failure — a documentation review found `readAccountChain`, `editSigned9v2`,
  `editUsPhone`, `editStateCode`, `editStateZipCombination`,
  `findFirstByAcctIdOrderByCardNum`, `DateValidationService.validate`, a `fieldsInError` response
  field and a `POST /api/role` operation, none of which exist in the code.
- **Root cause:** the mapping and API documents were drafted before the final signatures settled.
- **Changed:** `09-cobol-to-java-mapping.md`, `08-api.md`, `06-screen-mapping.md`,
  `04-architecture.md`
- **Fix:** every named class, method, DTO field and operation was re-read from the source and
  corrected (`editAccountId`, `readAccount`,
  `findFirstByXrefAcctIdOrderByXrefCardNumAsc`, `editSignedAmount`, `editUsPhoneNumber`,
  `editUsStateCode`, `editUsStateZipCombo`, `validateYyyyMmDd`, `fieldFlags`,
  `GET /api/role`).
- **Result:** the documentation matches the implementation. **Remaining limitation:** none. *The
  `/api/role` operation named here was later removed altogether — see AF-25.*

---

AF-18 onwards belong to the second phase: converting sign-on and user administration and splitting the
modular monolith into the account and identity services with a gateway.

---

### AF-18 — The new module could not resolve Flyway during test compilation

- **Error:** `package org.flywaydb.core.api does not exist` while compiling `account-domain` after the
  monolith was split — its shared integration-test base class re-applies the migrations of the service
  under test.
- **Root cause:** Flyway had been a runtime dependency of the old backend module, so the new library
  module compiled against a class it did not declare.
- **Changed:** `account-domain/pom.xml`
- **Fix:** `flyway-core` declared with `provided` scope — the module compiles against it while each
  consuming service keeps supplying its own runtime copy, rather than the library forcing one on them.
- **Result:** the reactor compiles. **Remaining limitation:** none.

---

### AF-19 — Split test sources lost their shared fixtures

- **Error:** `cannot find symbol: class FormFixtures` in four account service test classes.
- **Root cause:** the fixtures moved to `com.carddemo.FormFixtures` in the account service test tree,
  which is no longer the package the tests themselves are in, so the previously implicit reference no
  longer resolved.
- **Changed:** `AccountUpdateServiceTest`, `AccountUpdateValidatorTest`, `AccountChangeDetectorTest`,
  `AccountScreenMapperTest`
- **Fix:** the explicit import was added in each class; the fixtures were not duplicated, so every test
  still asserts against one definition of the sample forms.
- **Result:** tests compile and pass. **Remaining limitation:** none.

---

### AF-20 — The gateway module had no entry point

- **Error:** `Unable to find main class` when packaging `gateway`.
- **Root cause:** the module was scaffolded with its routes and security configuration but no
  annotated application class.
- **Changed:** `gateway/src/main/java/com/carddemo/GatewayApplication.java`
- **Fix:** the Spring Boot entry point was added, so the module produces a runnable jar like the other
  three.
- **Result:** `carddemo-gateway.jar` is produced and starts. **Remaining limitation:** none.

---

### AF-21 — Redocly rejected an empty parameter list

- **Error:** ``Expected type `ParameterList` (array) but got `null` `` on the account contract.
- **Root cause:** a `parameters:` key was left behind with no items when an operation's query
  parameters were replaced by the token.
- **Changed:** `account-service/src/main/resources/openapi/openapi.yaml`
- **Fix:** the empty key was removed rather than filled with a placeholder parameter.
- **Result:** both contracts lint clean.
- **Remaining limitation:** none. The lint configuration now disables only the localhost-server rule,
  because the contracts declare the bearer scheme the services actually require (AF-10 superseded).

---

### AF-22 — Every account API test returned 401 once the token was required

- **Error:** `Status expected:<200> but was:<401>` across the account MockMvc tests.
- **Root cause:** not a defect — the tests predated authentication and sent no `Authorization`
  header.
- **Changed:** the account API test classes and their test configuration
- **Fix:** the tests now sign their requests with a JWT carrying the operator type under test, which
  is what the source's COMMAREA carried. The security filter chain was not relaxed and no endpoint was
  opened to make a test pass.
- **Result:** the suite passes with authentication enforced, and the unauthenticated and wrong-type
  cases are asserted as well. **Remaining limitation:** none.

---

### AF-23 — Identity tests interfered with each other through the shared database

- **Error:** intermittent `User ID already exist...` and off-by-one page assertions depending on test
  order.
- **Root cause:** the add/update/delete tests wrote to the same `security_user` table that the list
  tests browse, and the ten seeded records are also the paging fixture.
- **Changed:** the identity integration tests
- **Fix:** each test class rolls back or removes what it inserts and the list assertions key off the
  seeded records only — rather than reordering tests or disabling the seed migration.
- **Result:** the suite passes in any order. **Remaining limitation:** the tests need
  `carddemo_identity_test` to exist (L-02).

---

### AF-24 — The coverage gate failed on the new modules

- **Error:** `Rule violated for bundle …: branches covered ratio is 0.xx, but expected minimum`.
- **Root cause:** the split re-scoped each JaCoCo bundle, so the change detector's null-guard branches
  and the exception handlers, previously diluted by the whole monolith, dominated their module's ratio.
- **Changed:** the change-detector and exception-handler test classes
- **Fix:** the missing branches were covered by tests asserting real outcomes — each null/blank
  permutation of the changed-record comparison and every mapped exception status. The thresholds were
  not lowered.
- **Result:** all gates pass. **Remaining limitation:** none.

---

### AF-25 — Deployment variables did not match the properties the services read

- **Error:** no build failure — review found Compose and the Helm chart setting
  `SPRING_DATASOURCE_URL`/`USERNAME`/`PASSWORD` while the services read
  `CARDDEMO_ACCOUNT_*` and `CARDDEMO_IDENTITY_*`, and the signing key was not passed at all.
- **Root cause:** the deployment assets were written against the monolith's single datasource.
- **Changed:** `docker-compose.yml`, `deploy/helm/carddemo/**`
- **Fix:** each service receives its own database variables plus `CARDDEMO_JWT_SECRET`; the chart takes
  the secret from a Kubernetes Secret instead of a literal.
- **Result:** `helm lint` and `helm template` render the intended environment; the values match the
  property names in each `application.yml`.
- **Remaining limitation:** neither Compose nor the chart was executed here (L-01, L-15).

---

### AF-26 — Documentation and one DTO still described the removed role endpoint

- **Error:** no build failure — review found `RoleResponse`, `GET /api/role`, `X-CardDemo-User-Type`,
  a "modular monolith" architecture, a single database and a nonexistent `app/jcl/USRSEC.jcl` cited
  across the documents, the migration comments and the smoke script.
- **Root cause:** those artifacts described the first phase, in which the operator type was chosen by
  the caller because no sign-on had been converted.
- **Changed:** `RoleResponse` → `MenuResponse` (with its OpenAPI schema and the UI types),
  `04-architecture.md`, `08-api.md`, `15-local-execution-guide.md`, `01`, `02`, `06`, `09`, `13`, `14`,
  `17`, `BRD-COMEN01C.md`, `README.md`, `tools/smoke.sh`, `tools/gen_disposition.py`, the identity
  migration comments
- **Fix:** every statement was re-derived from the code as it now stands: `GET /api/menu` with the type
  taken from the verified token, two services with their own databases, and `app/jcl/DUSRSECJ.jcl` as
  the actual USRSEC definition. Historical entries in this log keep their original wording and carry a
  forward reference instead.
- **Result:** no document, comment or script names an endpoint, header, class or file that does not
  exist. **Remaining limitation:** none.

---

### AF-27 — The authorization seed migration failed on NUL characters

- **Error:** Flyway aborted `V2__load_authorization_sample_data.sql` with
  `ERROR: insufficient data left in message`, which failed every `authorization-service` integration
  test.
- **Root cause:** the supplied EBCDIC IMS image holds LOW-VALUES (`X'00'`), not blanks, in
  `PA-AUTH-STATUS` and `PA-ACCOUNT-STATUS`; the decoder carried them through as NUL code points,
  which no PostgreSQL text type can store.
- **Changed:** `tools/ims_authorization_unload.py`, the regenerated seed migration,
  `docs/05-data-mapping.md`, `docs/11-unsupported-construct-register.md`
- **Fix:** `unpack_text` converts LOW-VALUES to blanks in one place, because every in-scope program
  reads both states as "not set". The source condition and the conversion are recorded as UCR-32
  rather than being silently normalised.
- **Result:** the migration applies to an empty `carddemo_authorization`; the generated file contains
  zero NUL bytes and reconciliation passes. **Remaining limitation:** UCR-32 stays REVIEW REQUIRED
  until it is confirmed that no out-of-scope program populates those two fields.

---

### AF-28 — The purge job would not start outside the tests

- **Error:** `BeanDefinitionOverrideException: Invalid bean definition with name 'clock'` —
  `APPLICATION FAILED TO START` when the built `batch-auth-purge` jar was run.
- **Root cause:** `AuthPurgeBatchApplication` declared its own `clock` bean while `common` already
  provides `ClockConfig`. The integration test sets
  `spring.main.allow-bean-definition-overriding=true` to install its fixed clock, so the clash was
  invisible to the test suite and only appeared on a real submission of the job.
- **Changed:** `batch-auth-purge/src/main/java/com/carddemo/AuthPurgeBatchApplication.java`
- **Fix:** the duplicate bean was removed; the job uses the shared `ClockConfig` clock, and the test
  keeps overriding it with a fixed instant.
- **Result:** the job runs from the jar and completes — evidence in
  `docs/evidence/authorization-purge-batch-run.txt`. **Remaining limitation:** none.

---

### AF-29 — Reconciliation reported false money mismatches

- **Error:** `RECONCILIATION FAILED (438)` with entries such as
  `source=Decimal('1.21') database=Decimal('1.2099999999999999644…')`.
- **Root cause:** the reconciliation script read `json_agg` output through Python's default JSON
  parser, which turns a JSON number into a binary `float`. The stored columns are `numeric(12,2)`;
  the rounding was introduced by the comparison, not by the migration.
- **Changed:** `tools/reconcile_authorization_data.py`
- **Fix:** the JSON is parsed with `parse_float=Decimal` and `parse_int=Decimal`, so a COMP-3 amount is
  never routed through binary floating point. The `psql` invocation also takes `--host` and reads the
  password from `PGPASSWORD` instead of assuming a local socket.
- **Result:** `RECONCILIATION PASSED` — 21 roots, 202 children, 3261 field comparisons.
  **Remaining limitation:** none.

---

## Iteration summary

| Result | IDs |
|---|---|
| Fixed and verified | AF-01 … AF-05, AF-07 … AF-10, AF-12 … AF-14, AF-16 … AF-24, AF-26 … AF-29 |
| Environment limitation, no fix available | AF-06 (first build needs network), AF-11 (registry limit → UCR-09) |
| Recorded as a source-data issue rather than fixed | AF-15 (→ UCR-01) |
| Fixed, but the asset itself remains unexecuted here | AF-25 (→ L-01, L-15) |

Not applicable per playbook §7: production penetration testing, MFA/OAuth testing, cloud-security
scanning and production infrastructure scanning. No dependency-vulnerability scanner was available on
this machine, so no vulnerability results are claimed.
