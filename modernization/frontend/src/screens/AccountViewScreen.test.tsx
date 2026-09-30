import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AccountViewScreen } from './AccountViewScreen';
import { accountDetails, stubFetch } from '../test/fixtures';

describe('Account View screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the prompt the empty map carried', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/accounts/view': {
          body: { details: null, infoMessage: 'Enter or update id of account to display' },
        },
      }),
    );

    render(<AccountViewScreen onExit={() => {}} />);

    expect(
      await screen.findByText('Enter or update id of account to display'),
    ).toBeInTheDocument();
  });

  it('renders the fetched account and its customer', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/accounts/view': { body: { details: null, infoMessage: 'prompt' } },
        'GET /api/accounts/search': { body: { details: accountDetails, infoMessage: null } },
      }),
    );

    render(<AccountViewScreen onExit={() => {}} />);
    await userEvent.type(screen.getByLabelText('Account Number'), '00000000009');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('Account 00000000009')).toBeInTheDocument();
    expect(screen.getByText('9680294154603697')).toBeInTheDocument();
    expect(screen.getByText('123-45-6789')).toBeInTheDocument();
    // The edited PIC +ZZZ,ZZZ,ZZZ.99 text keeps its padding, so the matcher must not normalise it.
    expect(screen.getByText('+      5,000.00', { normalizer: (text) => text })).toBeInTheDocument();
  });

  it('shows the not-found message of the source and highlights the filter', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/accounts/view': { body: { details: null, infoMessage: 'prompt' } },
        'GET /api/accounts/search': {
          status: 404,
          body: {
            status: 'NOT_FOUND',
            message: 'Account:99999999999 not found in Cross ref file.',
            fieldFlags: { accountId: 'NOT_OK' },
          },
        },
      }),
    );

    render(<AccountViewScreen onExit={() => {}} />);
    await userEvent.type(screen.getByLabelText('Account Number'), '99999999999');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(
      await screen.findByText('Account:99999999999 not found in Cross ref file.'),
    ).toBeInTheDocument();
    // 9200-GETCARDXREF-BYACCT sets WS-EDIT-ACCT-FLAG on NOTFND, so the filter is shown in error.
    expect(screen.getByLabelText('Account Number')).toHaveAttribute('aria-invalid', 'true');
  });

  it('marks a blank filter with the asterisk the map wrote back', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/accounts/view': { body: { details: null, infoMessage: 'prompt' } },
        'GET /api/accounts/search': {
          status: 400,
          body: {
            status: 'ACCOUNT_ID_BLANK',
            message: 'Please enter Account Number',
            fieldFlags: { accountId: 'BLANK' },
          },
        },
      }),
    );

    render(<AccountViewScreen onExit={() => {}} />);
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('Please enter Account Number')).toBeInTheDocument();
    const filter = screen.getByLabelText('Account Number');
    expect(filter).toHaveAttribute('aria-invalid', 'true');
    expect(filter).toHaveAttribute('placeholder', '*');
  });

  it('highlights the account filter when the edit rejects it', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/accounts/view': { body: { details: null, infoMessage: 'prompt' } },
        'GET /api/accounts/search': {
          status: 400,
          body: {
            status: 'ACCOUNT_ID_NOT_VALID',
            message: 'Account Filter must  be a non-zero 11 digit number',
            fieldFlags: { accountId: 'NOT_OK' },
          },
        },
      }),
    );

    render(<AccountViewScreen onExit={() => {}} />);
    await userEvent.type(screen.getByLabelText('Account Number'), '1234567890A');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() =>
      expect(screen.getByLabelText('Account Number')).toHaveAttribute('aria-invalid', 'true'),
    );
    expect(
      screen.getByText('Account Filter must  be a non-zero 11 digit number', {
        normalizer: (text) => text,
      }),
    ).toBeInTheDocument();
  });
});
