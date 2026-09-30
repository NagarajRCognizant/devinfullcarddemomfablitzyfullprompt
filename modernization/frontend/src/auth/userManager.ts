import { UserManager, WebStorageStateStore } from 'oidc-client-ts';
import type { UserManagerSettings } from 'oidc-client-ts';
import { realmConfig } from './config';

/**
 * The sign-on of the modernised application: the realm authenticates the operator with a password and
 * a TOTP, and this page only receives the resulting token.
 *
 * <p>The authorization code flow with PKCE is used and no token is written to local storage: session
 * storage ends with the tab, which is the closest equivalent to CICS discarding the COMMAREA when the
 * terminal session ended. Renewal happens in a hidden iframe before the access token expires, so a
 * long-running screen does not have to send the operator back to the realm.
 */
export function userManagerSettings(): UserManagerSettings {
  const config = realmConfig();
  return {
    authority: config.authority,
    client_id: config.clientId,
    redirect_uri: config.redirectUri,
    post_logout_redirect_uri: config.postLogoutRedirectUri,
    response_type: 'code',
    scope: 'openid profile',
    automaticSilentRenew: true,
    accessTokenExpiringNotificationTimeInSeconds: 60,
    monitorSession: false,
    stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  };
}

let manager: UserManager | null = null;

/** The one manager of the page; created on first use so a test can install its own. */
export function userManager(): UserManager {
  if (manager === null) {
    manager = new UserManager(userManagerSettings());
  }
  return manager;
}

export function setUserManager(replacement: UserManager | null): void {
  manager = replacement;
}

/** True while the browser is on the redirect the realm sent back to. */
export function isCallback(search: string = window.location.search): boolean {
  const parameters = new URLSearchParams(search);
  return parameters.has('code') || parameters.has('error');
}
