# Keycloak Realm Configuration

The realm is code: `keycloak/realm-carddemo.json` is imported on start and `keycloak/bootstrap.sh`
applies everything that must not be committed. Nothing in this document is configured by hand in the
admin console, and no secret is in the repository.

## 1. Files

| File | Contents | Applied by |
|---|---|---|
| `keycloak/realm-carddemo.json` | realm, roles, three clients, audience mappers, the browser flow, the OTP policy, brute-force settings, session lifetimes, and the ten users of the shipped USRSEC file with a `CONFIGURE_TOTP` required action | `--import-realm` on Keycloak start (`docker-compose.yml`, Helm `keycloak` values) |
| `keycloak/bootstrap.sh` | the two confidential client secrets, the realm-management roles of the identity-admin service account, the `SERVICE` role of the authorization service account, and the passwords and TOTP credentials of the users the E2E suite drives | the `keycloak-bootstrap` Compose service, or run by hand against any realm |

`bootstrap.sh` is idempotent: it updates what exists rather than failing, so it can be re-run after a
realm re-import or a secret rotation. Every value it needs comes from the environment and it refuses
to start without them (`${VAR:?}`).

## 2. Realm settings

| Setting | Value | Why |
|---|---|---|
| realm | `carddemo` | one realm for the whole application |
| `sslRequired` | `external` | local HTTP for development; external addresses must be TLS |
| `accessTokenLifespan` | 900 s | short enough that a leaked access token is of little use, long enough that a screen sequence is not interrupted; the SPA renews silently |
| `ssoSessionIdleTimeout` | 1800 s | an abandoned terminal loses its session, which is what the CICS session timeout did |
| `ssoSessionMaxLifespan` | 28800 s | one working day |
| `bruteForceProtected` | true, `failureFactor` 5 | five wrong password-or-code attempts and the account waits |
| `waitIncrementSeconds` / `maxFailureWaitSeconds` | 60 / 900 | the wait grows with repetition and caps at fifteen minutes |
| `permanentLockout` | false | an administrator is not needed to release a user who mistyped |
| `quickLoginCheckMilliSeconds` | 1000 | rejects scripted attempts faster than a person can type |

## 3. Roles

| Realm role | Stands for | Granted to |
|---|---|---|
| `ADMIN` | `SEC-USR-TYPE` `'A'`, the user `COSGN00C` routed to `COADM01C` | administrators, by the provisioning path from the USRSEC row |
| `USER` | every other `SEC-USR-TYPE`, routed to `COMEN01C` | regular users |
| `SERVICE` | a machine caller; the MQ-triggered `CP00` transaction had no signed-on user | the `carddemo-authorization-svc` service account |

`RealmRoleAuthorities` maps these to `ROLE_ADMIN`, `ROLE_USER` and `ROLE_SERVICE`; no role is derived
from anything but the signed token.

## 4. Clients

| Client | Kind | Flows | Notes |
|---|---|---|---|
| `carddemo-ui` | public | standard flow only, PKCE `S256` **required** | implicit flow and direct access grants disabled, so the SPA cannot receive a token in a URL fragment and cannot post a password; redirect URIs and web origins are `http://localhost:5173` and `http://127.0.0.1:5173` for local use and are per-environment values in Helm |
| `carddemo-identity-admin` | confidential, service account | none of the browser flows | holds the realm-management roles `manage-users`, `view-users`, `query-users` and `view-realm` only - the last because granting a new account its `ADMIN` or `USER` role begins by reading that role, which `manage-users` does not permit - which is what the provisioning in §7 of `25-authentication-and-mfa-architecture.md` needs and nothing more; both service clients allow the full scope, so that the roles their service accounts hold are in their tokens - the realm answers an Admin REST call `403 Forbidden` when they are not, whatever the account has been granted, and what limits either client is therefore the roles granted to its account |
| `carddemo-authorization-svc` | confidential, service account | `client_credentials` | holds `SERVICE`; its token is audienced to the account service |
| `carddemo-transaction-svc` | confidential, service account | `client_credentials` | holds `SERVICE`; its token is audienced to the account service, which is how the bill payment adjusts the balance as itself |

### 4.1 Audience mappers

A service accepts a token only if it is the intended audience, so the audiences are mapped
explicitly rather than left to the default `account` audience:

| Client | Audiences added to the access token |
|---|---|
| `carddemo-ui` | `carddemo-gateway`, `carddemo-identity-service`, `carddemo-account-service`, `carddemo-authorization-service`, `carddemo-transaction-service` |
| `carddemo-authorization-svc` | `carddemo-account-service` |
| `carddemo-transaction-svc` | `carddemo-account-service` |

