import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AccountUpdateScreen } from './AccountUpdateScreen';
import { stubFetch, updateForm } from '../test/fixtures';

const FETCH = 'GET /api/accounts/update';
const VALIDATE = 'POST /api/accounts/00000000009/update/validate';
const CONFIRM = 'POST /api/accounts/00000000009/update/confirm';

async function fetchAccount() {
  await userEvent.type(screen.getByLabelText('Account Number'), '00000000009');
  await userEvent.click(screen.getByRole('button', { name: 'Fetch' }));
}

describe('Account Update screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('keeps Save disabled until the edits have passed, then commits', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        [FETCH]: {
          body: {
            status: 'DETAILS_FETCHED',
            infoMessage: null,
            errorMessage: null,
            original: updateForm,
            updated: updateForm,
            details: null,
            fieldFlags: {},
          },
        },
        [VALIDATE]: {
          body: {
            status: 'CHANGES_VALIDATED',
            infoMessage: 'Changes validated.Press F5 to save',
            errorMessage: null,
            original: updateForm,
            updated: { ...updateForm, creditLimit: '3000.00' },
            details: null,
            fieldFlags: {},
          },
        },
        [CONFIRM]: {
          body: {
            status: 'CHANGES_COMMITTED',
            infoMessage: 'Changes committed to database',
            errorMessage: null,
            original: { ...updateForm, creditLimit: '3000.00' },
            updated: { ...updateForm, creditLimit: '3000.00' },
            details: null,
            fieldFlags: {},
          },
        },
      }),
    );

    render(<AccountUpdateScreen onExit={() => {}} />);
    await fetchAccount();

    const save = await screen.findByRole('button', { name: 'Save (F5)' });
    expect(save).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Process (ENTER)' }));
    expect(await screen.findByText('Changes validated.Press F5 to save')).toBeInTheDocument();
    await waitFor(() => expect(save).toBeEnabled());

    await userEvent.click(save);
    expect(await screen.findByText('Changes committed to database')).toBeInTheDocument();
    expect(save).toBeDisabled();
  });

  it('highlights the field that failed its edit and shows the source message', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        [FETCH]: {
          body: {
            status: 'DETAILS_FETCHED',
            infoMessage: null,
            errorMessage: null,
            original: updateForm,
            updated: updateForm,
            details: null,
            fieldFlags: {},
          },
        },
        [VALIDATE]: {
          body: {
            status: 'VALIDATION_ERROR',
            infoMessage: 'Please review and correct the highlighted fields',
            errorMessage: 'FICO Score: should be between 300 and 850',
            original: updateForm,
            updated: { ...updateForm, ficoScore: '100' },
            details: null,
            fieldFlags: { ficoScore: 'NOT_OK' },
          },
        },
      }),
    );

    render(<AccountUpdateScreen onExit={() => {}} />);
    await fetchAccount();
    await userEvent.click(await screen.findByRole('button', { name: 'Process (ENTER)' }));

    expect(await screen.findByText('FICO Score: should be between 300 and 850')).toBeInTheDocument();
    expect(screen.getByLabelText('FICO Score')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('button', { name: 'Save (F5)' })).toBeDisabled();
  });

  it('reports an unchanged submission instead of rewriting', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        [FETCH]: {
          body: {
            status: 'DETAILS_FETCHED',
            infoMessage: null,
            errorMessage: null,
            original: updateForm,
            updated: updateForm,
            details: null,
            fieldFlags: {},
          },
        },
        [VALIDATE]: {
          body: {
            status: 'NO_CHANGES',
            infoMessage: 'Enter changes and press ENTER',
            errorMessage: 'No change detected with respect to values fetched.',
            original: updateForm,
            updated: updateForm,
            details: null,
            fieldFlags: {},
          },
        },
      }),
    );

    render(<AccountUpdateScreen onExit={() => {}} />);
    await fetchAccount();
    await userEvent.click(await screen.findByRole('button', { name: 'Process (ENTER)' }));

    expect(
      await screen.findByText('No change detected with respect to values fetched.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save (F5)' })).toBeDisabled();
  });

  it('protects the account filter once the details are on the screen', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        [FETCH]: {
          body: {
            status: 'DETAILS_FETCHED',
            infoMessage: null,
            errorMessage: null,
            original: updateForm,
            updated: updateForm,
            details: null,
            fieldFlags: {},
          },
        },
      }),
    );

    render(<AccountUpdateScreen onExit={() => {}} />);
    await fetchAccount();

    // 3310-PROTECT-ALL-ATTRS protects ACCTSID and 3320-UNPROTECT-FEW-ATTRS never releases it, so
    // only the not-fetched state accepts a keyed account number.
    const filter = await screen.findByLabelText('Account Number');
    await waitFor(() => expect(filter).toHaveAttribute('readonly'));
    expect(filter).toHaveValue('00000000009');
    expect(screen.queryByRole('button', { name: 'Fetch' })).not.toBeInTheDocument();
  });

  it('cancel re-reads the record, discarding the keyed values', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        [FETCH]: {
          body: {
            status: 'DETAILS_FETCHED',
            infoMessage: null,
            errorMessage: null,
            original: updateForm,
            updated: updateForm,
            details: null,
            fieldFlags: {},
          },
        },
      }),
    );

    render(<AccountUpdateScreen onExit={() => {}} />);
    await fetchAccount();

    const creditLimit = await screen.findByLabelText('Credit Limit');
    await userEvent.clear(creditLimit);
    await userEvent.type(creditLimit, '9999.00');
    expect(creditLimit).toHaveValue('9999.00');

    await userEvent.click(screen.getByRole('button', { name: 'Cancel (F12)' }));
    await waitFor(() => expect(screen.getByLabelText('Credit Limit')).toHaveValue('5000.00'));
  });
});
