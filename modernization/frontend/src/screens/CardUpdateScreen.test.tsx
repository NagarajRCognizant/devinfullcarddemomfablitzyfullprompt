import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CardUpdateScreen } from './CardUpdateScreen';
import { cardDetail, stubFetch } from '../test/fixtures';

const FETCH_ROUTE = 'GET /api/cards/4111111111111111/update';

describe('Card update screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  async function openUpdate(fetchMock: ReturnType<typeof stubFetch>) {
    vi.stubGlobal('fetch', fetchMock);
    render(
      <CardUpdateScreen accountId="11111111111" cardNumber="4111111111111111" onExit={() => {}} />,
    );
    await screen.findByDisplayValue('JOHN Q PUBLIC');
  }

  it('fills the map from the record as fetched', async () => {
    await openUpdate(stubFetch({ [FETCH_ROUTE]: { body: cardDetail } }));

    expect(screen.getByLabelText('Active')).toHaveValue('Y');
    expect(screen.getByLabelText('Expiry Month')).toHaveValue('03');
    expect(screen.getByLabelText('Expiry Year')).toHaveValue('2026');
    expect(screen.queryByRole('button', { name: 'Save changes' })).not.toBeInTheDocument();
  });

  it('asks for the save confirmation once the changes pass the edits', async () => {
    await openUpdate(
      stubFetch({
        [FETCH_ROUTE]: { body: cardDetail },
        'POST /api/cards/validate': {
          body: { card: cardDetail, updated: false, message: 'Changes validated.Press F5 to save' },
        },
      }),
    );

    await userEvent.clear(screen.getByLabelText('Name on Card'));
    await userEvent.type(screen.getByLabelText('Name on Card'), 'JANE Q PUBLIC');
    await userEvent.click(screen.getByRole('button', { name: 'Validate changes' }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Changes validated.Press F5 to save',
    );
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeInTheDocument();
  });

  it('saves the confirmed changes and reports the commit', async () => {
    const saved = { ...cardDetail, embossedName: 'JANE Q PUBLIC' };
    const fetchMock = stubFetch({
      [FETCH_ROUTE]: { body: cardDetail },
      'POST /api/cards/validate': {
        body: { card: cardDetail, updated: false, message: 'Changes validated.Press F5 to save' },
      },
      'POST /api/cards': {
        body: { card: saved, updated: true, message: 'Changes committed to database' },
      },
    });
    await openUpdate(fetchMock);

    await userEvent.clear(screen.getByLabelText('Name on Card'));
    await userEvent.type(screen.getByLabelText('Name on Card'), 'JANE Q PUBLIC');
    await userEvent.click(screen.getByRole('button', { name: 'Validate changes' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Save changes' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Changes committed to database');
    expect(screen.getByLabelText('Name on Card')).toHaveValue('JANE Q PUBLIC');
    expect(screen.queryByRole('button', { name: 'Save changes' })).not.toBeInTheDocument();
  });

  it('reports a submission that changed nothing', async () => {
    await openUpdate(
      stubFetch({
        [FETCH_ROUTE]: { body: cardDetail },
        'POST /api/cards/validate': {
          status: 400,
          body: { message: 'No change detected with respect to values fetched.' },
        },
      }),
    );

    await userEvent.click(screen.getByRole('button', { name: 'Validate changes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No change detected with respect to values fetched.',
    );
  });

  it('reports a record another operator changed in the meantime', async () => {
    await openUpdate(
      stubFetch({
        [FETCH_ROUTE]: { body: cardDetail },
        'POST /api/cards/validate': {
          body: { card: cardDetail, updated: false, message: 'Changes validated.Press F5 to save' },
        },
        'POST /api/cards': {
          status: 409,
          body: { message: 'Record changed by some one else. Please review' },
        },
      }),
    );

    await userEvent.clear(screen.getByLabelText('Name on Card'));
    await userEvent.type(screen.getByLabelText('Name on Card'), 'JANE Q PUBLIC');
    await userEvent.click(screen.getByRole('button', { name: 'Validate changes' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Save changes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Record changed by some one else. Please review',
    );
  });

  it('reports a rejected edit of the keyed changes', async () => {
    await openUpdate(
      stubFetch({
        [FETCH_ROUTE]: { body: cardDetail },
        'POST /api/cards/validate': {
          status: 400,
          body: { message: 'Card expiry month must be between 1 and 12' },
        },
      }),
    );

    await userEvent.clear(screen.getByLabelText('Expiry Month'));
    await userEvent.type(screen.getByLabelText('Expiry Month'), '13');
    await userEvent.click(screen.getByRole('button', { name: 'Validate changes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Card expiry month must be between 1 and 12',
    );
  });
});
