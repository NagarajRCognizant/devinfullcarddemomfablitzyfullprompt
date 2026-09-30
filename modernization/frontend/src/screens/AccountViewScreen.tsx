import { useEffect, useState } from 'react';
import { ApiError, fetchViewPrompt, viewAccount } from '../api/client';
import type { AccountDetails, FieldFlag } from '../api/types';
import { Field, FieldRow } from '../components/Field';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

/**
 * COACTVWC / map COACTVW - Account View.
 *
 * <p>Every field of the map except the account filter is protected in the source, so the details
 * are rendered as a read-only table. "Search" is the ENTER key of the map and "Exit" is F3; the
 * terminal transfer back to the menu becomes an in-page navigation callback.
 */
export function AccountViewScreen({ onExit }: { onExit: () => void }) {
  const [accountId, setAccountId] = useState('');
  const [details, setDetails] = useState<AccountDetails | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [filterFlag, setFilterFlag] = useState<FieldFlag | undefined>(undefined);

  useEffect(() => {
    fetchViewPrompt()
      .then((response) => setInfo(response.infoMessage))
      .catch((failure: unknown) => setError(describe(failure)));
  }, []);

  async function search() {
    setError(null);
    setFilterFlag(undefined);
    try {
      const response = await viewAccount(accountId);
      setDetails(response.details);
      setInfo(response.infoMessage);
    } catch (failure: unknown) {
      setDetails(null);
      setInfo(null);
      setError(describe(failure));
      // A blank filter is flagged BLANK and a rejected or missing account NOT_OK, which is what
      // 3300-SETUP-SCREEN-ATTRS distinguishes with the asterisk.
      setFilterFlag(failure instanceof ApiError ? failure.body.fieldFlags?.accountId : undefined);
    }
  }

  return (
    <ScreenFrame transaction="CAVW" program="COACTVWC" title="View Account">
      <FieldRow label="Account Number" htmlFor="accountId">
        <Field
          label="Account Number"
          name="accountId"
          value={accountId}
          maxLength={11}
          flag={filterFlag}
          onChange={setAccountId}
        />
        <button type="button" className="primary" onClick={search}>
          Search
        </button>
      </FieldRow>

      <Messages info={info} error={error} />

      {details ? <AccountDetailsTable details={details} /> : null}

      <nav className="screen__nav" aria-label="Screen actions">
        <button type="button" onClick={onExit}>
          Exit (F3)
        </button>
      </nav>
    </ScreenFrame>
  );
}

function AccountDetailsTable({ details }: { details: AccountDetails }) {
  const rows: [string, string][] = [
    ['Active', details.activeStatus],
    ['Opened', details.openDate],
    ['Expiry', details.expirationDate],
    ['Reissue', details.reissueDate],
    ['Credit Limit', details.creditLimit.display],
    ['Cash Credit Limit', details.cashCreditLimit.display],
    ['Current Balance', details.currentBalance.display],
    ['Current Cycle Credit', details.currentCycleCredit.display],
    ['Current Cycle Debit', details.currentCycleDebit.display],
    ['Account Group', details.groupId],
    ['Customer id', details.customerId],
    ['Card Number', details.cardNumber],
    ['SSN', details.ssnFormatted],
    ['Date of birth', details.dateOfBirth],
    ['FICO Score', details.ficoScore === null ? '' : String(details.ficoScore)],
    ['First Name', details.firstName],
    ['Middle Name', details.middleName],
    ['Last Name', details.lastName],
    ['Address Line 1', details.addressLine1],
    ['Address Line 2', details.addressLine2],
    ['City', details.city],
    ['State', details.state],
    ['Zip', details.zip],
    ['Country', details.country],
    ['Phone 1', details.phone1],
    ['Phone 2', details.phone2],
    ['Government Issued Id', details.governmentIssuedId],
    ['EFT Account Id', details.eftAccountId],
    ['Primary Card Holder', details.primaryCardHolder],
  ];
  return (
    <table className="details">
      <caption style={{ textAlign: 'left', padding: '0.35rem 0' }}>
        Account {details.accountId}
      </caption>
      <tbody>
        {rows.map(([label, value]) => (
          <tr key={label}>
            <th scope="row">{label}</th>
            <td>{value}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function describe(failure: unknown): string {
  return failure instanceof ApiError ? failure.body.message : String(failure);
}
