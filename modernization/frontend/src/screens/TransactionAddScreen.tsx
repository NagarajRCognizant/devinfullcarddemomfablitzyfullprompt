import { useState } from 'react';
import { ApiError, addTransaction, fetchLastTransaction } from '../api/client';
import type { TransactionAddForm } from '../api/types';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

const EMPTY: TransactionAddForm = {
  accountId: '',
  cardNumber: '',
  typeCode: '',
  categoryCode: '',
  source: '',
  description: '',
  amount: '',
  originalDate: '',
  processedDate: '',
  merchantId: '',
  merchantName: '',
  merchantCity: '',
  merchantZip: '',
  confirm: '',
};

/**
 * COTRN02C / map COTRN2A - Add Transaction (transaction CT02).
 *
 * <p>The edits and the identifier arithmetic stay on the server, so the screen sends what was
 * keyed and redisplays the message it gets back: the first send with a blank confirmation returns
 * "Confirm to add this transaction...", and only a Y writes the record. PF4 clears the map and
 * PF5 copies the last transaction of the account or card, both as explicit buttons.
 */
export function TransactionAddScreen({ onExit }: { onExit: () => void }) {
  const [form, setForm] = useState<TransactionAddForm>(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  function set(field: keyof TransactionAddForm, value: string) {
    setForm({ ...form, [field]: value });
  }

  async function submit() {
    setError(null);
    setInfo(null);
    try {
      const response = await addTransaction(form);
      setForm({ ...form, accountId: response.accountId, cardNumber: response.cardNumber });
      setInfo(response.message);
      if (response.tranId) {
        // The source cleared the map after the write, leaving only the success message.
        setForm(EMPTY);
      }
    } catch (failure: unknown) {
      setError(failure instanceof ApiError ? failure.message : 'Unable to add transaction...');
    }
  }

  /** PF5: COPY-LAST-TRAN-DATA, which prefilled the map from the last transaction on file. */
  async function copyLast() {
    setError(null);
    setInfo(null);
    try {
      const last = await fetchLastTransaction({
        accountId: form.accountId.trim(),
        cardNumber: form.cardNumber.trim(),
      });
      setForm({ ...last, confirm: '' });
    } catch (failure: unknown) {
      setError(failure instanceof ApiError ? failure.message : 'Unable to lookup transaction...');
    }
  }

  return (
    <ScreenFrame transaction="CT02" program="COTRN02C" title="Add Transaction">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        {(
          [
            ['Account ID', 'accountId', 11],
            ['Card Number', 'cardNumber', 16],
            ['Type CD', 'typeCode', 2],
            ['Category CD', 'categoryCode', 4],
            ['Source', 'source', 10],
            ['Description', 'description', 100],
            ['Amount', 'amount', 12],
            ['Orig Date', 'originalDate', 10],
            ['Proc Date', 'processedDate', 10],
            ['Merchant ID', 'merchantId', 9],
            ['Merchant Name', 'merchantName', 30],
            ['Merchant City', 'merchantCity', 25],
            ['Merchant Zip', 'merchantZip', 10],
            ['Confirm', 'confirm', 1],
          ] as [string, keyof TransactionAddForm, number][]
        ).map(([label, field, length]) => (
          <div className="field" key={field}>
            <label htmlFor={field}>{label}</label>
            <div className="field__group">
              <input
                id={field}
                aria-label={label}
                value={form[field]}
                maxLength={length}
                size={Math.min(length, 30)}
                onChange={(event) => set(field, event.target.value)}
              />
            </div>
          </div>
        ))}

        <div className="field__group">
          <button type="submit" className="primary">
            Add
          </button>
          <button type="button" onClick={() => setForm(EMPTY)}>
            Clear
          </button>
          <button type="button" onClick={() => void copyLast()}>
            Copy last transaction
          </button>
          <button type="button" onClick={onExit}>
            Back
          </button>
        </div>
      </form>

      <Messages info={info} error={error} />
    </ScreenFrame>
  );
}
