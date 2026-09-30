# Local Build, Execution and Troubleshooting Guide

Everything here runs on one developer machine with no cloud service, no external identity provider and
no real credential. Every value below is a throwaway local development value.

## 1. Prerequisites

| Tool | Version used here |
|---|---|
| JDK | 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`) |
| Maven | 3.9.9 (`/usr/local/bin/mvn39` on this machine) — 3.9+ is required by §3 |
| Node.js | 20+ with npm |
| PostgreSQL | 16 — either a local server or the Docker container below |
| Docker + Compose | 24+, optional (see §8 and UCR-09) |
| Kafka | 3.8 broker, only needed to drive the authorization service by hand (§14.2) |

## 2. Start PostgreSQL

Each service owns its own database, so six are needed — three for running and three for the
integration tests (the authorization pair is created in §14.1). A native server, or a container:

```bash
docker run -d --name carddemo-pg -p 127.0.0.1:5432:5432 \
  -e POSTGRES_DB=carddemo -e POSTGRES_USER=carddemo -e POSTGRES_PASSWORD=carddemo \
  postgres:16

for db in carddemo_test carddemo_identity carddemo_identity_test; do
  docker exec carddemo-pg psql -U carddemo -d postgres -c "CREATE DATABASE ${db}"
done
```

One instance with four databases is enough locally; Compose (§8) and the Helm chart give the two
services separate instances, which is what the ownership rule actually requires.

## 3. Build and test the backend

One reactor build covers all nine modules:

```bash
cd modernization
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 /usr/local/bin/mvn39 -B clean verify
```

Runs every test, the JaCoCo report of each module (`<module>/target/site/jacoco/index.html`) and the
coverage gates, and produces four runnable jars:

| Jar | Module |
|---|---|
| `account-service/target/carddemo-account-service.jar` | account management API |
| `identity-service/target/carddemo-identity-service.jar` | sign-on and user administration API |
| `gateway/target/carddemo-gateway.jar` | the single address the UI calls |
| `batch-account-extract/target/carddemo-batch-account-extract.jar` | the converted READACCT job |
| `authorization-service/target/carddemo-authorization-service.jar` | authorizations API and the Kafka authorizer (§14) |
| `batch-auth-purge/target/carddemo-batch-auth-purge.jar` | the converted CBPAUP0J purge job (§14.6) |

`mvn clean package -DskipTests` builds the jars alone; `mvn -pl account-service -am clean verify`
builds and tests one service with its dependencies.

## 4. Start the services

Three processes, in any order; the gateway retries until the services answer. In the default
`keycloak` mode all three verify the realm's tokens, so a realm has to be reachable: the supported way
to run one locally is the Compose stack of §8, which starts Keycloak, imports
`keycloak/realm-carddemo.json` and runs `keycloak/bootstrap.sh`. Point the processes at it with the two
addresses that are the same string only when the realm is reached on one address
(`29-keycloak-deployment-and-operations.md` §1). No process holds a signing key any more.

```bash
cd modernization
export KEYCLOAK_ISSUER_URI=http://127.0.0.1:8088/realms/carddemo   # the "iss" of the tokens
export KEYCLOAK_URL=http://127.0.0.1:8088                          # where JWKS is fetched

java -jar identity-service/target/carddemo-identity-service.jar &     # :8081
java -jar account-service/target/carddemo-account-service.jar &       # :8080
java -jar gateway/target/carddemo-gateway.jar &                       # :8090
```

Each service runs its own Flyway migrations on startup, so the first start of clean databases also
creates the schemas and loads the converted data: `V1__create_account_management_schema.sql` and
`V2__load_sample_data.sql` for the account service, `V1__create_identity_schema.sql` and
`V2__load_usrsec_data.sql` (the decoded USRSEC records) for the identity service.

| URL | Purpose |
|---|---|
| http://127.0.0.1:8090/actuator/health | gateway health |
| http://127.0.0.1:8081/swagger-ui.html | identity API browser |
| http://127.0.0.1:8080/swagger-ui.html | account API browser |
| http://127.0.0.1:8090/api/me | the USRSEC row behind the token — the first call the SPA makes after the realm signs a user on |
| http://127.0.0.1:8088/realms/carddemo/.well-known/openid-configuration | the realm's discovery document; its `issuer` must equal `KEYCLOAK_ISSUER_URI` |

Override any setting with a Spring property or the environment variable behind it, for example
`--spring.datasource.url=jdbc:postgresql://localhost:5433/carddemo` (or
`CARDDEMO_ACCOUNT_JDBC_URL`), `CARDDEMO_IDENTITY_URI` for the gateway's identity route and
`KEYCLOAK_CLOCK_SKEW_SECONDS` for the accepted clock drift. Stop with Ctrl-C, or `kill %1 %2 %3`.

