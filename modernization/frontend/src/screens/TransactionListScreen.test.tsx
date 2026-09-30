import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TransactionListScreen } from './TransactionListScreen';
import { stubFetch, transactionPage } from '../test/fixtures';

describe('Transaction list screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  async function openList(fetchMock: ReturnType<typeof stubFetch>) {
    vi.stubGlobal('fetch', fetchMock);
    render(<TransactionListScreen onSelect={() => {}} onExit={() => {}} />);
    await screen.findByText('Groceries');
  }

  it('shows the page the browse returned', async () => {
    await openList(stubFetch({ 'GET /api/transactions': { body: transactionPage } }));

    expect(screen.getByText('0000000000000001')).toBeInTheDocument();
    expect(screen.getByText('+ 100.00')).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(3);
  });

  it('pages forward from the last identifier shown, as PF8 did', async () => {
    const fetchMock = stubFetch({ 'GET /api/transactions': { body: transactionPage } });
    await openList(fetchMock);

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));

    expect(String(fetchMock.mock.calls[1][0])).toContain(
      `lastTranId=${transactionPage.lastTranId}`,
    );
    expect(String(fetchMock.mock.calls[1][0])).toContain('direction=NEXT');
  });

  it('reports the top of the page instead of paging back off the first page', async () => {
    const fetchMock = stubFetch({ 'GET /api/transactions': { body: transactionPage } });
    await openList(fetchMock);

    await userEvent.click(screen.getByRole('button', { name: 'Previous page' }));

    expect(screen.getByRole('status')).toHaveTextContent(
      'You are already at the top of the page...',
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('reports the bottom of the page when the browse read no further transaction', async () => {
    const fetchMock = stubFetch({
      'GET /api/transactions': { body: { ...transactionPage, nextPageAvailable: false } },
    });
    await openList(fetchMock);

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));

    expect(screen.getByRole('status')).toHaveTextContent(
      'You are already at the bottom of the page...',
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('refuses a selection character other than S', async () => {
    await openList(stubFetch({ 'GET /api/transactions': { body: transactionPage } }));

    await userEvent.type(screen.getByLabelText('Selection for 0000000000000001'), 'X');
    await userEvent.click(screen.getAllByRole('button', { name: 'View' })[0]);

    expect(screen.getByRole('alert')).toHaveTextContent('Invalid selection. Valid value is S');
  });

  it('opens the view screen for a selection of S', async () => {
    const selected = vi.fn();
    vi.stubGlobal('fetch', stubFetch({ 'GET /api/transactions': { body: transactionPage } }));
    render(<TransactionListScreen onSelect={selected} onExit={() => {}} />);
    await screen.findByText('Groceries');

    await userEvent.type(screen.getByLabelText('Selection for 0000000000000002'), 's');
    await userEvent.click(screen.getAllByRole('button', { name: 'View' })[1]);

    expect(selected).toHaveBeenCalledWith('0000000000000002');
  });
});
