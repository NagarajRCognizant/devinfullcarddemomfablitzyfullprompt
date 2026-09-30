import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MenuScreen } from './MenuScreen';
import { regularUserMenu, stubFetch } from '../test/fixtures';

describe('Main menu screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('offers the account view option to a regular user', async () => {
    vi.stubGlobal('fetch', stubFetch({ 'GET /api/menu': { body: regularUserMenu } }));
    const select = vi.fn();

    render(<MenuScreen onSelect={select} onSignOff={() => {}} />);
    await userEvent.click(await screen.findByRole('button', { name: '01. Account View' }));

    expect(select).toHaveBeenCalledWith('COACTVWC');
  });

  it('leaves an option this user type may not select unselectable', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/menu': {
          body: {
            ...regularUserMenu,
            options: [{ ...regularUserMenu.options[0], userType: 'A', accessible: false }],
          },
        },
      }),
    );

    render(<MenuScreen onSelect={() => {}} onSignOff={() => {}} />);

    expect(await screen.findByRole('button', { name: '01. Account View' })).toBeDisabled();
  });
});
