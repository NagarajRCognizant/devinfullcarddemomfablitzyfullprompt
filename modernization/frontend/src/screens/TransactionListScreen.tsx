import { useEffect, useState } from 'react';
import { ApiError, fetchTransactions } from '../api/client';
import type { TransactionListResponse } from '../api/types';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

/** COTRN00C PROCESS-ENTER-KEY: the selection column accepted only S or s. */
const INVALID_SELECTION = 'Invalid selection. Valid value is S';
/** COTRN00C PROCESS-PF7-KEY, when the first page is already on display. */
const TOP_OF_PAGE = 'You are already at the top of the page...';
/** COTRN00C PROCESS-PF8-KEY, when the read ahead found no further transaction. */
const BOTTOM_OF_PAGE = 'You are already at the bottom of the page...';

/**
 * COTRN00C / map COTRN0A - List Transactions (transaction CT00).
 *
 * <p>The browse is still positional: a page is asked for by the identifier at its edge, which is
 * what CDEMO-CT00-TRNID-FIRST and CDEMO-CT00-TRNID-LAST held in the COMMAREA between turns. PF7
 * and PF8 are retired as keys and appear as the two paging buttons; their messages are unchanged,
 * including the distinction between reaching an edge and already being on it.
 */
export function TransactionListScreen({
  onSelect,
  onExit,
}: {
  onSelect: (tranId: string) => void;
  onExit: () => void;
}) {
  const [tranId, setTranId] = useState('');
  const [page, setPage] = useState<TransactionListResponse | null>(null);
  const [selections, setSelections] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  async function load(params: Parameters<typeof fetchTransactions>[0]) {
    setError(null);
    setInfo(null);
    try {
      const loaded = await fetchTransactions(params);
      setPage(loaded);
      setSelections({});
    } catch (failure: unknown) {
      setError(failure instanceof ApiError ? failure.message : 'Unable to lookup transaction...');
    }
  }

  useEffect(() => {
    void load({ direction: 'FIRST' });
    // The first send of the map read the file from the top.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** PROCESS-ENTER-KEY: a keyed identifier repositions the browse, a blank one starts at the top. */
  function search() {
    void load({ direction: 'FIRST', tranId: tranId.trim() });
  }

  function forward() {
    if (!page?.nextPageAvailable) {
      setInfo(BOTTOM_OF_PAGE);
      return;
    }
    void load({
      direction: 'NEXT',
      lastTranId: page.lastTranId,
      pageNumber: page.pageNumber,
    });
  }

  function backward() {
    if (!page || page.pageNumber <= 1) {
      setInfo(TOP_OF_PAGE);
      return;
    }
    void load({
      direction: 'PREVIOUS',
      firstTranId: page.firstTranId,
      pageNumber: page.pageNumber,
    });
  }

  /** Only S opens CT01, and anything else redisplays the map with the selection message. */
  function select(rowTranId: string) {
    if ((selections[rowTranId] ?? '').trim().toUpperCase() !== 'S') {
      setError(INVALID_SELECTION);
      return;
    }
    onSelect(rowTranId);
  }

  return (
    <ScreenFrame transaction="CT00" program="COTRN00C" title="List Transactions">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          search();
        }}
      >
        <div className="field">
          <label htmlFor="searchTranId">Search Tran ID</label>
          <div className="field__group">
            <input
              id="searchTranId"
              aria-label="Search Tran ID"
              value={tranId}
              maxLength={16}
              size={16}
              onChange={(event) => setTranId(event.target.value)}
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
            <th scope="col">Sel</th>
            <th scope="col">Tran ID</th>
            <th scope="col">Date</th>
            <th scope="col">Description</th>
            <th scope="col">Amount</th>
            <th scope="col" />
          </tr>
        </thead>
        <tbody>
          {(page?.rows ?? []).map((row) => (
            <tr key={row.tranId}>
              <td>
                <input
                  aria-label={`Selection for ${row.tranId}`}
                  value={selections[row.tranId] ?? ''}
                  maxLength={1}
                  size={1}
                  onChange={(event) =>
                    setSelections({ ...selections, [row.tranId]: event.target.value })
                  }
                />
              </td>
              <td>{row.tranId}</td>
              <td>{row.date}</td>
              <td>{row.description}</td>
              <td>{row.amount.display}</td>
              <td>
                <button type="button" onClick={() => select(row.tranId)}>
                  View
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="field__group">
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

      <Messages info={info ?? page?.message} error={error} />
    </ScreenFrame>
  );
}
