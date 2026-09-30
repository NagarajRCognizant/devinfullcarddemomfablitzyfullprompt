import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CardViewScreen } from './CardViewScreen';
import { cardDetail, stubFetch } from '../test/fixtures';

describe('Card view screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the record of the card chosen on the list', async () => {
    vi.stubGlobal('fetch', stubFetch({ 'GET /api/cards/4111111111111111': { body: cardDetail } }));
    render(
      <CardViewScreen accountId="11111111111" cardNumber="4111111111111111" onExit={() => {}} />,
    );

    expect(await screen.findByText('JOHN Q PUBLIC')).toBeInTheDocument();
    expect(screen.getByText('03/2026')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Displaying requested details');
  });

  it('searches on both keyed keys', async () => {
    const fetchMock = stubFetch({ 'GET /api/cards/4111111111111111': { body: cardDetail } });
    vi.stubGlobal('fetch', fetchMock);
    render(<CardViewScreen onExit={() => {}} />);

    await userEvent.type(screen.getByLabelText('Account Number'), '11111111111');
    await userEvent.type(screen.getByLabelText('Card Number'), '4111111111111111');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('JOHN Q PUBLIC')).toBeInTheDocument();
    expect(String(fetchMock.mock.calls[0][0])).toContain('accountId=11111111111');
  });

  it('shows the message of a search condition that matched nothing', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/cards/4111111111111111': {
          status: 404,
          body: { message: 'Did not find cards for this search condition' },
        },
      }),
    );
    render(
      <CardViewScreen accountId="11111111111" cardNumber="4111111111111111" onExit={() => {}} />,
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Did not find cards for this search condition',
    );
  });
});
