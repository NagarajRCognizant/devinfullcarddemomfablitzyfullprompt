import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UserAddScreen } from './UserAddScreen';
import { stubFetch } from '../test/fixtures';

async function fillIn() {
  await userEvent.type(screen.getByLabelText('First Name'), 'Ada');
  await userEvent.type(screen.getByLabelText('Last Name'), 'Lovelace');
  await userEvent.type(screen.getByLabelText('User ID'), 'USER0099');
  await userEvent.type(screen.getByLabelText('Password'), 'PASSWORD');
  await userEvent.type(screen.getByLabelText('User Type'), 'U');
}

describe('Add user screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('clears the map after a successful write, as COUSR01C does', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'POST /api/users': {
          status: 201,
          body: {
            message: 'User USER0099 has been added ...',
            user: {
              userId: 'USER0099',
              firstName: 'Ada',
              lastName: 'Lovelace',
              password: 'PASSWORD',
              userType: 'U',
            },
          },
        },
      }),
    );

    render(<UserAddScreen onExit={() => {}} />);
    await fillIn();
    await userEvent.click(screen.getByRole('button', { name: 'Enter' }));

    expect(await screen.findByRole('status')).toHaveTextContent('has been added');
    expect(screen.getByLabelText('User ID')).toHaveValue('');
  });

  it('shows the duplicate key message and flags the user id', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'POST /api/users': {
          status: 409,
          body: {
            status: 'DUPLICATE',
            message: 'User ID already exist...',
            fieldFlags: { userId: 'NOT_OK' },
          },
        },
      }),
    );

    render(<UserAddScreen onExit={() => {}} />);
    await fillIn();
    await userEvent.click(screen.getByRole('button', { name: 'Enter' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('User ID already exist...');
    expect(screen.getByLabelText('User ID')).toHaveAttribute('aria-invalid', 'true');
  });
});
