import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthorizationDetailScreen } from './AuthorizationDetailScreen';
import { authorizationDetail, stubFetch } from '../test/fixtures';

const DETAIL_ROUTE = 'GET /api/authorizations/99/75930899999999';
const FRAUD_ROUTE = 'POST /api/authorizations/99/75930899999999/fraud';

describe('Authorization detail screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the edited fields of map COPAU1A', async () => {
    vi.stubGlobal('fetch', stubFetch({ [DETAIL_ROUTE]: { body: authorizationDetail } }));

    render(
      <AuthorizationDetailScreen accountId={99} authKey="75930899999999" onExit={() => {}} />,
    );

    expect(await screen.findByText('0000-APPROVED')).toBeInTheDocument();
    expect(screen.getByText('12/25')).toBeInTheDocument();
    expect(screen.getByText('03/01/24 10:15:00')).toBeInTheDocument();
    expect(screen.getByText('-')).toBeInTheDocument();
  });

  it('reads the next authorization of the chain, as PF8 did', async () => {
    const fetchMock = stubFetch({
      [DETAIL_ROUTE]: { body: authorizationDetail },
      'GET /api/authorizations/99/75930899999998': {
        body: { ...authorizationDetail, authKey: '75930899999998', nextAuthKey: null },
      },
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <AuthorizationDetailScreen accountId={99} authKey="75930899999999" onExit={() => {}} />,
    );
    await screen.findByText('0000-APPROVED');
    await userEvent.click(screen.getByRole('button', { name: 'Next authorization' }));

    expect(String(fetchMock.mock.calls[1][0])).toContain('/api/authorizations/99/75930899999998');
  });

  it('reports the end of the chain instead of reading past it', async () => {
    const fetchMock = stubFetch({
      [DETAIL_ROUTE]: { body: { ...authorizationDetail, nextAuthKey: null } },
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <AuthorizationDetailScreen accountId={99} authKey="75930899999999" onExit={() => {}} />,
    );
    await screen.findByText('0000-APPROVED');
    await userEvent.click(screen.getByRole('button', { name: 'Next authorization' }));

    expect(screen.getByRole('status')).toHaveTextContent('Already at the last Authorization...');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('marks the authorization as fraud and shows the message the service returns', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        [DETAIL_ROUTE]: { body: authorizationDetail },
        [FRAUD_ROUTE]: {
          body: {
            ...authorizationDetail,
            fraudStatus: 'F-03/10/24',
            message: 'AUTH MARKED FRAUD...',
          },
        },
      }),
    );

    render(
      <AuthorizationDetailScreen accountId={99} authKey="75930899999999" onExit={() => {}} />,
    );
    await screen.findByText('0000-APPROVED');
    await userEvent.click(screen.getByRole('button', { name: 'Toggle fraud' }));

    expect(await screen.findByText('F-03/10/24')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('AUTH MARKED FRAUD...');
  });
});
