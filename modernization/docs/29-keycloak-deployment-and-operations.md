# Keycloak Deployment and Operations

How the realm is deployed with the application, what has to be supplied per environment, and the
operations the realm adds. This extends `20-deployment-and-rollback-checklist.md` rather than replacing
it.

## 1. Local: the Compose stack

| Service | Image | Address | Notes |
|---|---|---|---|
| `keycloak-db` | `postgres:16-alpine` | in-network | Keycloak's own database; the three business databases stay separate |
| `keycloak` | `quay.io/keycloak/keycloak:26.0` | `127.0.0.1:8088` | `start-dev --import-realm`, `KC_HOSTNAME=http://127.0.0.1:8088`, health endpoints enabled; readiness is a `/dev/tcp` connect to the management port because the image has neither `curl` nor `wget` |
| `keycloak-bootstrap` | same image | — | runs `keycloak/bootstrap.sh` once, for `kcadm.sh`; every downstream service waits on `service_completed_successfully` |
| the services | built from the multi-stage Dockerfiles | loopback ports | `KEYCLOAK_URL=http://keycloak:8080`, `KEYCLOAK_ISSUER_URI=http://127.0.0.1:8088/realms/carddemo` |

```bash
cp .env.example .env      # then fill every value in
docker compose up -d --build
docker compose ps         # keycloak, keycloak-bootstrap (exited 0), the services healthy
```

The two Keycloak addresses are not interchangeable and this is the most common misconfiguration: the
browser reaches `127.0.0.1:8088`, so the tokens say so and `iss` must be validated against it, while a
container reaching `127.0.0.1:8088` reaches itself. `KEYCLOAK_ISSUER_URI` is the string in the claim;
`KEYCLOAK_URL` is where JWKS and the token endpoint are fetched.

`start-dev` is a development server: HTTP, no hostname strictness, and an in-image bootstrap
administrator. A real deployment uses `start` with TLS and a production database.

## 2. Kubernetes: the chart

`deploy/helm/carddemo` no longer contains a signing key at all — `templates/jwt-secret.yaml` is gone,
and `CARDDEMO_JWT_SECRET` is not set for any workload. What it needs instead:

| Value | Meaning |
|---|---|
| `keycloak.url` | in-cluster address of Keycloak, for JWKS and the token endpoint |
| `keycloak.issuerUri` | the externally visible issuer, as it appears in `iss`; must match what the browser is redirected to |
| `keycloak.realm` | realm name, `carddemo` |
| `keycloak.clockSkewSeconds`, `keycloak.requireSecondFactor` | validation settings; `requireSecondFactor` stays true |
| `identityService.keycloakClient.existingSecret` / `secretKey` | the `carddemo-identity-admin` secret, created outside the chart |
| `authorizationService.keycloakClient.existingSecret` / `secretKey` | the `carddemo-authorization-svc` secret, likewise |

The chart does not deploy Keycloak: the realm is shared infrastructure and its lifecycle is not the
application's. Install it with the Keycloak operator or the community chart, import
`keycloak/realm-carddemo.json`, then run `keycloak/bootstrap.sh` as a job with the secrets mounted.

The SPA is configured at build time (`VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`,
`VITE_KEYCLOAK_CLIENT_ID`, `VITE_KEYCLOAK_REDIRECT_URI`,
`VITE_KEYCLOAK_POST_LOGOUT_REDIRECT_URI`), so a bundle is per-environment. None of them is a secret;
`carddemo-ui` is a public client and must never be given one.

## 3. Deployment order

The order in `20-deployment-and-rollback-checklist.md` §2 changes at its head:

1. **the realm** — import and bootstrap it first: a service that starts without a reachable realm
   accepts no token, and the SPA cannot sign anyone on. The decoders are lazy, so a service does start
   and reports healthy; it is the first request that fails.
2. **the realm users** — run `UserReconciliation` (or the provisioning path) so that every USRSEC row
   has a realm user; a user with a row and no realm account cannot sign on.
3. then the databases, identity-service, account-service, authorization-service, gateway, frontend and
   the batch jobs, as before. identity-service no longer has to precede the others for token reasons —
   it does not issue tokens — but it still owns the USRSEC migrations.

Additions to the pre-deployment gates:

| Check | Evidence |
|---|---|
| the realm answers `/.well-known/openid-configuration` and its `issuer` equals `keycloak.issuerUri` | `curl` output |
| the two client secrets exist in the namespace and are not chart defaults | `kubectl get secret` |
| `carddemo-ui` redirect URIs and web origins name this environment's host, exactly | realm export |
| the browser flow of the realm still has `auth-otp-form` REQUIRED | realm export; a realm re-imported from an older export could reintroduce the conditional sub-flow |
| no workload has `CARDDEMO_JWT_SECRET` set | rendered manifests |

## 4. Rollback

Two levels:

1. **Application rollback** with the realm in place: deploy the previous images. The previous release
   validates HS256 tokens only, so it needs `CARDDEMO_JWT_SECRET` again and it will not accept realm
   tokens — this is a full rollback of the authentication change, not a partial one.
2. **Authentication rollback without redeploying**: set `CARDDEMO_SECURITY_MODE=legacy` and supply
   `CARDDEMO_JWT_SECRET`. `POST /api/signon` is mapped again, the SPA's legacy sign-on path is used and
   the second factor is not asked for. This reinstates the clear-text comparison and the shared secret,
   so it is an incident measure with an owner and an end time, not a configuration option.

Either way the realm users can be left in place; they are additive and harmless to the old release.

## 5. TLS and cookies

Any address that is not loopback needs TLS end to end: the realm sets session cookies, the SPA holds
tokens, and the authorization code travels in a redirect. With TLS terminated at the ingress, Keycloak
must be told (`KC_PROXY_HEADERS=xforwarded`, `KC_HOSTNAME` set to the external URL) or it will issue
`iss` values and redirects on the wrong scheme, which fails issuer validation in every service.
`sslRequired` is `external` in the realm JSON, so it is already enforced for anything but loopback.

## 6. Operations the realm adds

| Task | How |
|---|---|
| a user has lost its authenticator | remove its OTP credential and re-add the `CONFIGURE_TOTP` required action; the next sign-on enrols again |
| a user is locked out and should not wait | clear the brute-force counter for that user in the admin console, or `kcadm.sh delete attack-detection/brute-force/users/<id>` |
| rotate a client secret | rotate it in the realm and in the secret store, then restart the owning service; the identity and authorization services read it at startup |
| the realm's signing keys roll over | nothing to do: the JWKS is fetched and cached by key id, and an unknown key id triggers a refetch |
| Keycloak is down | no one can sign on and no new service token is issued; tokens already issued stay valid until they expire (15 minutes) and cached service tokens keep the Kafka flow running for their remaining lifetime |
| a new environment | import the realm, run `bootstrap.sh`, set the redirect URIs and web origins for that host, build the SPA with its `VITE_KEYCLOAK_*` values |

## 7. What was verified here, and what was not

| Verified on this machine | Not verified |
|---|---|
| the full Compose stack starts healthy: Keycloak, bootstrap (exit 0), the three services and the gateway | the Helm chart applied to a cluster (`helm lint` only, UCR-09) |
| the realm's discovery document, sign-on with both factors, enrolment, refusals, lockout, logout and the ADMIN/USER journeys through the SPA (Playwright, `docs/evidence`) | a Keycloak deployment behind TLS and an ingress |
| unauthenticated calls answered 401 and a regular user's call to the administration API answered 403 | key rollover and secret rotation in a running environment |
