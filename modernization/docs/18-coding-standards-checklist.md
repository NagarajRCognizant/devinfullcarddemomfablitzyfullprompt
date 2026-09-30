# Coding Standards Checklist

The standards the converted code is held to, with the rule, where it is enforced, and where it is
visible in the delivered modules. A reviewer can use the middle column to check a claim rather than
taking it on trust. Playbook §8.2 and §8.12 item 6.

## 1. Structure and boundaries

| # | Rule | Enforced / evidenced by | Notes |
|---|---|---|---|
| 1.1 | One Maven module per deployable, plus `common` for cross-cutting COBOL semantics and one `*-domain` module per bounded context | `pom.xml` reactor: `common`, `account-domain`, `account-service`, `identity-service`, `batch-account-extract`, `authorization-domain`, `authorization-service`, `batch-auth-purge`, `gateway` | a service depends on its own domain module only |
| 1.2 | Layered packages: `api` (controllers, DTOs) → `service` → `persistence` (entities, repositories) → `domain` (rules, value objects) | package layout of every module | no reverse dependency; `domain` imports no Spring web type |
| 1.3 | No shared legacy-model library across contexts | `authorization-domain` shares nothing with `account-domain`; the authorization service reaches account data over HTTP only | playbook §8.3 item 9 |
| 1.4 | Each service owns its schema; no cross-database reads | `application.yml` of each service names exactly one datasource; `CardholderLookupClient` is the only path to account data | playbook §8.2 item 13 |
| 1.5 | Entities never leave the service; API responses are records | `api/dto/**` are Java records | |
| 1.6 | Mapping is explicit, hand-written, and one-directional per use | e.g. `AuthorizationInquiryService` builds `AuthorizationDetailResponse` | no reflective mapper, so a renamed field breaks the build |

## 2. Language and framework use

| # | Rule | Enforced / evidenced by |
|---|---|---|
| 2.1 | Java 21; records for immutable carriers, sealed/enum for closed sets, switch expressions for exhaustive decisions | `maven.compiler.release=21`; `DeclineReason`, `FraudStatus`, `MatchStatus` |
| 2.2 | Constructor injection only; no field injection, no `@Autowired` on fields | every service and controller |
| 2.3 | Immutability by default: fields `final` where the framework allows | services, domain types |
| 2.4 | No `Object`-typed or reflective field access; no `getattr`-style dynamic lookup | code review; the build has no reflection utility on the classpath |
| 2.5 | Money is `BigDecimal` with the declared scale; never `double`/`float` | entities, DTO `MoneyValue`, `ZonedDecimalCodec` |
| 2.6 | Imports at the top of the file, no wildcard imports | Checkstyle-free but uniformly applied; reviewable in the diff |
| 2.7 | No business logic in controllers — validation of the *screen* contract only | `AuthorizationController` validates the account id and selection (source screen edits) and delegates the rest |

## 3. Preserved COBOL semantics

| # | Rule | Enforced / evidenced by |
|---|---|---|
| 3.1 | Fixed-width text keeps its trailing blanks; comparisons are made on the trimmed value only where the source trims | `CHAR(n)` columns, `AuthorizationMessageLayoutTest` |
| 3.2 | Packed/zoned decimal conversion happens in one place per direction | `common` `ZonedDecimalCodec`/`CobolNumeric`; `tools/ims_authorization_unload.py` for the migration |
| 3.3 | Source messages are reproduced verbatim, including source defects | `AuthorizationMessages`, `LookupTables`, and UCR-16 |
| 3.4 | Source arithmetic order and rounding are preserved | `AuthorizationDecisionEngine`, `ExpiredAuthorizationProcessor` |
| 3.5 | A deliberate behaviour change is a documented delta, never a silent fix | UCR-27, UCR-30; each has a code comment pointing at the register entry |
| 3.6 | No business rule is left as a TODO, stub, mock or empty method | no `TODO`/`FIXME` in `modernization/**/src/main` |

## 4. Reliability and consistency

