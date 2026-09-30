import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UserUpdateScreen } from './UserUpdateScreen';
import { stubFetch, userDetail } from '../test/fixtures';

describe('Update user screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('opens on the stored record', async () => {
    vi.stubGlobal('fetch', stubFetch({ 'GET /api/users/ADMIN001': { body: userDetail } }));

    render(<UserUpdateScreen userId="ADMIN001" onExit={() => {}} />);

    expect(await screen.findByLabelText('First Name')).toHaveValue('Admin');
    expect(screen.getByLabelText('User Type')).toHaveValue('A');
  });

  it('shows the message a submission that changed nothing is refused with', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/users/ADMIN001': { body: userDetail },
        'PUT /api/users/ADMIN001': {
          status: 400,
          body: {
            status: 'NO_CHANGES',
            message: 'Please modify to update ...',
            fieldFlags: {},
          },
        },
      }),
    );

    render(<UserUpdateScreen userId="ADMIN001" onExit={() => {}} />);
    await screen.findByDisplayValue('Admin');
    await userEvent.click(screen.getByRole('button', { name: 'Enter' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Please modify to update ...');
  });

  it('reports a user id that is not on the file', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/users/NOSUCH': {
          status: 404,
          body: { status: 'NOT_FOUND', message: 'User ID NOT found...', fieldFlags: {} },
        },
      }),
    );

    render(<UserUpdateScreen userId="NOSUCH" onExit={() => {}} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('User ID NOT found...');
  });
});
