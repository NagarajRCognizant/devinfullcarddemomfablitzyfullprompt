import { useEffect, useState } from 'react';
import { ApiError, fetchTransaction } from '../api/client';
import type { TransactionDetailResponse } from '../api/types';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

/** COTRN01C PROCESS-ENTER-KEY, for a map sent with the identifier field empty. */
const EMPTY_TRAN_ID = 'Tran ID can NOT be empty...';

/**
 * COTRN01C / map COTRN1A - View Transaction (transaction CT01).
 *
 * <p>Entered from the list with a selected identifier, or keyed here directly, as the source
 * allowed both. The read and its messages stay on the server, so "Transaction ID NOT found..."
 * and "Unable to lookup Transaction..." arrive with the response rather than being guessed here.
 */
export function TransactionViewScreen({
  tranId: enteredWith,
  onExit,
}: {
  tranId?: string | null;
  onExit: () => void;
}) {
  const [tranId, setTranId] = useState(enteredWith ?? '');
  const [transaction, setTransaction] = useState<TransactionDetailResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function load(requested: string) {
    setError(null);
    if (requested.trim() === '') {
      setTransaction(null);
      setError(EMPTY_TRAN_ID);
      return;
    }
    try {
      setTransaction(await fetchTransaction(requested.trim()));
    } catch (failure: unknown) {
      // CLEAR-CURRENT-SCREEN ran before the message was shown, so no stale record stays up.
      setTransaction(null);
      setError(
        failure instanceof ApiError ? failure.message : 'Unable to lookup Transaction...',
      );
    }
  }

  useEffect(() => {
    if (enteredWith) {
      void load(enteredWith);
    }
    // The identifier arrives once, from the selection column of CT00.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enteredWith]);

  return (
    <ScreenFrame transaction="CT01" program="COTRN01C" title="View Transaction">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void load(tranId);
        }}
      >
        <div className="field">
          <label htmlFor="viewTranId">Tran ID</label>
          <div className="field__group">
            <input
              id="viewTranId"
              aria-label="Tran ID"
              value={tranId}
              maxLength={16}
              size={16}
              onChange={(event) => setTranId(event.target.value)}
            />
            <button type="submit" className="primary">
              View
            </button>
          </div>
        </div>
      </form>

      {transaction ? (
        <dl className="summary">
          <dt>Card Number</dt>
          <dd>{transaction.cardNumber}</dd>
          <dt>Type CD</dt>
          <dd>{transaction.typeCode}</dd>
          <dt>Category CD</dt>
          <dd>{transaction.categoryCode}</dd>
          <dt>Source</dt>
          <dd>{transaction.source}</dd>
          <dt>Amount</dt>
          <dd>{transaction.amount.display}</dd>
          <dt>Description</dt>
          <dd>{transaction.description}</dd>
          <dt>Orig Date</dt>
          <dd>{transaction.originalTimestamp}</dd>
          <dt>Proc Date</dt>
          <dd>{transaction.processedTimestamp}</dd>
          <dt>Merchant ID</dt>
          <dd>{transaction.merchantId}</dd>
          <dt>Merchant Name</dt>
          <dd>{transaction.merchantName}</dd>
          <dt>Merchant City</dt>
          <dd>{transaction.merchantCity}</dd>
          <dt>Merchant Zip</dt>
          <dd>{transaction.merchantZip}</dd>
        </dl>
      ) : null}

      <div className="field__group">
        <button type="button" onClick={onExit}>
          Back
        </button>
      </div>

      <Messages error={error} />
    </ScreenFrame>
  );
}
