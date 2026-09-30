# Target Architecture

## 1. Shape: three services behind a gateway

§1-§6 describe the account and identity contexts as originally delivered; §7 adds the authorization
context. The gateway, the token contract and the layering rules are unchanged by that addition.


The in-scope closure holds two capabilities that the source keeps apart: sign-on and user
administration own USRSEC, and account management owns ACCTDAT/CUSTDAT/CARDXREF. No program reads the
other capability's files, and no unit of work spans both — `COSGN00C` hands over to the next
transaction through the COMMAREA, not through a shared read. The two capabilities are therefore two
bounded contexts, and the target is **one service per context, each owning its own database**, with a
gateway in front and a separate React application for the UI.

The boundary is drawn where the source has no transaction to break. The one place the source *does*
need a single unit of work — the account and customer rewrite pair of `COACTUPC` — stays inside the
account service, in one local database transaction, so nothing distributed is introduced.

```
modernization/
  common/                 Java 21 library: COBOL semantics, field edits, messages, exceptions
  account-domain/         account entities and repositories (ACCTDAT, CUSTDAT, CARDXREF)
  account-service/        Spring Boot 3.3.4  → :8080  owns the carddemo database
  identity-service/       Spring Boot 3.3.4  → :8081  owns the carddemo_identity database
  batch-account-extract/  Spring Boot + Spring Batch, READACCT/CBACT01C, scheduled workload
  authorization-domain/   authorization entities, repositories, decision engine, message records
  authorization-service/  Spring Boot 3.3.4  → :8082  owns the carddemo_authorization database
  batch-auth-purge/       Spring Boot + Spring Batch, CBPAUP0J/CBPAUP0C expired-authorization purge
  gateway/                Spring Cloud Gateway → :8090  the single address the UI calls
  frontend/               React 18.3 + TypeScript 5.5 + Vite 5.4 → dev proxy to :8090
  docs/                   plan, discovery, rules, mappings, registers, RTM, evidence
  deploy/helm/            chart for the service, gateway and batch workloads
  tools/                  reconciliation and report generators (Python)
  docker-compose.yml, per-service Dockerfiles
```

`common` is a library, not a service: it holds the COBOL semantics both contexts need (fixed-width
text, `PIC` edits, `COMP-3`) and nothing that belongs to either domain. `account-domain` is separated
from `account-service` because the batch job needs the same entities and repositories as the online
service without inheriting its web layer.

## 2. Service ownership and boundaries

| Service | Owns | Source counterpart | Reached by |
|---|---|---|---|
| `identity-service` | `carddemo_identity`: the converted USRSEC record | `COSGN00C`, `COADM01C`, `COUSR00C`-`COUSR03C` | the UI through the gateway |
| `account-service` | `carddemo`: accounts, customers, cross-reference | `COMEN01C` (access rule), `COACTVWC`, `COACTUPC` | the UI through the gateway; `GET /api/cardholders/{cardNumber}` also by the authorization service |
| `authorization-service` | `carddemo_authorization`: pending authorization summaries and details, fraud reports, the request log and the reply outbox | `COPAUA0C`, `COPAUS0C`, `COPAUS1C`, `COPAUS2C` | the UI through the gateway, and Kafka for authorization requests |
| `batch-auth-purge` | nothing; reads the authorization database it shares `authorization-domain` with | `CBPAUP0J` + `CBPAUP0C` | launched per run, no HTTP surface |
| `batch-account-extract` | nothing; reads the account database it shares a module with | `READACCT.jcl` + `CBACT01C` | launched per run, no HTTP surface |
| `gateway` | nothing | the CICS transaction identifiers that routed a terminal | the browser |

No service reads another service's database. The account and identity services make no runtime call
to each other: the only thing they share is the token, which the identity service signs and the
account service and the gateway verify. The account service never asks the identity service who the
caller is, because everything it needs — the user id and the operator type — is a claim in the token.
The one runtime service-to-service call in the platform is the authorization service reading
cardholder data through the account service's `GET /api/cardholders/{cardNumber}` (§7.2), which
exists precisely so that the authorization context does not read the account database.

`batch-account-extract` is the one deliberate exception, and it is not a service: it is a scheduled
workload that shares `account-domain` with the account service and reads the same database directly,
exactly as the JCL step read the same VSAM cluster the online transactions used. Routing it through
the online API would replace one sequential read of the whole file with one HTTP call per account and
change the job's failure semantics, so the module boundary is kept and the deployment coupling is
documented here.

## 3. Backend layering (all services)

