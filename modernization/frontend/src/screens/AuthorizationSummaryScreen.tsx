import { useEffect, useState } from 'react';
import { ApiError, fetchAuthorizationSummary } from '../api/client';
import type { AuthorizationSummaryResponse } from '../api/types';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

/** COPAUS0C PROCESS-PF7-KEY, raised in the browser because the first page is already on display. */
const TOP_OF_PAGE = 'You are already at the top of the page...';
/** COPAUS0C PROCESS-PF8-KEY, raised when the read ahead found no further authorization. */
const BOTTOM_OF_PAGE = 'You are already at the bottom of the page...';
/** COPAUS0C PROCESS-ENTER-KEY: the selection column accepted only S or s. */
const INVALID_SELECTION = 'Invalid selection. Valid value is S';

/**
 * COPAUS0C / map COPAU00 - View Pending Authorization Summary (transaction CPVS).
 *
 * <p>PF7 and PF8 are retired per Section 8.4.5: the page is requested by the key of the last row
 * shown, and the keys of the pages already visited are kept here as a stack, which is what the
 * program kept in CDEMO-CPVS-PAGE-KEYS in the COMMAREA. Paging behaviour, the five rows per page,
 * the selection edit and the messages are the source's.
 *
 * <p>`accountId` reproduces the CDEMO-ACCT-ID the COMMAREA carried: entered from another program
 * with a numeric account id, COPAUS0C redisplayed that account's first page rather than an empty
 * screen, which is how CPVD returned here.
 */
export function AuthorizationSummaryScreen({
  accountId: enteredWith,
  onSelect,
  onExit,
}: {
  accountId?: number | null;
  onSelect: (accountId: number, authKey: string) => void;
  onExit: () => void;
}) {
  const [accountId, setAccountId] = useState(enteredWith ? String(enteredWith) : '');
  const [page, setPage] = useState<AuthorizationSummaryResponse | null>(null);
  const [cursors, setCursors] = useState<(string | null)[]>([]);
  const [selections, setSelections] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  async function load(
    afterAuthKey: string | null,
    history: (string | null)[],
    requested: string = accountId,
  ) {
    setError(null);
    setInfo(null);
    try {
      const loaded = await fetchAuthorizationSummary(requested, afterAuthKey);
      setPage(loaded);
      setCursors(history);
      setSelections({});
    } catch (failure: unknown) {
      // GATHER-DETAILS runs INITIALIZE-AUTH-DATA and re-sends an erased map, so a failed read
      // leaves no rows to select and none of the previous account's details on display.
      setPage(null);
      setCursors([]);
      setSelections({});
      setError(
        failure instanceof ApiError ? failure.message : 'Unable to read the authorization database',
      );
    }
  }

  useEffect(() => {
    if (enteredWith) {
      void load(null, [], String(enteredWith));
    }
    // The account id arrives once, when the screen is entered from another program.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enteredWith]);

  /** GATHER-DETAILS on ENTER: the account edits run on the server, so a blank id is sent too. */
  function search() {
    void load(null, []);
  }

  /**
   * PROCESS-PAGE-FORWARD: read on from the last key the page showed.
   *
   * <p>The page is read for the account the rows on display belong to, which is the CDEMO-ACCT-ID
   * the COMMAREA carried from the last ENTER: PF7 and PF8 never took the account id from the map,
   * so retyping the field without pressing Enter did not repoint the paging.
   */
  function forward() {
    if (!page?.morePages || !page.lastAuthKey) {
      setInfo(BOTTOM_OF_PAGE);
      return;
    }
    pageTo(page.lastAuthKey, [...cursors, page.lastAuthKey]);
  }

  /** PROCESS-PAGE-BACKWARD: re-read from the key the previous page started after. */
  function backward() {
    if (cursors.length === 0) {
      setInfo(TOP_OF_PAGE);
      return;
    }
    const history = cursors.slice(0, -1);
    pageTo(history.length === 0 ? null : history[history.length - 1], history);
  }

  function pageTo(afterAuthKey: string | null, history: (string | null)[]) {
    const paged = String(page?.accountId ?? '');
    setAccountId(paged);
    void load(afterAuthKey, history, paged);
  }

  /** PROCESS-ENTER-KEY: only S transfers to CPVD, and anything else redisplays the message. */
  function select(authKey: string) {
    if ((selections[authKey] ?? '').trim().toUpperCase() !== 'S') {
      setError(INVALID_SELECTION);
      return;
    }
    onSelect(page?.accountId ?? 0, authKey);
  }

  return (
    <ScreenFrame transaction="CPVS" program="COPAUS0C" title="View Pending Authorization Summary">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          search();
        }}
      >
        <div className="field">
          <label htmlFor="authAccountId">Account ID</label>
          <div className="field__group">
            <input
              id="authAccountId"
              aria-label="Account ID"
              value={accountId}
              maxLength={11}
              size={11}
              onChange={(event) => setAccountId(event.target.value)}
            />
            <button type="submit" className="primary">
              Search
            </button>
          </div>
        </div>
      </form>

      {page ? (
        <>
          <dl className="summary">
            <dt>Customer</dt>
            <dd>
              {page.customerId} - {page.customerName}
            </dd>
            <dt>Address</dt>
            <dd>
              {page.addressLine1} {page.addressLine2}
            </dd>
            <dt>Phone</dt>
            <dd>{page.phone}</dd>
            <dt>Credit Limit</dt>
            <dd>{page.creditLimit.display}</dd>
            <dt>Cash Limit</dt>
            <dd>{page.cashCreditLimit.display}</dd>
            <dt>Approved Auths</dt>
            <dd>
              {page.approvedCount} / {page.approvedAmount.display}
            </dd>
            <dt>Declined Auths</dt>
            <dd>
              {page.declinedCount} / {page.declinedAmount.display}
            </dd>
            <dt>Auth Credit Balance</dt>
            <dd>{page.creditBalance.display}</dd>
            <dt>Auth Cash Balance</dt>
            <dd>{page.cashBalance.display}</dd>
          </dl>

          <table>
            <thead>
              <tr>
                <th scope="col">Sel</th>
                <th scope="col">Date</th>
                <th scope="col">Time</th>
                <th scope="col">Type</th>
                <th scope="col">Status</th>
                <th scope="col">Match</th>
                <th scope="col">Amount</th>
                <th scope="col" />
              </tr>
            </thead>
            <tbody>
              {page.authorizations.map((row) => (
                <tr key={row.authKey}>
                  <td>
                    <input
                      aria-label={`Selection for ${row.authKey}`}
                      value={selections[row.authKey] ?? ''}
                      maxLength={1}
                      size={1}
                      onChange={(event) =>
                        setSelections({ ...selections, [row.authKey]: event.target.value })
                      }
                    />
                  </td>
                  <td>{row.authorizationDate}</td>
                  <td>{row.authorizationTime}</td>
                  <td>{row.authorizationType}</td>
                  <td>{row.approvalStatus}</td>
                  <td>{row.matchStatus}</td>
                  <td>{row.approvedAmount.display}</td>
                  <td>
                    <button type="button" onClick={() => select(row.authKey)}>
                      Enter
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
          </div>
        </>
      ) : null}

      {/* PF3 was active on every send of COPAU00, with or without authorizations on display. */}
      <div className="field__group">
        <button type="button" onClick={onExit}>
          Back
        </button>
      </div>

      <Messages info={info ?? page?.message} error={error} />
    </ScreenFrame>
  );
}
