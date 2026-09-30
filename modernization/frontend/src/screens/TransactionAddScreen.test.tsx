import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TransactionAddScreen } from './TransactionAddScreen';
import { stubFetch } from '../test/fixtures';

describe('Transaction add screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('asks for the confirmation before it writes, as the source did', async () => {
    const fetchMock = stubFetch({
      'POST /api/transactions': {
        body: {
          tranId: null,
          accountId: '00000000009',
          cardNumber: '4111111111111111',
          message: 'Confirm to add this transaction...',
        },
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<TransactionAddScreen onExit={() => {}} />);

    await userEvent.type(screen.getByLabelText('Account ID'), '00000000009');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Confirm to add this transaction...',
    );
    expect(screen.getByLabelText('Card Number')).toHaveValue('4111111111111111');
  });

  it('reports the edit the service refused', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'POST /api/transactions': {
          status: 400,
          body: { status: 'FIELD_NOT_NUMERIC', message: 'Type CD must be Numeric...' },
        },
      }),
    );
    render(<TransactionAddScreen onExit={() => {}} />);

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Type CD must be Numeric...');
  });

  it('prefills the map from the last transaction, as PF5 did', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/transactions/last': {
          body: {
            accountId: '00000000009',
            cardNumber: '4111111111111111',
            typeCode: '01',
            categoryCode: '0001',
            source: 'POS TERM',
            description: 'Groceries',
            amount: '+00000100.00',
            originalDate: '2022-06-01',
            processedDate: '2022-06-02',
            merchantId: '000000123',
            merchantName: 'Corner Store',
            merchantCity: 'Raleigh',
            merchantZip: '27610',
            confirm: '',
          },
        },
      }),
    );
    render(<TransactionAddScreen onExit={() => {}} />);

    await userEvent.type(screen.getByLabelText('Account ID'), '00000000009');
    await userEvent.click(screen.getByRole('button', { name: 'Copy last transaction' }));

    expect(await screen.findByLabelText('Description')).toHaveValue('Groceries');
    expect(screen.getByLabelText('Confirm')).toHaveValue('');
  });
});