A quick check that the edge is closed:

```bash
curl -si http://127.0.0.1:8090/api/users | head -1          # 401, no token
curl -si -H 'Authorization: Bearer nonsense' \
  http://127.0.0.1:8090/api/menu | head -1                 # 401, not a token of the realm
```

A token cannot be fetched with `curl` in the default mode by design: `carddemo-ui` has the direct
access grant disabled, so the only way to a browser token is the realm's pages with both factors
(§10). For a `curl`-driven smoke run use the retained legacy mode, which is what `smoke.sh` does (§9).

The seeded users are `ADMIN001`-`ADMIN005` (administrators) and `USER0001`-`USER0005` (regular) in
USRSEC, exactly as the shipped file has them. Signing on additionally needs a realm account with an
authenticator: `bootstrap.sh` seeds `admin001`, `user0001`, `user0002` and `user0003` from the
environment (`.env.example`), and every other user reaches the realm through the provisioning path of
the user-administration screens.

## 5. Start the UI

```bash
cd modernization/frontend
npm ci          # or npm install
npm run dev     # http://127.0.0.1:5173
```

The dev server proxies `/api` to the gateway on `http://127.0.0.1:8090`, so all three services must be
running, and the realm must be reachable at `VITE_KEYCLOAK_URL` (default `http://127.0.0.1:8088`) with
`http://127.0.0.1:5173/*` among the redirect URIs of `carddemo-ui` — the committed realm has both
`localhost` and `127.0.0.1` for that port. The app opens on the sign-on screen, whose only action
redirects to the realm; after the password and a TOTP code an administrator lands on the admin menu
(COADM01C) and a regular user on the main menu (COMEN01C), which is the routing COSGN00C performed.
Tokens live in `sessionStorage`, so a reload keeps the session and a closed tab ends it; **Sign off**
ends the realm's session too (`30-signing-on-with-mfa-user-guide.md`).

`npm test` runs the component tests; `npm run build` produces `dist/` and also type-checks
(`tsc --noEmit`), which is what `npm run lint` runs on its own.

## 6. Database reset and sample-data reload

This is the modernised equivalent of the `ACCTFILE` IDCAMS delete / define / REPRO job — one command:

```bash
docker exec carddemo-pg psql -U carddemo -d postgres \
  -c "DROP DATABASE IF EXISTS carddemo" -c "CREATE DATABASE carddemo"
```

Then restart the account service (§4); Flyway recreates the schema and reloads the seed data. To reload
the data without dropping the database, `DELETE` from `card_xref`, `account`, `customer` and rerun
`account-service/src/main/resources/db/migration/V2__load_sample_data.sql` with `psql`.

The user file is reset the same way — the equivalent of the `DUSRSECJ` define / REPRO job:

```bash
docker exec carddemo-pg psql -U carddemo -d postgres \
  -c "DROP DATABASE IF EXISTS carddemo_identity" -c "CREATE DATABASE carddemo_identity"
```

Restarting the identity service reloads the ten shipped users.

To regenerate `V2__load_sample_data.sql` from the supplied ASCII files:

```bash
python3 modernization/tools/generate_seed_sql.py
```

## 7. Run the converted READACCT batch job

```bash
cd modernization
java -jar batch-account-extract/target/carddemo-batch-account-extract.jar \
  --spring.batch.job.enabled=true \
  --spring.batch.job.name=readAcctJob \
  --carddemo.batch.readacct.output-directory=./target/readacct \
  run.id=1
```

Writes `acctdata.pscomp` (107-byte FB), `acctdata.arryps` (110-byte FB) and `acctdata.vbps` (84-byte
VB max) into the output directory, recreating them as the PREDEL step did. Increment `run.id` for each
fresh extract; relaunching with the same `run.id` restarts the previous execution from its last
committed chunk (CMD-07). The job reads the account database directly, as the JCL step read the same
VSAM cluster the online transactions used; it has no HTTP surface and does not need the other services.

## 8. Docker Compose (unverified — see UCR-09)

```bash
cd modernization
mvn clean package
npm --prefix frontend ci && npm --prefix frontend run build
docker compose build && docker compose up -d
docker compose down -v                            # stop and drop the volumes
```

