import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CardListScreen } from './CardListScreen';
import { cardPage, stubFetch } from '../test/fixtures';

describe('Card list screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  async function openList(fetchMock: ReturnType<typeof stubFetch>) {
    vi.stubGlobal('fetch', fetchMock);
    render(<CardListScreen onView={() => {}} onUpdate={() => {}} onExit={() => {}} />);
    await screen.findByText('4111111111111111');
  }

  it('shows the page the browse returned with the action prompt of the map', async () => {
    await openList(stubFetch({ 'GET /api/cards': { body: cardPage } }));

    expect(screen.getByText('4111111111111112')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(
      'TYPE S FOR DETAIL, U TO UPDATE ANY RECORD',
    );
  });

  it('sends both filters as keyed', async () => {
    const fetchMock = stubFetch({ 'GET /api/cards': { body: cardPage } });
    await openList(fetchMock);

    await userEvent.type(screen.getByLabelText('Account Number'), '11111111111');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(String(fetchMock.mock.calls[1][0])).toContain('accountId=11111111111');
    expect(String(fetchMock.mock.calls[1][0])).toContain('direction=FIRST');
  });

  it('pages forward from the last card shown, as PF8 did', async () => {
    const fetchMock = stubFetch({ 'GET /api/cards': { body: cardPage } });
    await openList(fetchMock);

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));

    expect(String(fetchMock.mock.calls[1][0])).toContain(
      `lastCardNumber=${cardPage.lastCardNumber}`,
    );
    expect(String(fetchMock.mock.calls[1][0])).toContain('direction=NEXT');
  });

  it('refuses PF8 when the browse read no further card', async () => {
    const fetchMock = stubFetch({
      'GET /api/cards': { body: { ...cardPage, nextPageAvailable: false } },
    });
    await openList(fetchMock);

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));

    expect(screen.getByRole('alert')).toHaveTextContent('NO MORE PAGES TO DISPLAY');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('refuses PF7 on the first page', async () => {
    const fetchMock = stubFetch({ 'GET /api/cards': { body: cardPage } });
    await openList(fetchMock);

    await userEvent.click(screen.getByRole('button', { name: 'Previous page' }));

    expect(screen.getByRole('alert')).toHaveTextContent('NO PREVIOUS PAGES TO DISPLAY');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('refuses two marked rows', async () => {
    await openList(stubFetch({ 'GET /api/cards': { body: cardPage } }));

    await userEvent.type(screen.getByLabelText('Action for 4111111111111111'), 'S');
    await userEvent.type(screen.getByLabelText('Action for 4111111111111112'), 'U');
    await userEvent.click(screen.getByRole('button', { name: 'Apply action' }));

    expect(screen.getByRole('alert')).toHaveTextContent(
      'PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE',
    );
  });

  it('refuses an action code that is neither S nor U', async () => {
    await openList(stubFetch({ 'GET /api/cards': { body: cardPage } }));

    await userEvent.type(screen.getByLabelText('Action for 4111111111111111'), 'X');
    await userEvent.click(screen.getByRole('button', { name: 'Apply action' }));

    expect(screen.getByRole('alert')).toHaveTextContent('INVALID ACTION CODE');
  });

  it('hands S to the detail screen and U to the update screen', async () => {
    const viewed: string[] = [];
    const updated: string[] = [];
    vi.stubGlobal('fetch', stubFetch({ 'GET /api/cards': { body: cardPage } }));
    render(
      <CardListScreen
        onView={(accountId, cardNumber) => viewed.push(`${accountId}/${cardNumber}`)}
        onUpdate={(accountId, cardNumber) => updated.push(`${accountId}/${cardNumber}`)}
        onExit={() => {}}
      />,
    );
    await screen.findByText('4111111111111111');

    await userEvent.type(screen.getByLabelText('Action for 4111111111111111'), 's');
    await userEvent.click(screen.getByRole('button', { name: 'Apply action' }));
    expect(viewed).toEqual(['11111111111/4111111111111111']);

    await userEvent.clear(screen.getByLabelText('Action for 4111111111111111'));
    await userEvent.type(screen.getByLabelText('Action for 4111111111111112'), 'u');
    await userEvent.click(screen.getByRole('button', { name: 'Apply action' }));
    expect(updated).toEqual(['11111111111/4111111111111112']);
  });

  it('shows the message of a rejected filter', async () => {
    vi.stubGlobal(
      'fetch',
      stubFetch({
        'GET /api/cards': {
          status: 400,
          body: { message: 'ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER' },
        },
      }),
    );
    render(<CardListScreen onView={() => {}} onUpdate={() => {}} onExit={() => {}} />);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER',
    );
  });
});
