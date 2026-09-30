# Deployment and Rollback Checklist

Playbook §8.10 and §8.12 item 8. Applies to the converted services: `account-service` (8080),
`identity-service` (8081), `authorization-service` (8082), `gateway` (8090), the `batch-account-extract`
and `batch-auth-purge` jobs, and the React frontend. Deployment assets:
`deploy/helm/carddemo` (Kubernetes) and `docker-compose.yml` (local only).

## 1. Pre-deployment gates

| # | Check | Evidence |
|---|---|---|
| 1.1 | All CI gates green on the release commit | `19-test-strategy-and-quality-gates.md` §3; PR checks |
| 1.2 | Images built and pushed for every changed service, tagged with the commit sha | registry listing (not exercised on the Devin VM — UCR-09) |
| 1.3 | Helm values reviewed per environment: image tags, replica counts, resource requests/limits, database URLs, Kafka bootstrap, ingress host | `deploy/helm/carddemo/values.yaml` |
| 1.4 | Secrets present in the target namespace and *not* taken from chart defaults: the two Keycloak client secrets (`carddemo-identity-admin`, `carddemo-authorization-svc`), three database passwords, Kafka credentials. No JWT signing secret is deployed — tokens are RS256, signed by the realm and verified through JWKS | production uses an external secret store; `29-keycloak-deployment-and-operations.md` §3 |
| 1.4a | Keycloak reachable, the `carddemo` realm imported, its OTP and brute-force policies as in `26-keycloak-realm-configuration.md`, the browser client's redirect URIs and web origins set to the environment's own host, and TLS terminated in front of it | `‹issuer›/.well-known/openid-configuration`, realm export diff |
| 1.4b | The identity service's service account can actually provision: a `client_credentials` token for `carddemo-identity-admin` carries `resource_access.realm-management` with `manage-users`, and `GET ‹issuer-host›/admin/realms/carddemo/users?max=1` with it answers `200`. A granted role that is outside the client's scope is not in the token, and the realm then answers `403` to every provisioning call while every service still looks healthy | token claims, admin API response |
| 1.5 | Databases `carddemo`, `carddemo_identity`, `carddemo_authorization` exist, each with its own owner role, and no role can reach another service's schema | `psql \du`, `\l` |
| 1.6 | Flyway dry run: `flyway info` against each target database shows only the expected pending versions and no checksum mismatch | migration log |
| 1.7 | Kafka topics exist with the intended partition count and replication factor, or topic auto-creation is deliberately enabled | `23-messaging-modernization-mapping.md` §4 |
| 1.8 | Authorization data reconciliation passed for the data being cut over | `tools/reconcile_authorization_data.py` output in `docs/evidence/` |
| 1.9 | Rollback artifacts identified: previous image tags, database backup/restore point, previous Helm revision number | `helm history carddemo` |
| 1.10 | On-call owner, change window and communication channel agreed | change record |

## 2. Deployment order

Order matters because of the schema and the token issuer. `KEYCLOAK_ISSUER_URI` is the issuer string
the tokens carry and every service validates; `KEYCLOAK_URL` is where the services reach the realm for
JWKS and for the client-credentials token. They differ whenever the browser and the services see
Keycloak at different addresses.

1. **Databases** — apply migrations first, by starting each service with `spring.flyway.enabled=true`
   in a single replica, or by running Flyway as a pre-install/pre-upgrade job. Migrations in this
   release are additive (new tables in a new database), so they are backward compatible with the
   currently running version.
2. **Keycloak** — the realm has to serve its signing keys before any service validates a token and
   before the identity service can provision a user. Both realm-role names (`ADMIN`, `USER`) and the
   service client have to exist at this point.
3. **identity-service** — it provisions realm users and serves `GET /api/me`, which every screen calls
   first; run its reconciliation once after deployment so any USRSEC row without a realm account is
   linked.
4. **account-service** — it serves the cardholder lookup the authorization service depends on.
5. **authorization-service** — starts consuming `carddemo.authorization.request.v1`.
6. **gateway** — publishes the new routes; until this step the new screens return 404 rather than
   an error, which is the intended order of exposure.
7. **frontend** — deploy the new bundle after the gateway routes are live, with its
   `VITE_KEYCLOAK_*` values pointing at the realm and its own redirect URI.
8. **batch jobs** — install/resume the `auth-purge` CronJob last, after the authorization schema is
   populated and reconciled.

## 3. Per-service verification after deployment

