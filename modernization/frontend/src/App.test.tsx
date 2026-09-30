import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { setUserManager } from './auth/userManager';
import { stubFetch } from './test/fixtures';

/**
 * The routing of COSGN00C, now that the realm performs the sign-on: the token arrives on the redirect,
 * /api/me reads the USRSEC row of the operator, and SEC-USR-TYPE decides which menu is displayed.
 */
describe('Application shell', () => {
  function manager(user: unknown) {
    return {
      getUser: vi.fn().mockResolvedValue(user),
      signinCallback: vi.fn().mockResolvedValue(user),
      signinRedirect: vi.fn().mockResolvedValue(undefined),
      signoutRedirect: vi.fn().mockResolvedValue(undefined),
      removeUser: vi.fn().mockResolvedValue(undefined),
    };
  }

  beforeEach(() => {
    setUserManager(null);
    window.history.replaceState({}, '', '/');
    vi.restoreAllMocks();
  });

  it('shows the sign-on screen when the operator has no session', async () => {
    setUserManager(manager(null) as never);

    render(<App />);

    expect(await screen.findByRole('button', { name: 'Sign on' })).toBeEnabled();
  });

  it('routes an administrator to the admin menu, as COSGN00C does', async () => {
    setUserManager(manager({ access_token: 'realm-token', expired: false }) as never);
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/me': {
          body: {
            userId: 'ADMIN001',
            firstName: 'Admin',
            lastName: 'User',
            userType: 'A',
            nextScreen: 'COADM01C',
          },
        },
        'GET /api/admin-menu': { body: [] },
      }),
    );

    render(<App />);

    expect(await screen.findByText(/COADM01C/)).toBeInTheDocument();
  });

  it('routes every other user type to the main menu', async () => {
    setUserManager(manager({ access_token: 'realm-token', expired: false }) as never);
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/me': {
          body: {
            userId: 'USER0001',
            firstName: 'Regular',
            lastName: 'User',
            userType: 'U',
            nextScreen: 'COMEN01C',
          },
        },
        'GET /api/menu': { body: { userId: 'USER0001', userType: 'U', options: [] } },
      }),
    );

    render(<App />);

    expect(await screen.findByText(/COMEN01C/)).toBeInTheDocument();
  });

  it('sends the token of the realm on the request that reads the USRSEC row', async () => {
    setUserManager(manager({ access_token: 'realm-token', expired: false }) as never);
    const fetchMock = stubFetch({
      'GET /api/me': {
        body: {
          userId: 'USER0001',
          firstName: 'Regular',
          lastName: 'User',
          userType: 'U',
          nextScreen: 'COMEN01C',
        },
      },
      'GET /api/menu': { body: { userId: 'USER0001', userType: 'U', options: [] } },
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);
    await screen.findByText(/COMEN01C/);

    const headers = fetchMock.mock.calls[0][1]?.headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer realm-token');
  });

  it('sends the token the realm renewed to, not the one the session opened with', async () => {
    // The manager renews in the background while the page stays open, so a request has to ask it for
    // the token rather than carry the one the redirect brought back, which the services soon refuse.
    const renewing = manager({ access_token: 'realm-token', expired: false });
    renewing.getUser = vi
      .fn()
      .mockResolvedValueOnce({ access_token: 'realm-token', expired: false })
      .mockResolvedValue({ access_token: 'renewed-token', expired: false });
    setUserManager(renewing as never);
    const fetchMock = stubFetch({
      'GET /api/me': {
        body: {
          userId: 'USER0001',
          firstName: 'Regular',
          lastName: 'User',
          userType: 'U',
          nextScreen: 'COMEN01C',
        },
      },
      'GET /api/menu': { body: { userId: 'USER0001', userType: 'U', options: [] } },
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);
    await screen.findByText(/COMEN01C/);

    const headers = fetchMock.mock.calls[0][1]?.headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer renewed-token');
  });

  it('keeps a realm account with no USRSEC row off the screens', async () => {
    setUserManager(manager({ access_token: 'realm-token', expired: false }) as never);
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/me': {
          status: 404,
          body: { status: 'NOT_FOUND', message: 'User not found. Try again ...' },
        },
      }),
    );

    render(<App />);

    expect(await screen.findByRole('alert')).toHaveTextContent('User not found. Try again ...');
  });

  it('signs off at the realm when the operator leaves the menu', async () => {
    const fake = manager({ access_token: 'realm-token', expired: false });
    setUserManager(fake as never);
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/me': {
          body: {
            userId: 'USER0001',
            firstName: 'Regular',
            lastName: 'User',
            userType: 'U',
            nextScreen: 'COMEN01C',
          },
        },
        'GET /api/menu': { body: { userId: 'USER0001', userType: 'U', options: [] } },
      }),
    );

    render(<App />);
    await screen.findByText(/COMEN01C/);
    await userEvent.click(screen.getByRole('button', { name: /sign off/i }));

    await waitFor(() => expect(fake.signoutRedirect).toHaveBeenCalled());
  });
});
