import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminMenuScreen } from './AdminMenuScreen';
import { adminMenuOptions, stubFetch } from '../test/fixtures';

describe('Admin menu screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('lists the six options of COADM02Y', async () => {
    vi.stubGlobal('fetch', stubFetch({ 'GET /api/admin-menu': { body: adminMenuOptions } }));

    render(<AdminMenuScreen onSelect={() => {}} onSignOff={() => {}} />);

    expect(await screen.findByRole('button', { name: '01. User List (Security)' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '06. Transaction Type Maintenance (Db2)' })).toBeInTheDocument();
  });

  it('transfers to the program of the keyed option', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/admin-menu': { body: adminMenuOptions },
        'GET /api/admin-menu/selection': { body: adminMenuOptions[1] },
      }),
    );
    const select = vi.fn();

    render(<AdminMenuScreen onSelect={select} onSignOff={() => {}} />);
    await userEvent.type(await screen.findByLabelText('Option'), '2');
    await userEvent.click(screen.getByRole('button', { name: 'Enter' }));

    expect(select).toHaveBeenCalledWith('COUSR01C');
  });

  it('shows the message an out of range option is refused with', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/admin-menu': { body: adminMenuOptions },
        'GET /api/admin-menu/selection': {
          status: 400,
          body: {
            status: 'VALIDATION_ERROR',
            message: 'Please enter a valid option number...',
            fieldFlags: { option: 'NOT_OK' },
          },
        },
      }),
    );

    render(<AdminMenuScreen onSelect={() => {}} onSignOff={() => {}} />);
    await userEvent.type(await screen.findByLabelText('Option'), '9');
    await userEvent.click(screen.getByRole('button', { name: 'Enter' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Please enter a valid option number...',
    );
  });

  it('reports that an out of scope option was not converted', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/admin-menu': { body: adminMenuOptions },
        'GET /api/admin-menu/selection': { body: adminMenuOptions[4] },
      }),
    );
    const select = vi.fn();

    render(<AdminMenuScreen onSelect={select} onSignOff={() => {}} />);
    await userEvent.click(await screen.findByRole('button', { name: '05. Transaction Type List/Update (Db2)' }));

    expect(await screen.findByRole('status')).toHaveTextContent('not part of this conversion');
    expect(select).not.toHaveBeenCalled();
  });
});