| # | Check | How |
|---|---|---|
| 3.1 | Pod ready, no restart loop | `kubectl get pods -l app.kubernetes.io/instance=carddemo` |
| 3.2 | `/actuator/health` UP, including the database component | `kubectl exec` / probe status |
| 3.3 | Flyway applied the expected version and nothing else | service log line, `flyway_schema_history` |
| 3.4 | Sign-on asks for the password and then a one-time code, and the menu reflects the USRSEC user type | the frontend URL, one test user per role, an authenticator app |
| 3.4a | A user without an authenticator is sent to enrolment and reaches no screen until it has one | a freshly provisioned test user |
| 3.4b | A token with the wrong audience, a stale token and a token without MFA evidence are all refused with 401 | `curl` against the gateway with a crafted token; `28-authentication-security-controls.md` |
| 3.5 | Account view/update behave as before the release (regression) | the account screens |
| 3.6 | Authorization summary screen shows the five-row page and the six metrics for a seeded account | `/api/authorizations?accountId=…` |
| 3.7 | Authorization detail and fraud toggle round-trip | detail screen |
| 3.8 | A synthetic authorization request produces a reply on the reply topic with the same correlation id | `kafka-console-producer`/`consumer` with a `CCPAURQY` payload |
| 3.9 | Consumer group lag returns to zero after the synthetic request | `kafka-consumer-groups --describe` |
| 3.10 | Outbox table has no rows older than a minute | `SELECT count(*) FROM authorization_reply_outbox WHERE published_at IS NULL` |
| 3.11 | DLT has no new records | topic end offsets |
| 3.12 | Purge job runs in dry-run configuration and reports totals read/deleted | job log |
| 3.13 | Metrics scraped and dashboards populated | Prometheus targets |

## 4. Progressive delivery

The authorization service is the only new consumer of the request topic, so exposure is controlled by
*messages*, not only by replicas:

- **Canary (HTTP):** gateway routes the new authorization paths to one replica first; the account and
  identity paths are untouched, so a canary failure cannot affect the existing capability.
- **Canary (messaging):** keep the source region as the authoritative processor and mirror requests
  to Kafka in read-only comparison mode — the target processes and replies to a comparison topic,
  and replies are diffed rather than returned to the acquirer. Promote by switching the acquirer's
  reply destination.
- **Blue/green:** two Helm releases sharing one database are *not* safe for the authorization
  consumer, because both would consume the same group and both would write. Blue/green is therefore
  applied to the HTTP services and the frontend only; the consumer is upgraded in place with a
  rolling restart (per-card ordering is preserved because a partition is owned by one consumer at a
  time).

## 5. Rollback

| Situation | Action | Data consequence |
|---|---|---|
| Service fails readiness or a P1 defect appears | `helm rollback carddemo <previous-revision>` | none: this release's migrations are additive, so the previous image runs against the new schema |
| Bad authorization decisions being produced | scale `authorization-service` to zero replicas, then roll back | requests accumulate on the request topic and are processed after recovery — retention must exceed the outage; replies are not lost |
| Migration failed part-way | Flyway rolls back the failed migration's transaction; fix forward with a new version rather than editing the failed one | the failed version is recorded as failed in `flyway_schema_history` and must be repaired (`flyway repair`) before the next attempt |
| Authorization seed data wrong | drop and recreate `carddemo_authorization`, re-run migrations, re-run reconciliation | deterministic reload; the only service that owns this data is the one being rolled back |
| Reply topic polluted with wrong replies | stop the relay, correct the authorizations, re-publish from the outbox (rows are retained) | consumers must tolerate a duplicate reply with the same correlation id — the source's own contract |
| Fraud markings made in error | fraud rows are append-only; reverse by the documented reversal path rather than by deleting rows | audit trail preserved |
| Gateway route defect only | roll back the gateway release alone | none |
| Frontend defect only | redeploy the previous bundle | none |
| Keycloak unavailable or the realm unusable, and screens must be reachable | set `CARDDEMO_SECURITY_MODE=legacy` on every service and deploy the pre-Keycloak frontend bundle, then withdraw it as soon as the realm is back | severe: legacy mode is one factor, compares the clear-text USRSEC password and needs a shared HS256 secret, so the realm's password changes and enrolments are bypassed and the USRSEC password is authoritative again (`28-authentication-security-controls.md`); it is a break-glass path, not a supported mode |
| The realm's signing keys rotated and tokens are refused | let the services re-fetch JWKS (they do so on an unknown `kid`); if a service was pinned to a stale key set, restart it | none; users already signed on may need to sign on again |

Rollback rehearsal is part of the cutover dress rehearsal (`21-cutover-runbook.md` §3) and must be
timed, not assumed.

## 6. Sign-off

| Role | Confirms |
|---|---|
| Release engineer | gates green, images tagged, Helm diff reviewed |
| DBA | migrations applied, backups and restore point verified |
| Platform/SRE | probes, dashboards, alerts, topic configuration |
| Business owner | screen and authorization spot checks, including the remediated defects (UCR-27, UCR-30) |
| Security | RS256 tokens verified through JWKS with issuer, audience, expiry and MFA evidence; realm client secrets held in the secret store; no HS256 signing secret deployed; the enforced OTP and brute-force policies; audit logging |