| Layer | Package | Contents | Source counterpart |
|---|---|---|---|
| API | `com.carddemo.api` | account: `AccountViewController`, `AccountUpdateController`, `MenuController`, `SignedOnUser`; identity: `SignOnController`, `UserAdminController`, `AdminMenuController`; both: `ApiExceptionHandler`, DTOs | BMS send/receive, `EXEC CICS RETURN TRANSID`, COMMAREA |
| Application services | `com.carddemo.service` | account: `AccountViewService`, `AccountUpdateService`, `AccountReadService`, `AccountUpdateValidator`, `AccountChangeDetector`, `AccountScreenMapper`, `MenuAccessService`; identity: `SignOnService`, `UserListService`, `UserAdminService`, `AdminMenuService` | `0000-MAIN`, `2000-*`, `1200-*`, `9000-*`, `9600-*`, `9700-*`, `PROCESS-*`, `READ/WRITE/UPDATE-USER-SEC-FILE` |
| Domain | `com.carddemo.domain` (in `common`, plus `UserMessages` in identity) | `ScreenMessages`, `UserMessages`, `reference.LookupTables`, `validation.FieldEditor`/`DateEditor`/`DateValidationService`/`ScreenField`/`FieldFlag` | `CSMSG01Y`, the message literals of the identity programs, `CSLKPCDY`, `1210`-`1280`, `CSUTLDPY`, `CSUTLDTC`, `CSSETATY` |
| COBOL semantics | `com.carddemo.cobol` (in `common`) | `CobolText`, `CobolNumeric`, `ZonedDecimalCodec`, `CobolRecordBuilder`, `CobdatftDateFormatter` | fixed-width moves, `PIC` edits, `COMP-3`, `COBDATFT` |
| Persistence | `com.carddemo.persistence` | account (`account-domain`): `AccountEntity`, `CustomerEntity`, `CardXrefEntity` and three repositories; identity: `SecurityUserEntity`, `SecurityUserRepository` | `ACCTDAT`, `CUSTDAT`, `CARDXREF`/`CXACAIX`, `USRSEC` |
| Security | `com.carddemo.config` | identity: `SecurityConfig`, `TokenIssuer`, `JwtProperties`; account and gateway: `SecurityConfig`/`GatewaySecurityConfig` | the CICS sign-on and the transactions it made reachable |
| Batch | `com.carddemo.batch` | `ReadAcctJobConfig`, three record aggregators, `AcctExtractProperties` | `READACCT.jcl` + `CBACT01C` |
| Exceptions | `com.carddemo.exception` | `ScreenValidationException`, `RecordNotFoundException`, `StorageAccessException`, `UpdateFailedException`, `AdminOnlyOptionException`, `BusinessRuleException`, `SignOnFailedException`, `DuplicateUserException` | `WS-*-MSG` paths, `DFHRESP` branches, `ABEND-ROUTINE` |

Dependencies point inward: `api → service → domain → cobol`, and `service → persistence`. Nothing in
`domain` or `cobol` depends on Spring, which is why the field edits and record layouts are testable
without a context.

## 4. Significant source-to-target decisions

**CICS transaction → REST operation.** Each business action a terminal operator could take becomes
one operation: sign on, display prompt, view an account, fetch for update, validate, confirm, list
users, add, update, delete. The conversation state that COMMAREA carried between pseudo-conversational
turns becomes explicit request/response state — `AccountUpdateRequest` carries the values as fetched
plus the values keyed, `AccountUpdateResponse.status` names the state the source held in `ACUP-*`
flags, and the user list carries the first and last user id of the page shown where the source carried
`CDEMO-CU00-USRID-FIRST`/`-LAST`. Terminal transport (AID codes, `CSSTRPFY`, `XCTL`, map names) is
dropped as platform plumbing; the *business* meaning of each function key is preserved as a distinct
operation or UI action.

**CICS sign-on → realm sign-on with a second factor.** `COSGN00C` verified the credentials, put the
user id and the operator type in the COMMAREA, and transferred to the menu the operator type selected.
The target keeps the last two steps and moves the first out of the application: the Keycloak realm
`carddemo` asks for the password and for a TOTP code and signs an RS256 token, the SPA obtains it with
Authorization Code + PKCE, and `GET /api/me` reports the USRSEC row and the `XCTL` target as
`nextScreen`. The COMMAREA becomes that token: `preferred_username` is `CDEMO-USER-ID` and the realm
role `ADMIN`/`USER` is `CDEMO-USER-TYPE`. Tokens are held in `sessionStorage`, and signing off ends the
realm's session as well. `POST /api/signon`, with the source's own clear-text comparison, remains only
under `carddemo.security.mode=legacy`, which exists for the parity evidence and the rollback path.
`25-authentication-and-mfa-architecture.md` is the design; 26–30 cover the realm, the flow, the
controls, the operations and the user guidance.

