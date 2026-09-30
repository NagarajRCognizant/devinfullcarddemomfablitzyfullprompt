import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TransactionViewScreen } from './TransactionViewScreen';
import { stubFetch, transactionDetail } from '../test/fixtures';

describe('Transaction view screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the record the list selected', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({ 'GET /api/transactions/0000000000000001': { body: transactionDetail } }),
    );
    render(<TransactionViewScreen tranId="0000000000000001" onExit={() => {}} />);

    expect(await screen.findByText('Corner Store')).toBeInTheDocument();
    expect(screen.getByText('4111111111111111')).toBeInTheDocument();
    expect(screen.getByText('+ 100.00')).toBeInTheDocument();
  });

  it('refuses an empty identifier without calling the service', async () => {
    const fetchMock = stubFetch({});
    vi.stubGlobal('fetch', fetchMock);
    render(<TransactionViewScreen onExit={() => {}} />);

    await userEvent.click(screen.getByRole('button', { name: 'View' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Tran ID can NOT be empty...');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('clears the screen and shows the message when the record is not on file', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/transactions/0000000000000009': {
          status: 404,
          body: { status: 'NOT_FOUND', message: 'Transaction ID NOT found...' },
        },
      }),
    );
    render(<TransactionViewScreen onExit={() => {}} />);

    await userEvent.type(screen.getByLabelText('Tran ID'), '0000000000000009');
    await userEvent.click(screen.getByRole('button', { name: 'View' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Transaction ID NOT found...');
    expect(screen.queryByText('Corner Store')).not.toBeInTheDocument();
  });
});
