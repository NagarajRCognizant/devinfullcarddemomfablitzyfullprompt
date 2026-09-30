import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BillPaymentScreen } from './BillPaymentScreen';
import { billPaymentBalance, stubFetch } from '../test/fixtures';

describe('Bill payment screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  async function lookupBalance(fetchMock: ReturnType<typeof stubFetch>) {
    vi.stubGlobal('fetch', fetchMock);
    render(<BillPaymentScreen onExit={() => {}} />);
    await userEvent.type(screen.getByLabelText('Account ID'), '00000000009');
    await userEvent.click(screen.getByRole('button', { name: 'Get balance' }));
    await screen.findByText('+ 1,025.50');
  }

  it('shows the balance and asks for the confirmation', async () => {
    await lookupBalance(
      stubFetch({ 'GET /api/bill-payments/00000000009': { body: billPaymentBalance } }),
    );

    expect(screen.getByRole('status')).toHaveTextContent('Confirm to make bill payment...');
    expect(screen.getByLabelText('Confirm to pay balance (Y/N)')).toBeInTheDocument();
  });

  it('sends one idempotency key with the confirmed payment', async () => {
    const fetchMock = stubFetch({
      'GET /api/bill-payments/00000000009': { body: billPaymentBalance },
      'POST /api/bill-payments': {
        body: {
          ...billPaymentBalance,
          currentBalance: { amount: 0, display: '+          0.00' },
          tranId: '0000000000000123',
          paid: true,
          message: 'Payment successful. Your Transaction ID is 0000000000000123.',
        },
      },
    });
    await lookupBalance(fetchMock);

    await userEvent.type(screen.getByLabelText('Confirm to pay balance (Y/N)'), 'Y');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    expect(await screen.findByText('0000000000000123')).toBeInTheDocument();
    const body = JSON.parse(String(fetchMock.mock.calls[1][1]?.body));
    expect(body.confirm).toBe('Y');
    expect(body.idempotencyKey).toMatch(/[0-9a-f-]{36}/);
  });

  it('reports an account with nothing to pay', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/bill-payments/00000000009': {
          status: 400,
          body: { status: 'NOTHING_TO_PAY', message: 'You have nothing to pay...' },
        },
      }),
    );
    render(<BillPaymentScreen onExit={() => {}} />);

    await userEvent.type(screen.getByLabelText('Account ID'), '00000000009');
    await userEvent.click(screen.getByRole('button', { name: 'Get balance' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('You have nothing to pay...');
  });
});
