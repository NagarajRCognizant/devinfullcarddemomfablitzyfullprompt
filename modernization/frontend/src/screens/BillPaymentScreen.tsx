import { useState } from 'react';
import { ApiError, fetchBillPaymentBalance, payBill } from '../api/client';
import type { BillPaymentResponse } from '../api/types';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

/**
 * COBIL00C / map COBIL0A - Bill Payment (transaction CB00).
 *
 * <p>The source paid the whole current balance in one unit of recovery over TRANSACT and ACCTDAT.
 * Here the transaction and the account are two services, so the confirmed send carries an
 * idempotency key: the key is minted once per balance enquiry, which makes a resubmitted
 * confirmation the same payment rather than a second one. The edits, the confirmation byte and
 * the messages are the source's.
 */
export function BillPaymentScreen({ onExit }: { onExit: () => void }) {
  const [accountId, setAccountId] = useState('');
  const [balance, setBalance] = useState<BillPaymentResponse | null>(null);
  const [confirm, setConfirm] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  /** PROCESS-ENTER-KEY with no confirmation yet: read the balance the payment would clear. */
  async function lookup() {
    setError(null);
    setInfo(null);
    setConfirm('');
    try {
      const response = await fetchBillPaymentBalance(accountId.trim());
      setBalance(response);
      setIdempotencyKey(crypto.randomUUID());
      setInfo(response.message);
    } catch (failure: unknown) {
      setBalance(null);
      setError(failure instanceof ApiError ? failure.message : 'Unable to lookup Account...');
    }
  }

  /** The confirmed send. A blank byte redisplays the confirmation, N clears the screen. */
  async function pay() {
    setError(null);
    setInfo(null);
    try {
      const response = await payBill(accountId.trim(), confirm, idempotencyKey);
      setBalance(response.paid ? response : response.accountId === '' ? null : response);
      setInfo(response.message);
      if (response.paid || response.accountId === '') {
        setConfirm('');
      }
      if (response.accountId === '') {
        setAccountId('');
      }
    } catch (failure: unknown) {
      setError(failure instanceof ApiError ? failure.message : 'Unable to update Account...');
    }
  }

  return (
    <ScreenFrame transaction="CB00" program="COBIL00C" title="Bill Payment">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void lookup();
        }}
      >
        <div className="field">
          <label htmlFor="billAccountId">Account ID</label>
          <div className="field__group">
            <input
              id="billAccountId"
              aria-label="Account ID"
              value={accountId}
              maxLength={11}
              size={11}
              onChange={(event) => setAccountId(event.target.value)}
            />
            <button type="submit" className="primary">
              Get balance
            </button>
          </div>
        </div>
      </form>

      {balance ? (
        <>
          <dl className="summary">
            <dt>Current Balance</dt>
            <dd>{balance.currentBalance.display}</dd>
            {balance.tranId ? (
              <>
                <dt>Transaction ID</dt>
                <dd>{balance.tranId}</dd>
              </>
            ) : null}
          </dl>
          <div className="field">
            <label htmlFor="billConfirm">Confirm to pay balance (Y/N)</label>
            <div className="field__group">
              <input
                id="billConfirm"
                aria-label="Confirm to pay balance (Y/N)"
                value={confirm}
                maxLength={1}
                size={1}
                onChange={(event) => setConfirm(event.target.value)}
              />
              <button type="button" className="primary" onClick={() => void pay()}>
                Send
              </button>
            </div>
          </div>
        </>
      ) : null}

      <div className="field__group">
        <button type="button" onClick={onExit}>
          Back
        </button>
      </div>

      <Messages info={info} error={error} />
    </ScreenFrame>
  );
}
