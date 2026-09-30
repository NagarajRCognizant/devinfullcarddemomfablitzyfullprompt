import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UserDeleteScreen } from './UserDeleteScreen';
import { stubFetch, userDetail } from '../test/fixtures';

describe('Delete user screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('displays the record before the delete is offered', async () => {
    vi.stubGlobal('fetch', stubFetch({ 'GET /api/users/ADMIN001': { body: userDetail } }));

    render(<UserDeleteScreen userId="ADMIN001" onExit={() => {}} />);

    expect(await screen.findByText('Admin')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'F5 - Delete' })).toBeEnabled();
  });

  it('confirms the delete with the message of COUSR03C', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/users/ADMIN001': { body: userDetail },
        'DELETE /api/users/ADMIN001': {
          body: { message: 'User ADMIN001 has been deleted ...', user: null },
        },
      }),
    );

    render(<UserDeleteScreen userId="ADMIN001" onExit={() => {}} />);
    await userEvent.click(await screen.findByRole('button', { name: 'F5 - Delete' }));

    expect(await screen.findByRole('status')).toHaveTextContent('has been deleted');
  });

  it('keeps the delete failure message of the source, which reports an update', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/users/ADMIN001': { body: userDetail },
        'DELETE /api/users/ADMIN001': {
          status: 500,
          body: { status: 'DELETE_FAILED', message: 'Unable to Update User...', fieldFlags: {} },
        },
      }),
    );

    render(<UserDeleteScreen userId="ADMIN001" onExit={() => {}} />);
    await userEvent.click(await screen.findByRole('button', { name: 'F5 - Delete' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to Update User...');
  });
});