| # | Rule | Enforced / evidenced by |
|---|---|---|
| 4.1 | Transaction boundaries match the source unit of work, or the delta is registered | `@Transactional` on `AuthorizationRequestProcessor.process`; CMD-10 … CMD-15 |
| 4.2 | Read-modify-write on shared state uses explicit locking | `findByIdForUpdate`, JPA `@Version`; `AuthorizationConcurrencyIntegrationTest` |
| 4.3 | Retries are bounded, with backoff, and only on idempotent or de-duplicated work | `KafkaConfig.authorizationErrorHandler`; request log |
| 4.4 | State-changing message handling carries an idempotency key and duplicate detection | `authorization_request_log` |
| 4.5 | Cross-resource publication uses a transactional outbox, never a send inside a database transaction | `authorization_reply_outbox`, `AuthorizationReplyPublisher` |
| 4.6 | Batch jobs are restartable with explicit checkpointing | `PurgeAuthJobConfig` chunking, `AuthSummaryKeysetReader` |
| 4.7 | Remote calls have connect and read timeouts | `AccountServiceProperties` (2 s / 5 s) |

## 5. Error handling and validation

| # | Rule | Enforced / evidenced by |
|---|---|---|
| 5.1 | One exception taxonomy per service, translated at the edge | `ScreenValidationException` + `AuthorizationExceptionHandler` / `ApiExceptionHandler` |
| 5.2 | Error responses carry a stable code plus the source message text | `ApiError` |
| 5.3 | Input validation happens before any state change | controllers and `*RequestMessage.parse` |
| 5.4 | An unmapped lookup falls back to the source's own fallback value, not to null | `9999-ERROR` in the response-code table |
| 5.5 | No exception is swallowed; every caught exception is logged or rethrown | code review; contrast with UCR-28 |

## 6. Security

| # | Rule | Enforced / evidenced by |
|---|---|---|
| 6.1 | All non-actuator endpoints require a valid JWT | `SecurityConfig` of each service, gateway route filters |
| 6.2 | Role mapping from the legacy user type is centralised | `identity-service` token issuance, `MenuAccessService` |
| 6.3 | No secret is committed; every credential is an environment placeholder | `application.yml` `${…}` defaults are local-only and labelled |
| 6.4 | Service-to-service calls are authenticated | `ServiceTokenIssuer` |
| 6.5 | No password, token or card number in a log line | `logging` configuration and log statements reviewed |
| 6.6 | Dependencies are pinned, with no snapshot or floating range | `pom.xml`, `package-lock.json` |

## 7. Observability

| # | Rule | Enforced / evidenced by |
|---|---|---|
| 7.1 | Structured logging with the business key (account id, card number masked, correlation id) | listener and service log statements |
| 7.2 | Actuator health, info, metrics and Prometheus exposed; nothing else | `management.endpoints.web.exposure.include` |
| 7.3 | Batch job outcome is observable from the exit status and the job repository | `AuthPurgeBatchApplication` |

## 8. Tests

| # | Rule | Enforced / evidenced by |
|---|---|---|
| 8.1 | Every business rule has at least one test naming the behaviour, not the method | test method names throughout |
| 8.2 | JaCoCo ≥ 80% line and ≥ 80% branch on migrated business logic, build fails below | root `pom.xml` `check-business-logic-coverage`, `haltOnFailure=true` |
| 8.3 | Boilerplate (applications, config, DTOs, entities) is excluded from the coverage denominator, not from testing | JaCoCo `excludes` |
| 8.4 | Integration tests run against real PostgreSQL and a real Kafka broker | `AuthorizationPostgresIntegrationTest`, `@EmbeddedKafka` |
| 8.5 | A test is never weakened to make the build pass | history of the branch |

## 9. Frontend

| # | Rule | Enforced / evidenced by |
|---|---|---|
| 9.1 | TypeScript strict mode; no `any` | `tsconfig.json`, `npm run lint` |
| 9.2 | API types are declared once and shared | `src/api/types.ts` |
| 9.3 | Screen behaviour (paging, selection, messages) is tested at the screen level | `*Screen.test.tsx` |
| 9.4 | Accessible form semantics: label per field, error text associated with its input | screen components |
| 9.5 | No terminal interaction pattern is reproduced (no PF-key handlers, no COMMAREA-shaped state) | `06-screen-mapping.md` §retired constructs |

## 10. Build and delivery

| # | Rule | Enforced / evidenced by |
|---|---|---|
| 10.1 | `mvn -B clean verify` is the single backend gate | CI `backend` job |
| 10.2 | Frontend gate is lint + test + build | CI `frontend` job |
| 10.3 | OpenAPI 3.1 contracts pass Redocly lint | CI `contracts` job |
| 10.4 | Helm chart passes `lint` and `template` | CI `charts` job |
| 10.5 | Generated code is never hand-edited; generators are committed | `tools/*.py` produce the seed migration and the disposition report |
