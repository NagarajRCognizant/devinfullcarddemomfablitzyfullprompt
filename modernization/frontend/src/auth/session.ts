import type { User } from 'oidc-client-ts';
import { isCallback, userManager } from './userManager';

/**
 * What the page knows about the operator, kept in the shape the screens already used.
 *
 * <p>The realm decides whether a second factor was presented and whether an authenticator still has
 * to be enrolled; both happen before the redirect comes back, so a session that exists here has
 * already been through them. `pending` is the state of the page while the realm has the operator.
 */
export type SessionState =
  | { status: 'loading' }
  | { status: 'signedOut'; message?: string }
  | { status: 'pendingRealm' }
  | { status: 'signedIn'; user: User };

let restoring: Promise<SessionState> | null = null;

/**
 * The redirect of the realm carries an authorization code the realm accepts once, so a second
 * exchange of the same code fails. Callers that ask again while the first exchange is still in
 * flight are given that same exchange.
 */
export function restoreSession(): Promise<SessionState> {
  if (restoring === null) {
    restoring = exchange().finally(() => {
      restoring = null;
    });
  }
  return restoring;
}

async function exchange(): Promise<SessionState> {
  const manager = userManager();
  try {
    if (isCallback()) {
      const user = await manager.signinCallback();
      window.history.replaceState({}, document.title, window.location.pathname);
      return user ? { status: 'signedIn', user } : { status: 'signedOut' };
    }
    const user = await manager.getUser();
    if (user && !user.expired) {
      return { status: 'signedIn', user };
    }
    return { status: 'signedOut' };
  } catch (failure: unknown) {
    return { status: 'signedOut', message: signOnMessage(failure) };
  }
}

/**
 * The token of the session as it stands, which is not the token the session started with: the manager
 * renews it in the background, and a request made after that has to carry the renewed one.
 */
export async function currentAccessToken(): Promise<string | null> {
  const user = await userManager().getUser();
  return user && !user.expired ? user.access_token : null;
}

/**
 * Sends the operator to the realm. The password and the TOTP are keyed there and never seen by this
 * page, which is the point of the change: there is no password field to intercept any more.
 */
export function signIn(): Promise<void> {
  return userManager().signinRedirect();
}

/**
 * Signs off at the realm as well, so the next sign-on asks for the password and the TOTP again.
 *
 * <p>The id token is handed to the realm as the hint of who is signing off. Without it the realm cannot
 * tell whose session to end and asks the operator to confirm on a page of its own, which is one screen
 * more than CL04 ever showed; with it the sign-off is silent and the return is straight to COSGN00C.
 */
export async function signOut(): Promise<void> {
  const manager = userManager();
  const user = await manager.getUser();
  await manager.removeUser();
  await manager.signoutRedirect({ id_token_hint: user?.id_token });
}

/**
 * The text shown on the sign-on screen when the realm refused.
 *
 * <p>A refused OTP or a locked account comes back as an error on the redirect; the message of the
 * realm is not shown to the operator, because it would say which of the two factors failed.
 */
export function signOnMessage(failure: unknown): string {
  const description = failure instanceof Error ? failure.message : String(failure ?? '');
  if (/access_denied|invalid_grant|login_required/i.test(description)) {
    return 'Unable to verify the User ID and Password. Try again ...';
  }
  return 'Unable to reach the sign-on service. Try again ...';
}