The stack is the three databases (5433 account, 5434 identity, 5435 authorization), a Kafka 3.8
broker in KRaft mode, the three services (8081 identity, 8080 account, 8082 authorization), the
gateway (8090) and the UI on 5173 behind nginx, which proxies `/api` to the gateway.
Every port is published on the loopback interface only.

The image build could not be executed on this machine: the registry returned
`429 Too Many Requests` (`evidence/docker-build.log`). Treat these assets as **unverified**; §2-§7 is
the verified path.

### 8.1 A browser that is not on the Docker host

The stack of §8 publishes every port on the loopback interface and the realm is configured for it: the
`iss` of every token, the discovery document and the redirect URIs of `carddemo-ui` all say
`127.0.0.1`. A browser on another machine — a remote desktop, a tunnel, a shared preview address —
cannot reach that address, so the sign-on has nowhere to go, and the SPA reports `Unable to reach the
sign-on service`.

`docker-compose.public.yml` puts the realm on the same address as the page, under `/auth`, which nginx
proxies to Keycloak. One address then serves the SPA, the API and the realm, so the discovery document,
the sign-on pages, the TOTP prompt, the enrolment page and the token endpoint are all reachable
wherever the page is:

```bash
cd modernization
export CARDDEMO_PUBLIC_UI_URL=https://ui.example.test         # what the browser opens, no trailing slash
docker compose -f docker-compose.yml -f docker-compose.public.yml up -d --build
CARDDEMO_PUBLIC_UI_URL=$CARDDEMO_PUBLIC_UI_URL ./keycloak/public-redirects.sh
```

`public-redirects.sh` adds that address to the redirect URIs, the web origins and the sign-off redirect
URIs of `carddemo-ui`; the realm refuses a redirect it was not told about, and refuses the return from
signing off separately from the return from signing on. The loopback entries are kept, so the E2E suite
and a browser on the host still work — but only in whichever mode the stack is currently in, because the
issuer is one string: the services are told the public one, and a token whose `iss` is the loopback
address is then rejected.

The address has to be `https`, or a `localhost` name such as `http://carddemo.localhost:5173`. PKCE
hashes the verifier with WebCrypto, which a browser only exposes in a secure context; on a plain-`http`
address that is not loopback, `crypto.subtle` is absent and the redirect never happens.

The E2E suite talks to the realm directly as well, to read a user's enrolment state, and needs the
prefix the overlay added:

```bash
cd modernization/e2e
E2E_BASE_URL=http://carddemo.localhost:5173 \
E2E_KEYCLOAK_URL=http://127.0.0.1:8088/auth npx playwright test
```

## 9. Reconciliation and OpenAPI checks

```bash
python3 modernization/tools/reconcile_sample_data.py     # ASCII vs EBCDIC vs database, field by field
cd modernization && npx @redocly/cli lint                # all three contracts, per redocly.yaml
bash modernization/tools/smoke.sh                        # signs on, then drives every operation
```

`smoke.sh` defaults to the gateway on `http://127.0.0.1:8090`; pass another base URL as its first
argument. It signs on as an administrator and as a regular user and then exercises sign-on,
the admin menu, the four user screens and the two account screens with the tokens it received. It needs
the services started with `CARDDEMO_SECURITY_MODE=legacy`, because it signs on with `POST /api/signon`
and a shell cannot obtain a realm token that has passed a second factor; the default mode is exercised
end to end by the Playwright suite instead:

```bash
cd modernization/e2e && npm ci && npx playwright install chromium
npm run test          # against the Compose stack of §8, with the .env secrets exported
```

## 10. Signing on

The realm signs users on: the SPA redirects to it (Authorization Code + PKCE), it asks for the password
and for a six-digit TOTP code, and the RS256 token it returns is what every endpoint requires as
`Authorization: Bearer <token>`. `GET /api/me` then reports the USRSEC row and the menu to route to.
The walk-through is `27-mfa-enrolment-and-signon-flow.md`; the user-facing version is
`30-signing-on-with-mfa-user-guide.md`.

No CardDemo component sees a password in this mode and nothing compares `SEC-USR-PWD`. The column is
still there, still clear text and still written by the user-administration screens, which is UCR-14 and
unchanged — what changed is that the sign-on no longer reads it.

`carddemo.security.mode=legacy` (`CARDDEMO_SECURITY_MODE=legacy`) restores the previous behaviour in
full — `POST /api/signon`, one factor, a self-issued HS256 token and the clear-text comparison — and
then `CARDDEMO_JWT_SECRET` is required again. It exists for the `COSGN00C` parity evidence and as the
rollback path (`29-keycloak-deployment-and-operations.md` §4), and is not a deployment mode.

