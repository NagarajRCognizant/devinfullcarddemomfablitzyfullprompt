import { useEffect, useState } from 'react';
import { ApiError, fetchCards } from '../api/client';
import type { CardListResponse } from '../api/types';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

/** COCRDLIC 2250-EDIT-ARRAY: only S and U are action codes. */
const INVALID_ACTION_CODE = 'INVALID ACTION CODE';
/** COCRDLIC 2250-EDIT-ARRAY, when more than one row carries an action. */
const MORE_THAN_ONE_ACTION = 'PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE';
/** COCRDLIC 1400-SETUP-MESSAGE, PF7 on the first page. */
const NO_PREVIOUS_PAGES = 'NO PREVIOUS PAGES TO DISPLAY';
/** COCRDLIC 1400-SETUP-MESSAGE, PF8 when no further record was read ahead. */
const NO_MORE_PAGES = 'NO MORE PAGES TO DISPLAY';

/**
 * COCRDLIC / map CCRDLIA - List Credit Cards (transaction CCLI).
 *
 * <p>The seven row page and the positional browse are kept: a page is asked for by the card
 * number at its edge, which is what the COMMAREA held between turns. The action column is edited
 * on the client exactly where 2250-EDIT-ARRAY edited it, because the source refuses the whole
 * page before it reads anything when the operator marked two rows.
 */
export function CardListScreen({
  onView,
  onUpdate,
  onExit,
}: {
  onView: (accountId: string, cardNumber: string) => void;
  onUpdate: (accountId: string, cardNumber: string) => void;
  onExit: () => void;
}) {
  const [accountFilter, setAccountFilter] = useState('');
  const [cardFilter, setCardFilter] = useState('');
  const [page, setPage] = useState<CardListResponse | null>(null);
  const [actions, setActions] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  async function load(params: Parameters<typeof fetchCards>[0]) {
    setError(null);
    setInfo(null);
    try {
      const loaded = await fetchCards(params);
      setPage(loaded);
      setActions({});
    } catch (failure: unknown) {
      setError(failure instanceof ApiError ? failure.message : 'Error reading Card Data File');
    }
  }

  useEffect(() => {
    void load({ direction: 'FIRST' });
    // The first send of the map browses the file from the top.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function search() {
    void load({
      direction: 'FIRST',
      accountId: accountFilter.trim(),
      cardNumber: cardFilter.trim(),
    });
  }

  function forward() {
    if (!page?.nextPageAvailable) {
      setError(NO_MORE_PAGES);
      return;
    }
    void load({
      direction: 'NEXT',
      accountId: accountFilter.trim(),
      cardNumber: cardFilter.trim(),
      lastCardNumber: page.lastCardNumber,
      pageNumber: page.pageNumber,
    });
  }

  function backward() {
    if (!page || page.pageNumber <= 1) {
      setError(NO_PREVIOUS_PAGES);
      return;
    }
    void load({
      direction: 'PREVIOUS',
      accountId: accountFilter.trim(),
      cardNumber: cardFilter.trim(),
      firstCardNumber: page.firstCardNumber,
      pageNumber: page.pageNumber,
    });
  }

  /** 2250-EDIT-ARRAY: one S or one U per page, and nothing else. */
  function act() {
    const marked = Object.entries(actions)
      .map(([cardNumber, action]) => [cardNumber, action.trim().toUpperCase()] as const)
      .filter(([, action]) => action !== '');
    if (marked.length === 0) {
      return;
    }
    if (marked.length > 1) {
      setError(MORE_THAN_ONE_ACTION);
      return;
    }
    const [cardNumber, action] = marked[0];
    const row = page?.rows.find((candidate) => candidate.cardNumber === cardNumber);
    if (!row || (action !== 'S' && action !== 'U')) {
      setError(INVALID_ACTION_CODE);
      return;
    }
    if (action === 'S') {
      onView(row.accountId, row.cardNumber);
    } else {
      onUpdate(row.accountId, row.cardNumber);
    }
  }

  return (
    <ScreenFrame transaction="CCLI" program="COCRDLIC" title="List Credit Cards">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          search();
        }}
      >
        <div className="field">
          <label htmlFor="accountFilter">Account Number</label>
          <input
            id="accountFilter"
            aria-label="Account Number"
            value={accountFilter}
            maxLength={11}
            size={11}
            onChange={(event) => setAccountFilter(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="cardFilter">Card Number</label>
          <div className="field__group">
            <input
              id="cardFilter"
              aria-label="Card Number"
              value={cardFilter}
              maxLength={16}
              size={16}
              onChange={(event) => setCardFilter(event.target.value)}
            />
            <button type="submit" className="primary">
              Search
            </button>
          </div>
        </div>
      </form>

      <table>
        <thead>
          <tr>
            <th scope="col">Action</th>
            <th scope="col">Card Number</th>
            <th scope="col">Account</th>
            <th scope="col">Active</th>
          </tr>
        </thead>
        <tbody>
          {(page?.rows ?? []).map((row) => (
            <tr key={row.cardNumber}>
              <td>
                <input
                  aria-label={`Action for ${row.cardNumber}`}
                  value={actions[row.cardNumber] ?? ''}
                  maxLength={1}
                  size={1}
                  onChange={(event) =>
                    setActions({ ...actions, [row.cardNumber]: event.target.value })
                  }
                />
              </td>
              <td>{row.cardNumber}</td>
              <td>{row.accountId}</td>
              <td>{row.activeStatus}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="field__group">
        <button type="button" className="primary" onClick={act}>
          Apply action
        </button>
        <button type="button" onClick={backward}>
          Previous page
        </button>
        <button type="button" onClick={forward}>
          Next page
        </button>
        <button type="button" onClick={onExit}>
          Back
        </button>
      </div>

      <Messages info={info ?? page?.infoMessage} error={error ?? page?.errorMessage} />
    </ScreenFrame>
  );
}