The identity-admin client needs no mapper: it calls Keycloak, not a CardDemo service.

## 5. The browser flow

`carddemo browser` is a copy of the built-in browser flow whose forms sub-flow is:

| Step | Requirement |
|---|---|
| `auth-username-password-form` | REQUIRED |
| `auth-otp-form` | REQUIRED |

`REQUIRED` rather than `CONDITIONAL` is the whole point: with the built-in conditional OTP sub-flow a
user without an authenticator signs on with a password alone, so MFA would be optional in practice.
Because the step is required, a user without an OTP credential cannot pass it, and Keycloak sends it
through the `CONFIGURE_TOTP` required action first — enrolment is therefore mandatory and needs no
separate enforcement.

## 6. OTP policy

| Setting | Value |
|---|---|
| type | `totp` (time-based, RFC 6238) |
| algorithm | `HmacSHA1` |
| digits | 6 |
| period | 30 s |
| look-ahead window | 1 |

A code of the current or the next step is accepted, a code of the step that has passed is not, and a
code cannot be used twice. Any RFC 6238 authenticator app works; the realm's setup page offers the
secret as a QR code and, behind "Unable to scan?", as the base32 key.

## 7. Users

The realm JSON contains the ten users of the shipped `USRSEC` file (`admin001`–`admin005`,
`user0001`–`user0005`) with their names and their `ADMIN`/`USER` role, enabled, and each with the
`CONFIGURE_TOTP` required action and no credential. They exist so that the demonstration data lines up
with the database, and none of them can sign on until a password is set for it — the realm JSON is
public and therefore holds no credential at all.

`bootstrap.sh` then sets, from the environment:

| User | What it gets | Variable |
|---|---|---|
| `admin001` | password, pre-enrolled TOTP credential, no required action | `E2E_PASSWORD`, `E2E_ADMIN_TOTP_SECRET` |
| `user0001` | password, pre-enrolled TOTP credential | `E2E_PASSWORD`, `E2E_USER_TOTP_SECRET` |
| `user0002` | password, pre-enrolled TOTP credential; the user the refusal tests are locked out on | `E2E_PASSWORD`, `E2E_LOCKOUT_TOTP_SECRET` |
| `user0003` | password only, `CONFIGURE_TOTP` retained, so the enrolment test can enrol | `E2E_PASSWORD` |

The other six keep their required action and stay without a password.

## 8. Secrets

| Secret | Where it is used | Where it must come from |
|---|---|---|
| `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` | Keycloak's own bootstrap administrator, and `kcadm` in `bootstrap.sh` | the environment; a real deployment creates a scoped administrator and disables the bootstrap one |
| `KEYCLOAK_IDENTITY_CLIENT_SECRET` | identity-service, as `KEYCLOAK_CLIENT_SECRET` | secret store |
| `KEYCLOAK_AUTHORIZATION_CLIENT_SECRET` | authorization-service, as `KEYCLOAK_CLIENT_SECRET` | secret store |
| `E2E_PASSWORD`, `E2E_*_TOTP_SECRET` | the four users above and the E2E suite | the environment of the test run only |

`carddemo-ui` has no secret, because a public client cannot keep one. `.env.example` lists every
variable with a placeholder; the Compose file requires each with `${VAR:?}` so a missing value fails
the stack rather than starting it insecurely.

## 9. Applying it elsewhere

```bash
# import (or re-import) the realm
/opt/keycloak/bin/kc.sh start --import-realm       # the image does this from /opt/keycloak/data/import

# then, with the secrets in the environment
KEYCLOAK_URL=http://keycloak:8080 \
KEYCLOAK_ADMIN=… KEYCLOAK_ADMIN_PASSWORD=… \
KEYCLOAK_IDENTITY_CLIENT_SECRET=… KEYCLOAK_AUTHORIZATION_CLIENT_SECRET=… \
E2E_PASSWORD=… E2E_ADMIN_TOTP_SECRET=… E2E_USER_TOTP_SECRET=… E2E_LOCKOUT_TOTP_SECRET=… \
keycloak/bootstrap.sh
```

The script waits for the realm to answer on `KEYCLOAK_URL` using a `/dev/tcp` probe: the Keycloak
image ships neither `curl` nor `wget`, so a health check written with either silently never succeeds.
The same applies to the Compose health check for the `keycloak` service.
