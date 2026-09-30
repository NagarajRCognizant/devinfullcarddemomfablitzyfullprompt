# MFA Enrolment and Sign-on Flow

What happens between a person opening the application and a screen appearing, and what happens when it
does not. Every step below is exercised by the Playwright suite in `e2e/tests` against the Compose
stack; the test that covers it is named in the last column.

## 1. First sign-on: enrolment

| Step | Where | What happens | Test |
|---|---|---|---|
| 1 | SPA | `COSGN00C`'s screen has no user id or password field; it has one action, which starts the authorization request (Authorization Code, PKCE `S256`) | `the sign-on page has no password field of its own` |
| 2 | realm | the login page asks for the user id and password | |
| 3 | realm | the OTP step is REQUIRED and the user has no authenticator, so the `CONFIGURE_TOTP` required action runs: "Mobile Authenticator Setup", a QR code, and the base32 key behind "Unable to scan?" | `a user without an authenticator has to enrol one before reaching a screen` |
| 4 | user | scans or keys the secret into an authenticator app and keys the code it shows | |
| 5 | realm | stores the OTP credential, clears the required action, issues the code and redirects to the SPA's callback | |
| 6 | SPA | exchanges the code with the verifier, stores the tokens in `sessionStorage`, calls `GET /api/me` | |
| 7 | SPA | routes on the USRSEC row: `COADM01C` for `SEC-USR-TYPE` `'A'`, `COMEN01C` otherwise, exactly as `PROCESS-ENTER-KEY` did | `an administrator reaches the admin menu after both factors`, `a regular user reaches the main menu and not the admin one` |

A user created through `COUSR01C` arrives here: provisioning sets `CONFIGURE_TOTP`, so its first
sign-on enrols. Enrolment is not a separate administrative act.

## 2. Later sign-ons

Steps 1, 2, then the OTP page, then 6 and 7. The password and the code are keyed on the realm's pages
only; no CardDemo component sees either.

## 3. Refusals

| Case | Realm behaviour | Application behaviour | Test |
|---|---|---|---|
| wrong password | login page again, "Invalid username or password", failure counted | never reached the application | `wrong codes are refused and in the end lock the account out` |
| wrong code | OTP page again, "Invalid authenticator code", failure counted | no token, so no screen | same |
| code of a step that has passed | refused; the look-ahead window is 1, so only the current and next step are accepted | | same |
| a code used twice | refused as a replay | | same |
| five failures | the account waits, growing to fifteen minutes; the password is refused before the OTP page is reached | | same |
| the user cancels or the realm denies | the SPA shows the sign-on screen again with a factor-neutral message | the message never says which factor failed | |
| expired access token | the SPA renews silently with the refresh token; if renewal fails the session is dropped and the sign-on screen returns | each request reads the token from the session as it stands, so a renewal is carried without a reload; a request that still presents an expired token is answered 401 | `the session survives a reload and signing off ends it at the realm` |

The messages the SPA shows are deliberately factor-neutral: "Sign-on was not completed" rather than
"wrong code", because a message that distinguishes the factors tells an attacker which half it has.
The pages of the realm are Keycloak's own, and they do name the factor - "Invalid username or
password", then "Invalid authenticator code". What the application itself ever learns of a refused
sign-on is that it was refused, which is all it says.

## 4. Signing off

`COSGN00C`'s sign-off cleared the COMMAREA; the modern equivalent must also end the realm's session,
or the next sign-on is silently answered from the SSO cookie without either factor. The SPA therefore
performs RP-initiated logout: it reads the current user, removes it locally, and redirects to the
realm's `end_session_endpoint` with `id_token_hint`, which is what lets the realm end the session
without asking for confirmation. It returns to the sign-on screen, and a following sign-on asks for
the password and a code again.

## 5. Machine callers

The Kafka-driven authorization flow has no person and no authenticator. `authorization-service`
obtains its own token with `client_credentials` and `ServiceAccessTokens` caches it until shortly
before expiry. `SecondFactorValidator` accepts it because its `azp` is a configured service client
(§3.1 of `25-authentication-and-mfa-architecture.md`), not because the MFA requirement is switched
off — the browser clients and the service clients are separated by client id, so relaxing one does not
relax the other.

## 6. What the tests need

The suite computes codes the way an authenticator app does, from the secrets `bootstrap.sh` seeded, so
`e2e/tests/realm.ts` must:

* wait for a step boundary rather than sign a code that expires between computing and submitting it,
* never re-use a step for the same user, because the realm refuses a replayed code,
* drive the refusals as a user no other test signs on as (`user0002`), because the lockout is
  per-user and would otherwise fall on a later test,
* read the enrolment secret off the page and base32-decode it, which is what an app scanning the QR
  code does.

These are properties of the realm's OTP policy, not test scaffolding: a person reading a countdown on
a phone satisfies them without noticing.