## 11. Logging and diagnostics

Console logging at `INFO` for `com.carddemo`; raise it with
`--logging.level.com.carddemo=DEBUG`. Useful switches:

- `--logging.level.org.hibernate.SQL=DEBUG` — SQL issued per request.
- `--logging.level.org.springframework.transaction=DEBUG` — transaction and rollback boundaries, the
  converted `SYNCPOINT` behaviour.
- `--logging.level.org.springframework.batch=DEBUG` — chunk commits and restart decisions.
- `--spring.flyway.loggers=slf4j` with `--logging.level.org.flywaydb=DEBUG` — migration detail.

## 12. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Connection to localhost:5432 refused` | PostgreSQL is not running — §2. |
| `Schema-validation: wrong column type … found [numeric], but expecting [bigint]` | the database predates the current `V1__create_account_management_schema.sql`; reset it — §6. |
| `FlywayValidateException: Migration checksum mismatch` | a migration file changed after being applied; reset the database rather than editing history. |
| Integration tests fail with `database "carddemo_test" does not exist` | create it — §2. The identity tests need `carddemo_identity_test` as well. |
| `401` on every call | the token is missing or expired, or it was signed by another realm — `KEYCLOAK_ISSUER_URI` must equal the `issuer` of the realm's discovery document, character for character. |
| `401` with `Connection refused: /127.0.0.1:8088` in a service log | a container was given the browser's address for the realm; `KEYCLOAK_URL` is the address the *service* can reach, `KEYCLOAK_ISSUER_URI` only the string in `iss`. |
| `Invalid authenticator code` although the app shows one | the code had already changed, or it was keyed twice — a code is single-use; if it happens always, the device clock has drifted. |
| the account waits although the password is right | five failures locked it out for up to fifteen minutes; clear it in the admin console (`29-keycloak-deployment-and-operations.md` §6). |
| `invalid_redirect_uri` on the realm's page | the SPA's origin is not among `carddemo-ui`'s redirect URIs; the committed realm has ports 5173 on `localhost` and `127.0.0.1` only. |
| `Unable to reach the sign-on service` and no redirect | the page is open on an address the realm is not on — the browser is not on the Docker host, or the address is plain `http` and not a `localhost` name, which leaves PKCE without WebCrypto (§8.1). |
| `Invalid redirect uri` after signing off | the address is among the redirect URIs but not among the sign-off ones, which the realm keeps separately — `public-redirects.sh` sets both (§8.1). |
| `403` on `/api/users` or `/api/admin-menu` | the token belongs to a regular user; those screens were only reachable from the admin menu, so they require an administrator. |
| Integration tests fail with `client version 1.32 is too old` | a Testcontainers path is being used; the suite is configured for the local database instead (L-02). Ensure `src/test/resources/application-test.yml` points at `carddemo_test`. |
| UI shows `Failed to fetch` | the gateway is not running on 8090, a service behind it is down, or the Vite dev server was not used (direct `dist/` hosting has no `/api` proxy). |
| Gateway answers `503` for a route | its circuit breaker is open because the service behind it is failing — check that service's log. |
| `npm run build` fails with `Cannot find name 'process'` | a Node global crept into `vite.config.ts`; the committed config avoids it deliberately (auto-fix AF-08). |
| Batch job "completes" writing nothing | it restarted a finished execution — pass a new `run.id`. |
| `Account Filter must  be a non-zero 11 digit number` on a valid-looking id | the id must be exactly 11 digits and non-zero; the double space is the source's own text, not a typo. |
| `docker compose build` fails with `429 Too Many Requests` | registry rate limit (UCR-09); authenticate to the registry or use §2-§7. |

## 13. Cleanup / rollback

```bash
docker compose down -v                      # if Compose was used
docker rm -f carddemo-pg                    # drops the local databases and all their data
mvn -f modernization/pom.xml clean
rm -rf modernization/frontend/node_modules modernization/frontend/dist
```

Nothing outside the `modernization/` directory and the local PostgreSQL instance is touched; the COBOL
sources under `app/` are unmodified. Reverting the branch removes the entire conversion.

## 14. Authorization service — local run

### 14.1 Create its database

The authorization service owns `carddemo_authorization` and never connects to `carddemo` or
`carddemo_identity`. Two more databases are needed (one to run, one for the integration tests):

```bash
for db in carddemo_authorization carddemo_authorization_test; do
  psql -U carddemo -d postgres -c "CREATE DATABASE ${db}"
done
```

