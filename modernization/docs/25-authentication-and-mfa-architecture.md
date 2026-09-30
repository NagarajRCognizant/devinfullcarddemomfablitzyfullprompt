# Authentication and MFA Architecture

Scope: how a person or a service proves who it is, after the sign-on of `COSGN00C` was moved out of
the application and into a Keycloak realm. The screens, the field edits, the browse order and the
routing rules are unchanged; what changed is who asks for the password, who signs the token and what
a service does with it.

## 1. What the previous release did, and why it changed

| Previous release | Weakness | This release |
|---|---|---|
| `POST /api/signon` read USRSEC and compared `SEC-USR-PWD` as clear text, as `READ-USER-SEC-FILE` does | the clear-text comparison is on the normal sign-on path | the realm holds the credential; `SEC-USR-PWD` is read only by the retained legacy mode |
| identity-service signed an HS256 token with `CARDDEMO_JWT_SECRET` | the signing key is a shared symmetric secret, so every validating service can also mint tokens | the realm signs RS256; services hold no key and fetch the public keys from its JWKS |
| every service was configured with that same secret | one leaked value forges any identity | no service holds a signing key; the secrets that remain are two confidential client secrets |
| one factor: a password | a stolen password is a complete authentication | password **and** a TOTP code, enforced by the realm's browser flow |
| the SPA posted the password to the application | the password passes through application code and logs | Authorization Code + PKCE: the password is only ever keyed on the realm's page |
| service-to-service calls used a token the caller minted for itself | a service can name itself anything | OAuth2 `client_credentials` against the realm |

The clear-text column is **not** removed. `SEC-USR-PWD` is still the shipped USRSEC data and the
`legacy` mode still compares it, so the parity evidence for `COSGN00C` remains reproducible; see §5
and UCR-14. The claim this release supports is narrower and exact: in the default mode no clear-text
password comparison happens and no service holds a signing key.

## 2. Participants

| Participant | Realm client | Kind | What it may do |
|---|---|---|---|
| React SPA | `carddemo-ui` | public | Authorization Code + PKCE (S256) only; implicit flow and direct access grants are disabled, so it cannot ask for a password itself and cannot be given a secret it could not keep |
| identity-service | `carddemo-identity-admin` | confidential, service account | provision, update, delete and role-assign realm users through the Admin REST API |
| authorization-service | `carddemo-authorization-svc` | confidential, service account | obtain its own token with `client_credentials` for the cardholder lookup it makes while consuming Kafka records |
| account-service, identity-service, authorization-service, gateway | — | resource servers | verify tokens; they are never a client of the realm for the purpose of verification |

## 3. Token verification, the same in every service

`carddemo-security` (`CardDemoJwtDecoders`, `TokenProperties`) is the one place a token is accepted,
so every service applies the same rules:

| Check | Implementation | Rejects |
|---|---|---|
| signature | `NimbusJwtDecoder`, RS256, keys from the realm's JWKS | `alg: none`, an HS256 token forged with any shared secret, a token signed by another key, any tampering |
| issuer | `JwtIssuerValidator` on `carddemo.security.token.issuer-uri` | a token from another realm or another Keycloak |
| audience | `AudienceValidator` on the per-service audience | a token minted for a different service of the same realm |
| expiry | `JwtTimestampValidator`, 60 s skew (`CARDDEMO_TOKEN_CLOCK_SKEW_SECONDS`) | expired and not-yet-valid tokens |
| second factor | `SecondFactorValidator` | a token that carries no evidence that a second factor was used |
| authority | `RealmRoleAuthorities` maps `realm_access.roles` to `ROLE_ADMIN` / `ROLE_USER` | a caller that claims a role the realm did not grant, because the roles are read from the signed token only |

The issuer and the JWKS address are configured separately (`KEYCLOAK_ISSUER_URI` and `KEYCLOAK_URL`).
A browser reaches Keycloak on a published address and the tokens say so; a container reaches it on the
service address. Validation of `iss` stays against what the browser used while the keys are fetched
where the service can reach them.

### 3.1 What counts as second-factor evidence

`SecondFactorValidator` accepts a token when any of the following holds:

1. its `amr` claim contains one of `carddemo.security.token.amr-values` (`otp`, `mfa` by default), or
2. its `acr` claim matches a configured value, when the deployment defines an ACR level, or
3. its `azp` is a configured browser client and the realm's browser flow requires OTP for everyone —
   the flow is the enforcement point and the claim is the evidence of it, or
4. its `azp` is a configured service client, for which a second factor is meaningless: a
   `client_credentials` caller has no person and no authenticator, and its assurance comes from the
   client secret instead.

Case 3 exists because a realm can be configured to issue tokens without `amr`; the realm shipped here
requires OTP in the browser flow, so a token from `carddemo-ui` cannot have been issued without one.
Case 4 is what keeps the Kafka-driven authorization flow working without weakening the browser path:
the two are separated by client id, not by a flag.

## 4. Identity carried into the business logic

