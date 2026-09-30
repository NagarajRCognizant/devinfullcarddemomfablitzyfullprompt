import { beforeEach, describe, expect, it, vi } from 'vitest';
import { restoreSession, signIn, signOnMessage, signOut } from './session';
import { isCallback, setUserManager, userManagerSettings } from './userManager';
import { realmConfig } from './config';

/**
 * The sign-on of the modernised application. The realm holds the password and the one-time code, so
 * what is tested here is the handover: the redirect out, the token coming back, and the sign-off that
 * ends the session at the realm too.
 */
describe('Realm session', () => {
  const user = { access_token: 'realm-token', expired: false };

  function manager(overrides: Record<string, unknown> = {}) {
    return {
      getUser: vi.fn().mockResolvedValue(null),
      signinCallback: vi.fn().mockResolvedValue(user),
      signinRedirect: vi.fn().mockResolvedValue(undefined),
      signoutRedirect: vi.fn().mockResolvedValue(undefined),
      removeUser: vi.fn().mockResolvedValue(undefined),
      ...overrides,
    };
  }

  beforeEach(() => {
    setUserManager(null);
    window.history.replaceState({}, '', '/');
  });

  it('reads the token out of the redirect the realm sent back', async () => {
    const fake = manager();
    setUserManager(fake as never);
    window.history.replaceState({}, '', '/callback?code=abc&state=xyz');

    const state = await restoreSession();

    expect(state).toEqual({ status: 'signedIn', user });
    expect(fake.signinCallback).toHaveBeenCalled();
    expect(window.location.search).toBe('');
  });

  it('exchanges the code of the redirect once when asked twice at the same time', async () => {
    const fake = manager();
    setUserManager(fake as never);
    window.history.replaceState({}, '', '/callback?code=abc&state=xyz');

    const [first, second] = await Promise.all([restoreSession(), restoreSession()]);

    expect(first).toEqual({ status: 'signedIn', user });
    expect(second).toEqual(first);
    expect(fake.signinCallback).toHaveBeenCalledTimes(1);
  });

  it('keeps a session that has not expired instead of signing on again', async () => {
    const fake = manager({ getUser: vi.fn().mockResolvedValue(user) });
    setUserManager(fake as never);

    expect(await restoreSession()).toEqual({ status: 'signedIn', user });
    expect(fake.signinRedirect).not.toHaveBeenCalled();
  });

  it('treats an expired session as signed off, as CICS discarded the COMMAREA', async () => {
    setUserManager(manager({
      getUser: vi.fn().mockResolvedValue({ access_token: 'old', expired: true }),
    }) as never);

    expect(await restoreSession()).toEqual({ status: 'signedOut' });
  });

  it('reports a refused one-time code without saying which factor failed', async () => {
    setUserManager(manager({
      signinCallback: vi.fn().mockRejectedValue(new Error('access_denied')),
    }) as never);
    window.history.replaceState({}, '', '/callback?error=access_denied');

    expect(await restoreSession()).toEqual({
      status: 'signedOut',
      message: 'Unable to verify the User ID and Password. Try again ...',
    });
  });

  it('reports an unreachable realm separately from a refused sign-on', () => {
    expect(signOnMessage(new TypeError('Failed to fetch'))).toBe(
      'Unable to reach the sign-on service. Try again ...',
    );
  });

  it('signs on by redirecting to the realm', async () => {
    const fake = manager();
    setUserManager(fake as never);

    await signIn();

    expect(fake.signinRedirect).toHaveBeenCalled();
  });

  it('signs off at the realm as well, so both factors are asked for again', async () => {
    const fake = manager();
    setUserManager(fake as never);

    await signOut();

    expect(fake.removeUser).toHaveBeenCalled();
    expect(fake.signoutRedirect).toHaveBeenCalled();
  });

  it('recognises the redirect of the realm by its parameters', () => {
    expect(isCallback('?code=abc&state=xyz')).toBe(true);
    expect(isCallback('?error=access_denied')).toBe(true);
    expect(isCallback('')).toBe(false);
  });
});

describe('Realm configuration', () => {
  it('addresses the carddemo realm as a public client', () => {
    const config = realmConfig({});

    expect(config.authority).toBe('http://127.0.0.1:8088/realms/carddemo');
    expect(config.clientId).toBe('carddemo-ui');
    expect(JSON.stringify(config)).not.toMatch(/secret/i);
  });

  it('takes the realm of the deployment from the environment', () => {
    const config = realmConfig({
      VITE_KEYCLOAK_URL: 'https://sso.example.com/',
      VITE_KEYCLOAK_REALM: 'carddemo-prod',
      VITE_KEYCLOAK_CLIENT_ID: 'carddemo-ui-prod',
      VITE_KEYCLOAK_REDIRECT_URI: 'https://carddemo.example.com/callback',
      VITE_KEYCLOAK_POST_LOGOUT_REDIRECT_URI: 'https://carddemo.example.com/',
    });

    expect(config.authority).toBe('https://sso.example.com/realms/carddemo-prod');
    expect(config.clientId).toBe('carddemo-ui-prod');
    expect(config.redirectUri).toBe('https://carddemo.example.com/callback');
  });

  it('uses the authorization code flow with PKCE and keeps nothing in local storage', () => {
    const settings = userManagerSettings();

    expect(settings.response_type).toBe('code');
    expect(settings.automaticSilentRenew).toBe(true);
    expect(window.localStorage.length).toBe(0);
  });
});
