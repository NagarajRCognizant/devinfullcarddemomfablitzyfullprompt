import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthorizationSummaryScreen } from './AuthorizationSummaryScreen';
import { authorizationPage, stubFetch } from '../test/fixtures';

describe('Authorization summary screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  async function searchAccount(fetchMock: ReturnType<typeof stubFetch>) {
    vi.stubGlobal('fetch', fetchMock);
    render(<AuthorizationSummaryScreen onSelect={() => {}} onExit={() => {}} />);
    await userEvent.type(screen.getByLabelText('Account ID'), '99');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    return await screen.findByText('55 - Alice B Carter');
  }

  it('shows the totals of PAUTSUM0 and the five row page', async () => {
    await searchAccount(stubFetch({ 'GET /api/authorizations': { body: authorizationPage } }));

    // getByText normalises the runs of spaces the PIC edited amounts carry.
    expect(screen.getByText('2 / + 300.00')).toBeInTheDocument();
    expect(screen.getByText('1 / + 40.00')).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(3);
  });

  it('pages forward from the last key shown, as PF8 did', async () => {
    const fetchMock = stubFetch({ 'GET /api/authorizations': { body: authorizationPage } });
    await searchAccount(fetchMock);

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));

    expect(String(fetchMock.mock.calls[1][0])).toContain(
      `afterAuthKey=${authorizationPage.lastAuthKey}`,
    );
  });

  it('reports the top of the page instead of paging back off the first page', async () => {
    const fetchMock = stubFetch({ 'GET /api/authorizations': { body: authorizationPage } });
    await searchAccount(fetchMock);

    await userEvent.click(screen.getByRole('button', { name: 'Previous page' }));

    expect(screen.getByRole('status')).toHaveTextContent(
      'You are already at the top of the page...',
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('reports the bottom of the page when no further authorization exists', async () => {
    const fetchMock = stubFetch({
      'GET /api/authorizations': { body: { ...authorizationPage, morePages: false } },
    });
    await searchAccount(fetchMock);

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));

    expect(screen.getByRole('status')).toHaveTextContent(
      'You are already at the bottom of the page...',
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('transfers to the detail screen on S and refuses any other selection', async () => {
    const select = vi.fn();
    vi.stubGlobal('fetch', stubFetch({ 'GET /api/authorizations': { body: authorizationPage } }));
    render(<AuthorizationSummaryScreen onSelect={select} onExit={() => {}} />);
    await userEvent.type(screen.getByLabelText('Account ID'), '99');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    await screen.findByText('55 - Alice B Carter');

    await userEvent.click(screen.getAllByRole('button', { name: 'Enter' })[0]);
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid selection. Valid value is S');
    expect(select).not.toHaveBeenCalled();

    await userEvent.type(screen.getByLabelText('Selection for 75930899999999'), 's');
    await userEvent.click(screen.getAllByRole('button', { name: 'Enter' })[0]);
    expect(select).toHaveBeenCalledWith(99, '75930899999999');
  });

  it('shows the message the service returns when the account is unknown', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/authorizations': {
          status: 404,
          body: { status: 'NOT_FOUND', message: 'Account:99 not found in XREF file.' },
        },
      }),
    );

    render(<AuthorizationSummaryScreen onSelect={() => {}} onExit={() => {}} />);
    await userEvent.type(screen.getByLabelText('Account ID'), '99');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Account:99 not found in XREF file.',
    );
  });

  it('erases the authorizations on display when the next search fails', async () => {
    const fetchMock = stubFetch({ 'GET /api/authorizations': { body: authorizationPage } });
    await searchAccount(fetchMock);

    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ status: 'NOT_FOUND', message: 'Account:7 not found in XREF file.' }), {
        status: 404,
        headers: { 'content-type': 'application/json' },
      }),
    );
    await userEvent.clear(screen.getByLabelText('Account ID'));
    await userEvent.type(screen.getByLabelText('Account ID'), '7');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Account:7 not found in XREF file.');
    expect(screen.queryByText('55 - Alice B Carter')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Enter' })).not.toBeInTheDocument();
  });

  it('pages the account on display, not an account id retyped without a search', async () => {
    const fetchMock = stubFetch({ 'GET /api/authorizations': { body: authorizationPage } });
    await searchAccount(fetchMock);

    await userEvent.clear(screen.getByLabelText('Account ID'));
    await userEvent.type(screen.getByLabelText('Account ID'), '1');
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));

    const paged = String(fetchMock.mock.calls[1][0]);
    expect(paged).toContain('accountId=99');
    expect(paged).toContain(`afterAuthKey=${authorizationPage.lastAuthKey}`);
    expect(screen.getByLabelText('Account ID')).toHaveValue('99');
  });

  it('offers the exit before any account has been searched, as PF3 always was', async () => {
    const exit = vi.fn();
    vi.stubGlobal('fetch', stubFetch({}));
    render(<AuthorizationSummaryScreen onSelect={() => {}} onExit={exit} />);

    await userEvent.click(screen.getByRole('button', { name: 'Back' }));

    expect(exit).toHaveBeenCalled();
  });

  it('redisplays the account it was entered with, which is how CPVD returns', async () => {
    const fetchMock = stubFetch({ 'GET /api/authorizations': { body: authorizationPage } });
    vi.stubGlobal('fetch', fetchMock);

    render(<AuthorizationSummaryScreen accountId={99} onSelect={() => {}} onExit={() => {}} />);

    expect(await screen.findByText('55 - Alice B Carter')).toBeInTheDocument();
    expect(String(fetchMock.mock.calls[0][0])).toContain('accountId=99');
    expect(screen.getByLabelText('Account ID')).toHaveValue('99');
  });
});
