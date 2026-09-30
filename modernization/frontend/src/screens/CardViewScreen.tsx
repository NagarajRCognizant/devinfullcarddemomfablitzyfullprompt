import { useEffect, useState } from 'react';
import { ApiError, fetchCard } from '../api/client';
import type { CardDetailResponse } from '../api/types';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

/**
 * COCRDSLC / map CCRDSLA - View Credit Card (transaction CCDL).
 *
 * <p>Both keys are part of the search condition, and both are edited on the server where
 * 2210-EDIT-ACCOUNT and 2220-EDIT-CARD edited them, so the screen shows the message the source
 * produced rather than guessing at one.
 */
export function CardViewScreen({
  accountId,
  cardNumber,
  onExit,
}: {
  accountId?: string | null;
  cardNumber?: string | null;
  onExit: () => void;
}) {
  const [keyedAccount, setKeyedAccount] = useState(accountId ?? '');
  const [keyedCard, setKeyedCard] = useState(cardNumber ?? '');
  const [card, setCard] = useState<CardDetailResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function load(searchAccount: string, searchCard: string) {
    setError(null);
    try {
      setCard(await fetchCard(searchAccount, searchCard));
    } catch (failure: unknown) {
      setCard(null);
      setError(failure instanceof ApiError ? failure.message : 'Error reading Card Data File');
    }
  }

  useEffect(() => {
    if (accountId && cardNumber) {
      void load(accountId, cardNumber);
    }
    // A card chosen on the list screen arrives with both keys already known.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountId, cardNumber]);

  return (
    <ScreenFrame transaction="CCDL" program="COCRDSLC" title="View Credit Card">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void load(keyedAccount.trim(), keyedCard.trim());
        }}
      >
        <div className="field">
          <label htmlFor="viewAccountId">Account Number</label>
          <input
            id="viewAccountId"
            aria-label="Account Number"
            value={keyedAccount}
            maxLength={11}
            size={11}
            onChange={(event) => setKeyedAccount(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="viewCardNumber">Card Number</label>
          <div className="field__group">
            <input
              id="viewCardNumber"
              aria-label="Card Number"
              value={keyedCard}
              maxLength={16}
              size={16}
              onChange={(event) => setKeyedCard(event.target.value)}
            />
            <button type="submit" className="primary">
              Search
            </button>
          </div>
        </div>
      </form>

      {card ? (
        <table className="details">
          <tbody>
            {(
              [
                ['Card Number', card.cardNumber],
                ['Account Number', card.accountId],
                ['Name on Card', card.embossedName],
                ['Active', card.activeStatus],
                ['Expiry', `${card.expiryMonth}/${card.expiryYear}`],
                ['CVV', card.cvvCode],
              ] as [string, string][]
            ).map(([label, value]) => (
              <tr key={label}>
                <th scope="row">{label}</th>
                <td>{value}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}

      <div className="field__group">
        <button type="button" onClick={onExit}>
          Back
        </button>
      </div>

      <Messages info={card?.infoMessage} error={error} />
    </ScreenFrame>
  );
}