**Operator type → authority, not a request parameter.** The admin-only rule of `COMEN01C` and the
administrator entry rule of `COADM01C` are preserved, but the operator type is now taken from the
verified token (`SignedOnUser.userType`, `hasAuthority("ROLE_ADMIN")`) instead of from anything the
caller supplies. `MenuAccessService` still holds the option table and still refuses an admin-only
option, exactly where the source holds that rule. Whether a signed-on operator may reach the user
screens at all is enforced in the identity service's `SecurityConfig`, because in the source those
transactions were only reachable from the admin menu.

**Screen state → response envelope.** `1200-SETUP-SCREEN-VARS`/`3300-SETUP-SCREEN-ATTRS` decided both
the values and the highlighting. The target returns the values, the single message the source would
have displayed, and the set of failing fields — so the React screens can reproduce highlighting
without reproducing 3270 attribute bytes.

**Read-for-update → pessimistic lock in one transaction.** `READ … UPDATE` on ACCTDAT and CUSTDAT
becomes `SELECT … FOR UPDATE` through `lockByAcctId`/`lockByCustId` inside a single `@Transactional`
method, and `SYNCPOINT ROLLBACK` becomes the transaction rollback. The optimistic "changed by someone
else" comparison is kept as an explicit comparison against the fetched baseline (BR-47), because it is
a business rule, not a locking artefact. Guarantee changes are itemised in
`12-consistency-model-delta-register.md`.

**Keyed VSAM browse → keyed query.** `STARTBR`/`READNEXT`/`READPREV` over USRSEC becomes repository
queries keyed on the user id of the page boundary, ten rows at a time with a one-row lookahead, so
paging depends on the key rather than on an offset — the property the source's browse had, and the
reason a user added or deleted between two pages cannot shift a page the way an offset would.

**JCL job step → Spring Batch step.** `READACCT` becomes job `readAcctJob` with one chunk-oriented
step: a paged JPA reader ordered by account id (equivalent to the sequential KSDS read), and a
composite writer fanning out to the three extract files with their exact record layouts. It is
deployed as a Kubernetes CronJob (`deploy/helm`), which is where the absent scheduler definition
(UCR-12) would otherwise have to be guessed.

**CICS transaction routing → gateway routes.** The terminal reached a transaction by identifier; the
browser reaches one address (`:8090`) and the gateway routes `/api/me`, `/api/users/**` and
`/api/admin-menu/**` to the identity service and `/api/accounts/**` and `/api/menu` to the account
service. The gateway verifies the token at the edge, so a call the CICS sign-on would have refused
never reaches a service, and wraps each route in a circuit breaker — the resilience the source got
for free from the transaction manager. Each service still enforces its own rules, because the gateway
is not the only way in.

## 5. Configuration and diagnostics

Each service binds its own datasource (`CARDDEMO_ACCOUNT_JDBC_URL`, `CARDDEMO_IDENTITY_JDBC_URL`,
`CARDDEMO_AUTHORIZATION_JDBC_URL`) and
runs its own Flyway migrations on startup with Hibernate set to `validate`, so schema drift fails fast
instead of silently altering a database. No service holds a signing key: each binds the realm's issuer
(`KEYCLOAK_ISSUER_URI`, the address in `iss`) and the address it can reach the realm on
(`KEYCLOAK_URL`, used for JWKS and the token endpoint), and verifies RS256 signatures against the
fetched keys. The two confidential clients bind their own secret (`KEYCLOAK_CLIENT_ID`,
`KEYCLOAK_CLIENT_SECRET`). `CARDDEMO_JWT_SECRET` is needed only by `carddemo.security.mode=legacy` and
is set by neither the Compose file nor the chart. The authorization
service additionally binds `CARDDEMO_KAFKA_BOOTSTRAP` and `CARDDEMO_ACCOUNT_SERVICE_URL`, and its
topic names, partition count, retry attempts and backoff are configuration
(`carddemo.authorization.kafka.*`). Batch job launching
is off by default and enabled per run by command line. Logging is Spring Boot's default pattern with
the service layer at `INFO`; every rejected input logs the rule that rejected it, which is what makes
the API smoke evidence readable.

## 6. Messaging — account and identity contexts

