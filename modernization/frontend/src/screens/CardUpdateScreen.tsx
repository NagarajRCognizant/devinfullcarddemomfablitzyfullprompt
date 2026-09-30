import { useEffect, useState } from 'react';
import { ApiError, fetchCardForUpdate, saveCardUpdate, validateCardUpdate } from '../api/client';
import type { CardDetailResponse, CardUpdateForm } from '../api/types';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

const EMPTY: CardUpdateForm = {
  accountId: '',
  cardNumber: '',
  embossedName: '',
  activeStatus: '',
  expiryYear: '',
  expiryMonth: '',
  fetched: null,
};

/**
 * COCRDUPC / map CCRDUPA - Update Credit Card (transaction CCUP).
 *
 * <p>The three turns of 2000-DECIDE-ACTION are kept apart: the record is fetched, the keyed
 * changes are edited without touching the file, and the save is a separate confirmation. The
 * record as it was fetched travels with the save so that a row changed in the meantime is
 * reported rather than overwritten, which is what the re-read under a lock did.
 */
export function CardUpdateScreen({
  accountId,
  cardNumber,
  onExit,
}: {
  accountId?: string | null;
  cardNumber?: string | null;
  onExit: () => void;
}) {
  const [form, setForm] = useState<CardUpdateForm>({
    ...EMPTY,
    accountId: accountId ?? '',
    cardNumber: cardNumber ?? '',
  });
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  function fill(card: CardDetailResponse) {
    setForm({
      accountId: card.accountId,
      cardNumber: card.cardNumber,
      embossedName: card.embossedName,
      activeStatus: card.activeStatus,
      expiryYear: card.expiryYear,
      expiryMonth: card.expiryMonth,
      fetched: card,
    });
  }

  async function fetchRecord(searchAccount: string, searchCard: string) {
    setError(null);
    setInfo(null);
    setConfirming(false);
    try {
      const card = await fetchCardForUpdate(searchAccount, searchCard);
      fill(card);
      setInfo(card.infoMessage);
    } catch (failure: unknown) {
      setError(failure instanceof ApiError ? failure.message : 'Error reading Card Data File');
    }
  }

  useEffect(() => {
    if (accountId && cardNumber) {
      void fetchRecord(accountId, cardNumber);
    }
    // A card chosen on the list screen arrives with both keys already known.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountId, cardNumber]);

  /** The ENTER turn: the changes are edited and the confirmation is asked for. */
  async function validate() {
    setError(null);
    setInfo(null);
    try {
      const response = await validateCardUpdate(form);
      setConfirming(true);
      setInfo(response.message);
    } catch (failure: unknown) {
      setConfirming(false);
      setError(
        failure instanceof ApiError ? failure.message : 'Changes unsuccessful. Please try again',
      );
    }
  }

  /** The PF5 turn, which rewrites the record. */
  async function save() {
    setError(null);
    setInfo(null);
    try {
      const response = await saveCardUpdate(form);
      fill(response.card);
      setConfirming(false);
      setInfo(response.message);
    } catch (failure: unknown) {
      setConfirming(false);
      setError(
        failure instanceof ApiError ? failure.message : 'Changes unsuccessful. Please try again',
      );
    }
  }

  return (
    <ScreenFrame transaction="CCUP" program="COCRDUPC" title="Update Credit Card">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void fetchRecord(form.accountId.trim(), form.cardNumber.trim());
        }}
      >
        <div className="field">
          <label htmlFor="updateAccountId">Account Number</label>
          <input
            id="updateAccountId"
            aria-label="Account Number"
            value={form.accountId}
            maxLength={11}
            size={11}
            onChange={(event) => setForm({ ...form, accountId: event.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="updateCardNumber">Card Number</label>
          <div className="field__group">
            <input
              id="updateCardNumber"
              aria-label="Card Number"
              value={form.cardNumber}
              maxLength={16}
              size={16}
              onChange={(event) => setForm({ ...form, cardNumber: event.target.value })}
            />
            <button type="submit" className="primary">
              Fetch
            </button>
          </div>
        </div>
      </form>

      <div className="field">
        <label htmlFor="embossedName">Name on Card</label>
        <input
          id="embossedName"
          aria-label="Name on Card"
          value={form.embossedName}
          maxLength={50}
          size={50}
          onChange={(event) => setForm({ ...form, embossedName: event.target.value })}
        />
      </div>
      <div className="field">
        <label htmlFor="activeStatus">Active</label>
        <input
          id="activeStatus"
          aria-label="Active"
          value={form.activeStatus}
          maxLength={1}
          size={1}
          onChange={(event) => setForm({ ...form, activeStatus: event.target.value })}
        />
      </div>
      <div className="field">
        <label htmlFor="expiryMonth">Expiry Month</label>
        <input
          id="expiryMonth"
          aria-label="Expiry Month"
          value={form.expiryMonth}
          maxLength={2}
          size={2}
          onChange={(event) => setForm({ ...form, expiryMonth: event.target.value })}
        />
      </div>
      <div className="field">
        <label htmlFor="expiryYear">Expiry Year</label>
        <input
          id="expiryYear"
          aria-label="Expiry Year"
          value={form.expiryYear}
          maxLength={4}
          size={4}
          onChange={(event) => setForm({ ...form, expiryYear: event.target.value })}
        />
      </div>

      <div className="field__group">
        <button type="button" className="primary" onClick={() => void validate()}>
          Validate changes
        </button>
        {confirming ? (
          <button type="button" className="primary" onClick={() => void save()}>
            Save changes
          </button>
        ) : null}
        <button type="button" onClick={onExit}>
          Back
        </button>
      </div>

      <Messages info={info} error={error} />
    </ScreenFrame>
  );
}
