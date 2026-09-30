# Authentication Security Controls and Evidence

The controls the authentication design puts in place, the attack each answers, and the test that shows
it. This document covers authentication only; the wider scope exclusions stay in
`17-limitations-and-review-required.md`.

## 1. Controls and the tests that hold them

| # | Attack | Control | Evidence |
|---|---|---|---|
| 1 | a stolen password is a complete authentication | the realm's browser flow requires the password **and** the OTP step; the OTP step is REQUIRED, not conditional | `27-mfa-enrolment-and-signon-flow.md` §1; `e2e/tests/signon.spec.ts` |
| 2 | a token forged with a shared secret | tokens are RS256, verified against the realm's JWKS; no service holds a signing key | `CardDemoJwtDecodersTest.anHs256TokenIsRefusedTheWayTheOldSelfIssuedOneNowIs` |
| 3 | an unsigned token | `alg: none` is refused before a claim is read | `CardDemoJwtDecodersTest.anAlgNoneTokenIsRefusedBeforeAnyClaimIsRead` |
| 4 | a token of another realm or another Keycloak | issuer validation against the configured issuer | `CardDemoJwtDecodersTest.aTokenOfAnotherIssuerIsRefused` |
| 5 | a token minted for one service replayed against another | audience validation per service | `AudienceValidatorTest.aTokenForAnotherServiceIsRefused`, `.aTokenWithNoAudienceIsRefused` |
| 6 | a tampered claim | signature verification covers every claim | `CardDemoJwtDecodersTest` |
| 7 | an expired token, or clock drift used to widen the window | timestamp validation with a 60 s skew | `CardDemoJwtDecodersTest.anExpiredTokenIsRefusedBeyondTheAllowedSkew`, `.aTokenThatExpiredWithinTheAllowedSkewIsStillAccepted` |
| 8 | a token issued without a second factor | `SecondFactorValidator`, from `amr`, `acr` or an authorized party that can only come from the MFA flow | `CardDemoJwtDecodersTest.aTokenWithoutTheSecondFactorIsRefused`, `SecondFactorValidatorTest` |
| 9 | a caller claiming a role it was not granted | roles are read from `realm_access.roles` of the verified token only | `RealmRoleAuthoritiesTest`; `e2e` `a regular user cannot reach the user administration` (403) |
| 10 | a regular user reaching the administration screens | `SEC-USR-TYPE` `'A'` of the USRSEC row required, and the realm role with it | `KeycloakModeApiIntegrationTest.aRegularUserCannotReachTheUserScreensOfTheAdminMenu`; `e2e` `a regular user cannot reach the user administration` |
| 10a | the `ADMIN` role granted in the realm to a user whose record is type `U`, at the realm's console or by a compromised service account | the record decides: `/api/me` refuses a token whose role disagrees with `SEC-USR-TYPE`, and `AdministratorOfRecord` asks the row rather than the role on the user screens | `KeycloakModeApiIntegrationTest.anAdminRoleTheRecordDoesNotHaveIsNotAnAuthority` |
| 11 | a code guessed or replayed | TOTP, 6 digits, 30 s, look-ahead 1, single use | `e2e` `wrong codes are refused …` |
| 12 | codes tried in bulk | brute-force detection, five failures, wait growing to fifteen minutes | same |
| 13 | a token intercepted in a redirect URL | Authorization Code with PKCE `S256`; the implicit flow is disabled, so no token is ever in a fragment | `keycloak/realm-carddemo.json`; `26-keycloak-realm-configuration.md` §4 |
| 14 | an authorization code intercepted and exchanged | PKCE `S256` is required for `carddemo-ui`, so the code is useless without the verifier | same |
| 15 | a malicious page starting a flow that returns to it | exact redirect URIs and web origins per environment | same |
| 16 | a machine caller pretending to be a person | a `client_credentials` token has a service `azp` and only `SERVICE`; the browser clients and the service clients are separate configuration | `SecondFactorValidatorTest.aServiceAccountDoesNotHaveToPresentASecondFactor`, `.aTokenFromAnUnknownClientWithNoEvidenceIsRefused` |
| 17 | a password posted to the application and reaching its logs | the SPA has no password field; `POST /api/signon` is not mapped in the default mode | `e2e` `the sign-on page has no password field of its own`; `KeycloakModeApiIntegrationTest` |
| 18 | a stale SSO cookie signing the next person on without either factor | RP-initiated logout with `id_token_hint` | `e2e` `the session survives a reload and signing off ends it at the realm` |
| 19 | a token surviving in a shared browser | tokens live in `sessionStorage`, so a closed tab is a closed session | `frontend/src/auth/userManager.ts` |
| 19a | an expired token kept in use by a page nobody reloads | every request asks the session for the token as it stands, so a silent renewal is carried by the next request | `App.test.tsx` `sends the token the realm renewed to, not the one the session opened with` |
| 20 | a probe learning which factor failed | factor-neutral messages in the SPA; the realm's own pages are Keycloak's and do name the factor, which is stated in `30-signing-on-with-mfa-user-guide.md` | `27-mfa-enrolment-and-signon-flow.md` §3 |
| 21 | a realm user left behind by a failed provisioning, later usable | compensation on failure and `UserReconciliation` for unlinked rows | `UserAdminProvisioningIntegrationTest`, `UserReconciliationTest` |
| 22 | a service starting insecurely because a secret was not supplied | Compose requires each with `${VAR:?}`; `bootstrap.sh` refuses to run without them | `docker-compose.yml`, `keycloak/bootstrap.sh` |

## 2. What is deliberately still weak

| ID | Weakness | Why it remains | Mitigation |
|---|---|---|---|
| UCR-14 (carried forward) | `USRSEC.SEC-USR-PWD` still holds a clear-text password | it is the shipped source data and the `legacy` mode's parity evidence depends on it | in the default mode nothing compares it; hashing or dropping the column is a business decision, not a conversion detail |
| — | `carddemo.security.mode=legacy` reproduces the previous release in full: one factor, HS256, a shared secret, a clear-text comparison | it is the rollback path and the way the `COSGN00C` evidence is reproduced | not a supported deployment mode; the default is `keycloak`, and `CARDDEMO_JWT_SECRET` is no longer set by the Compose file or the chart |
| — | the Compose stack serves HTTP on loopback and Keycloak runs with `sslRequired=external` | local development | `29-keycloak-deployment-and-operations.md` §5 states the TLS and cookie requirements for any address that is not loopback |
| — | the account service authorises on the realm role alone | USRSEC belongs to the identity service, and reading another service's database to authorise a request would undo the service boundary | the role is written from `SEC-USR-TYPE` when the account is provisioned and re-applied when the type changes; the user administration screens, where the escalation would be exercised, are the identity service's own and ask the record |
| — | passwords are seeded from the environment for the E2E users | a test has to be able to sign on | the values are never committed; the realm JSON holds no credential |

## 3. What was not done here

* No penetration test and no dependency-vulnerability scan (L-05).
* No token-binding or DPoP: bearer tokens are used as issued.
* No account-recovery flow: a user who loses its authenticator needs an administrator to clear the OTP
  credential, which is a realm operation described in `29-keycloak-deployment-and-operations.md` §6.
* No step-up authentication for individual screens; the second factor is required once per session,
  which is what a CICS sign-on was.