Flyway creates the schema and loads the decoded IMS unload on first start (V1 DDL, V2 seed: 21
`PAUTSUM0` roots and 202 `PAUTDTL1` children). To start from empty:

```bash
psql -U carddemo -d postgres -c "DROP DATABASE IF EXISTS carddemo_authorization"
psql -U carddemo -d postgres -c "CREATE DATABASE carddemo_authorization"
```

### 14.2 A local Kafka broker

The service consumes `carddemo.authorization.request.v1` and replies on
`carddemo.authorization.reply.v1`; the topics are created on start-up by its `KafkaAdmin` beans. The
integration tests use an embedded broker, so a broker is only needed to drive the service by hand:

```bash
docker run -d --name carddemo-kafka -p 127.0.0.1:9092:9092 apache/kafka:3.8.0
```

Compose (§8) brings up the same broker as the `kafka` service in KRaft mode, and the
`carddemo-authorization-pgdata` volume plus the `authorization-db` instance on 5435.

### 14.3 Start it

```bash
cd modernization
KEYCLOAK_ISSUER_URI=http://127.0.0.1:8088/realms/carddemo \
KEYCLOAK_URL=http://127.0.0.1:8088 \
KEYCLOAK_CLIENT_ID=carddemo-authorization-svc \
KEYCLOAK_CLIENT_SECRET=... \
CARDDEMO_KAFKA_BOOTSTRAP=127.0.0.1:9092 \
CARDDEMO_ACCOUNT_SERVICE_URL=http://127.0.0.1:8080 \
java -jar authorization-service/target/carddemo-authorization-service.jar
```

It listens on 127.0.0.1:8082, and the gateway routes `/api/authorizations/**` to it. The account
service must be running: cardholder data is read over HTTP from
`/api/cardholders/by-card/{cardNumber}`, never from the account database.

### 14.4 Drive the screens

Sign on as in §10, then:

```bash
TOKEN=...   # the realm's token, from the browser session (§10) or from the legacy sign-on

# CPVS: the first page of five authorizations
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://127.0.0.1:8090/api/authorizations?accountId=00000000001"

# CPVS next page: pass the last key of the current page
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://127.0.0.1:8090/api/authorizations?accountId=00000000001&afterAuthKey=<lastKey>"

# CPVD: one authorization
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://127.0.0.1:8090/api/authorizations/1/76699998747444?selection=S"

# PF5: toggle the fraud mark
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  "http://127.0.0.1:8090/api/authorizations/1/76699998747444/fraud"
```

In the UI, *CPVS - Pending Authorizations* on the menu opens the summary screen and selecting a row
with `S` opens the detail screen, as the CPVS → CPVD transaction chain did.

### 14.5 Send an authorization request

```bash
docker exec -i carddemo-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server 127.0.0.1:9092 \
  --topic carddemo.authorization.request.v1 \
  --property parse.key=true --property key.separator='|' <<'EOF'
4111111111111111|{"requestId":"TRAN0000000001","cardNumber":"4111111111111111","transactionAmount":"10.00","messageType":"0100","processingCode":"000000","merchantId":"MERCH0000000001","merchantName":"LOCAL TEST","merchantCategoryCode":"5411"}
EOF

docker exec -i carddemo-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server 127.0.0.1:9092 --topic carddemo.authorization.reply.v1 \
  --from-beginning --property print.headers=true --max-messages 1
```

The reply carries the same correlation id header the request had, `00`/`0000-APPROVED` or
`05` with the decline reason, and is keyed by card number. A malformed payload lands on
`carddemo.authorization.request.DLT` after the bounded retry.

### 14.6 Run the purge batch

```bash
cd modernization
java -jar batch-auth-purge/target/carddemo-batch-auth-purge.jar \
  --spring.batch.job.enabled=true \
  --spring.batch.job.name=purgeExpiredAuthorizationsJob \
  --carddemo.batch.authpurge.expiry-days=5 \
  run.id=1
```

Prints the same report lines `CBPAUP0C` did. Evidence of a full run is in
`evidence/authorization-purge-batch-run.txt`. Reload the seed data afterwards by recreating the
database (§14.1), because the run deletes every expired authorization.

### 14.7 Reconcile the migrated data

```bash
python3 modernization/tools/generate_authorization_seed_sql.py   # re-decode and regenerate V2
python3 modernization/tools/reconcile_authorization_data.py . --container - --host localhost
```

Compares the decoded EBCDIC unload with the loaded database field by field and prints
`RECONCILIATION PASSED` or the differing fields
(`evidence/authorization-data-reconciliation.txt`).