**NOT APPLICABLE.** No MQ, IMS, queue or asynchronous interface exists anywhere in the account and
identity dependency closure (`03-discovery-and-dependency-map.md` §7), so no messaging technology is
introduced. The account and identity services communicate through no runtime call at all — only
through the token. The account service does answer one synchronous read from the authorization
context (§7.2), and the authorization context is where Kafka enters the build (§7.5).

## 7. The authorization bounded context

### 7.1 Why a third context, and why it owns its own database

`COPAUA0C`, `COPAUS0C`, `COPAUS1C`, `COPAUS2C` and `CBPAUP0C` share one data closure — the IMS HIDAM
hierarchy `DBPAUTP0` and the DB2 table `AUTHFRDS` — that no other in-scope program touches, and they
are driven by a different trigger (an acquirer message) than the account screens. That is a
capability boundary, not a program boundary, so the five programs become one service owning one
database, `carddemo_authorization`, with its own Flyway history. The playbook's per-service ownership
rule (§8.2.13) then forbids reading `carddemo` or `carddemo_identity` directly, which is the
constraint that shapes everything below.

| Module | Contents | Runtime |
|---|---|---|
| `authorization-domain` | the `CIPAUSMY`/`CIPAUDTY`/`AUTHFRDS` layouts as entities plus their repositories, `AuthorizationDecisionEngine`, `AuthorizationKey`, `DeclineReason`, `FraudStatus`, `MatchStatus`, `AuthorizationMessages`, and the `CCPAURQY`/`CCPAURLY` message records | library, shared by the two runtimes below |
| `authorization-service` | `AuthorizationRequestListener`/`Processor`/`ReplyPublisher`, `AuthorizationInquiryService`, `FraudMarkingService`, the REST layer, the Kafka configuration, the Flyway migrations | port 8082 |
| `batch-auth-purge` | the converted `CBPAUP0J` job | job, launched per run |

`authorization-domain` is shared deliberately and narrowly: both runtimes are inside the same bounded
context and both read the same two tables, so a shared entity module is cohesion, not the
global legacy-model library §8.3 warns against. Nothing in it is visible to the account or identity
contexts.

### 7.2 Cardholder data across the boundary

`COPAUA0C` read `CARDXREF`, `ACCTDAT` and `CUSTDAT` directly — files the account context now owns.
The target adds one operation to the account service, `GET /api/cardholders/{cardNumber}`
(`CardholderLookupController` → `CardholderLookupService`), which performs exactly the source's
xref → account → customer closure and returns the fields the authorization decision needs.
`CardholderLookupClient` calls it with a bearer token; `ServiceTokenIssuer` forwards the signed-on
user's token on the screen path and mints a short-lived service token on the Kafka path, where there
is no user. A lookup failure is a `CardholderLookupException`, which is retryable — it must not be
mistaken for "no such card", which declines. That distinction is the whole reason the client
returns an empty optional for a 404 and throws for anything else.

### 7.3 Decision logic as a pure function

`AuthorizationDecisionEngine.decide` takes the request, the optional summary and the optional
cardholder view and returns an `AuthorizationDecision`. It performs no I/O, which is what lets the
source's decision table be tested exhaustively and keeps the transaction in `AuthorizationRequestProcessor`
to persistence only. `DeclineReasonFlag` is the source's 88-level set; `DeclineReason` is the source's
code table, including the entries no decision path reaches (UCR-18) and the `9999-ERROR` fallback of
`COPAUS1C`.

### 7.4 Request processing and its transaction

`AuthorizationRequestProcessor.process` is one `@Transactional` unit: duplicate check against
`authorization_request_log`, pessimistic read of the summary (`findByIdForUpdate`), decision, summary
update, detail insert, request-log insert, reply row into `authorization_reply_outbox`. The source's
two-phase commit across IMS, DB2 and MQ becomes one local transaction (CMD-10), the reply moves from
before the write to after the commit (CMD-11), at-most-once MQ delivery becomes at-least-once Kafka
delivery made idempotent by the request log (CMD-12), and the source's global queue ordering becomes
per-card partition ordering plus the account-level lock (CMD-14).

### 7.5 Messaging

Kafka replaces MQ for this context only. Topic and partition design, header mapping, retry, dead
lettering, duplicate detection and the outbox are covered in `23-messaging-modernization-mapping.md`;
`§6` of this document remains correct for the account and identity contexts, which have no messaging.

### 7.6 Batch

`batch-auth-purge` mirrors `batch-account-extract`: a jar with the job disabled by default, launched
per run. The IMS `GN` root walk becomes a keyset reader whose position is saved in the step execution
context, so a restart resumes after the last committed root instead of at the top of the database
(CMD-15).