`COSGN00C` left `CDEMO-USER-ID` and `CDEMO-USER-TYPE` in the COMMAREA and every later screen read
them. The token carries the same two values:

| COMMAREA field | Token | Mapping |
|---|---|---|
| `CDEMO-USER-ID` | `preferred_username` | `SignedOnUser.userId()` uppercases it into the eight-byte USRSEC key; the subject, a realm UUID, is never used as a user id |
| `CDEMO-USER-TYPE` | `SEC-USR-TYPE` of the USRSEC row, which the realm role `ADMIN` / `USER` has to agree with | `'A'` for an administrator, `'U'` otherwise, which is what `COSGN00C` routed on |

`GET /api/me` reports the USRSEC row for the user the token names, plus the `nextScreen` decision of
`PROCESS-ENTER-KEY`; the SPA calls it after the callback instead of reading a sign-on response.

The realm authenticates, the record authorises. The role is written from `SEC-USR-TYPE` when the user
is provisioned, so the two normally agree; where they do not, the row wins. `/api/me` refuses a token
whose role disagrees with the row it names, and `AdministratorOfRecord` reads `SEC-USR-TYPE` on the
user administration endpoints, so an `ADMIN` role granted in the realm alone cannot promote a type
`'U'` user. The account service has no USRSEC to read and authorises on the role
(`28-authentication-security-controls.md` §2).

## 5. The two modes

`carddemo.security.mode` (`CARDDEMO_SECURITY_MODE`), default `keycloak`:

| | `keycloak` (default) | `legacy` |
|---|---|---|
| who asks for the password | the realm's login page | the SPA, posting to `POST /api/signon` |
| second factor | required | none |
| token | RS256, issued by the realm | HS256, issued by identity-service with `CARDDEMO_JWT_SECRET` |
| `POST /api/signon` | not mapped | mapped, unauthenticated |
| clear-text `SEC-USR-PWD` comparison | never | on every sign-on |
| purpose | the release | reproducing the `COSGN00C` parity evidence, and a rollback that does not need the realm |

`legacy` is a documented, deliberately retained weakness, not a supported deployment: it keeps the
inherited USRSEC behaviour reachable for evidence and for the rollback step of
`20-deployment-and-rollback-checklist.md`.

## 6. Sequences

Browser sign-on (`carddemo.security.mode=keycloak`):

```
SPA                     Keycloak (realm carddemo)              Services
 |  GET /  (no session)
 |--- authorization request, PKCE S256 challenge ------------->|
 |                        password page  ---------------------->
 |                        OTP page (or CONFIGURE_TOTP first) -->
 |<-- redirect to /callback?code=… ----------------------------|
 |--- POST /token, code + verifier --------------------------->|
 |<-- access + id + refresh token -----------------------------|
 |  GET /api/me, Authorization: Bearer …  -------------------------------->|
 |                                                     JWKS (cached) <----|
 |<-- userId, userType, nextScreen ---------------------------------------|
```

Service-to-service (authorization-service, per Kafka record):

```
authorization-service --- client_credentials (client id + secret) ---> Keycloak
                      <-- access token, azp=carddemo-authorization-svc ---
                      --- GET /api/accounts/…  Bearer … ---> account-service
```

`ServiceAccessTokens` caches the token until shortly before it expires, so a burst of records does not
mean a token request per record.

## 7. Provisioning

The user administration screens (`COUSR01C`, `COUSR02C`, `COUSR03C`) still own USRSEC, and the realm
now needs the same user to exist:

1. `UserAdminService` writes the USRSEC row in a local transaction.
2. `KeycloakUserDirectory` creates the realm user, assigns `ADMIN` or `USER` from `SEC-USR-TYPE`, and
   sets the `CONFIGURE_TOTP` required action, so a new user enrols an authenticator at first sign-on.
3. The realm id is stored on the USRSEC row (`keycloak_user_id`, nullable and unique).
4. A failure inside step 2 withdraws whatever it created: a role that cannot be granted removes the
   realm user again, so a retry is not met by `409 User exists`. A failure after it removes the realm
   user too; a failure before it leaves the row unlinked.
5. `UserReconciliation` links or creates the realm user for the unlinked rows, which is also how the
   ten shipped USRSEC records reached the realm.

The two stores cannot be written in one transaction, so the design is compensation plus
reconciliation rather than an assumed atomicity; this is registered with the other consistency-model
deltas in `12-consistency-model-delta-register.md`.

## 8. Related documents

| Document | Contents |
|---|---|
| `26-keycloak-realm-configuration.md` | the realm as code: clients, roles, flow, OTP policy, brute force, mappers, bootstrap |
| `27-mfa-enrolment-and-signon-flow.md` | enrolment, sign-on, refusal, lockout and logout, step by step |
| `28-authentication-security-controls.md` | the threats the design answers and the evidence for each |
| `29-keycloak-deployment-and-operations.md` | Compose, Helm, secrets, rotation, key rollover, operations |
| `30-signing-on-with-mfa-user-guide.md` | what a user and an administrator do |
