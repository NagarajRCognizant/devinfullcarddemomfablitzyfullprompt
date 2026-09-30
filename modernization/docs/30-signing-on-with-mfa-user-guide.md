# Signing On with MFA — User and Administrator Guide

For the people who use the screens. The screens themselves are unchanged; only the sign-on is.

## 1. What you need

An authenticator app on a phone or a computer — Microsoft Authenticator, Google Authenticator, FreeOTP
or any other app that shows a six-digit code that changes every thirty seconds.

## 2. Signing on the first time

1. Open the application. The sign-on screen has one action, **Sign on**; there is no longer a user id
   and password field on it, because the password is asked for on the sign-on page of the identity
   provider and never by the application.
2. Key your user id and password there.
3. **Mobile Authenticator Setup** appears. Open your authenticator app, add an account and scan the
   QR code. If you cannot scan it, choose *Unable to scan?* and key the code shown there into the app
   by hand.
4. Key the six-digit code the app now shows, and confirm.
5. You arrive at your menu: the administrator menu if your user type is `A`, the main menu otherwise —
   the same routing as before.

Keep the app. You will need a code from it at every sign-on, and the setup is offered once.

## 3. Signing on afterwards

**Sign on** → user id and password → the six-digit code from the app → your menu.

## 4. When it does not work

| What you see | What it means | What to do |
|---|---|---|
| "Invalid username or password" | the user id or the password was wrong | try again; after five failed attempts the account waits before it will answer, up to fifteen minutes |
| "Invalid authenticator code" | the code was wrong, or it had already changed by the time you confirmed | wait for the app to show the next code and key that one; do not key the same code twice, a code can be used once |
| the account waits although the password is right | five failures were counted; the wait grows with each further attempt | wait, or ask an administrator to release the account |
| "Sign-on was not completed" on the CardDemo screen | the sign-on was abandoned or refused | choose **Sign on** and start again |
| the codes are always refused | the clock on the phone has drifted | turn on automatic time on the device; the code is derived from the time |
| you no longer have the app | the authenticator is gone, and it cannot be recovered by you | ask an administrator to reset it; you will be offered the setup again at the next sign-on |

## 5. Signing off

Use **Sign off** on the screen. It ends the session at the identity provider as well as in the
application, so the next sign-on asks for the password and a code again. Simply closing the tab also
ends the application session, but using **Sign off** is what ends it everywhere — which matters on a
shared machine.

## 6. For administrators

Creating and maintaining users is unchanged: the *User Administration* screens (add, update, delete)
work exactly as they did, and the field edits, messages and browse order are the same.

What is new around them:

| Situation | What happens or what to do |
|---|---|
| you add a user | it is created in the security file **and** at the identity provider, with the ADMIN or USER role taken from the user type you keyed; it has a password and no authenticator, so it enrols one at its first sign-on as in §2 |
| you change a user's type | the role at the identity provider changes with it, so the routing and the access follow immediately |
| you delete a user | it is removed from both |
| a user lost its authenticator | remove its OTP credential at the identity provider and require the setup again; see §6 of `29-keycloak-deployment-and-operations.md` |
| a user is locked out and cannot wait | clear its lockout at the identity provider |
| a user exists in the security file but cannot sign on | its identity-provider account may be missing after a failed provisioning; the reconciliation creates or links it |

The password field on the add and update screens is unchanged and still required, as `COUSR01C` and
`COUSR02C` require it: what you key there becomes the user's credential at the identity provider, which
is what the user then signs on with. It is also still written to the security file as the source did,
which is the clear-text column recorded as UCR-14; nothing compares it at sign-on any more.
