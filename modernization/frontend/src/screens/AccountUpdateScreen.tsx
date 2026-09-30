import { useState } from 'react';
import { ApiError, fetchAccountForUpdate, submitUpdate } from '../api/client';
import type { AccountUpdateForm, AccountUpdateResponse, FieldFlag } from '../api/types';
import { Field, FieldRow } from '../components/Field';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

type Flags = Partial<Record<keyof AccountUpdateForm, FieldFlag>>;

/**
 * COACTUPC / map COACTUP - Account Update.
 *
 * <p>The pseudo conversational turns of the source become three API calls: fetch (first ENTER),
 * validate (the ENTER that runs the edits) and confirm (F5). The ACUP-OLD-DETAILS / ACUP-NEW-DETAILS
 * pair the COMMAREA carried between turns is held in component state and sent on every submit, so
 * the change detection and the record-changed check still compare the fetched snapshot with both the
 * keyed values and the stored record. F12 re-fetches the record, which is the source's cancel.
 */
export function AccountUpdateScreen({ onExit }: { onExit: () => void }) {
  const [accountId, setAccountId] = useState('');
  const [original, setOriginal] = useState<AccountUpdateForm | null>(null);
  const [form, setForm] = useState<AccountUpdateForm | null>(null);
  const [flags, setFlags] = useState<Flags>({});
  const [info, setInfo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [validated, setValidated] = useState(false);

  function apply(response: AccountUpdateResponse) {
    setOriginal(response.original);
    setForm(response.updated);
    setFlags(response.fieldFlags ?? {});
    setInfo(response.infoMessage);
    setError(response.errorMessage);
    setValidated(response.status === 'CHANGES_VALIDATED');
  }

  function fail(failure: unknown) {
    if (failure instanceof ApiError) {
      setError(failure.body.message);
      setFlags(failure.body.fieldFlags ?? {});
    } else {
      setError(String(failure));
    }
    setValidated(false);
  }

  async function fetchAccount(id: string) {
    setError(null);
    try {
      apply(await fetchAccountForUpdate(id));
    } catch (failure: unknown) {
      setOriginal(null);
      setForm(null);
      setInfo(null);
      fail(failure);
    }
  }

  async function submit(action: 'validate' | 'confirm') {
    if (!original || !form) {
      return;
    }
    try {
      apply(await submitUpdate(form.accountId, action, original, form));
    } catch (failure: unknown) {
      fail(failure);
    }
  }

  const edit = (name: keyof AccountUpdateForm) => (value: string) =>
    setForm((current) => (current ? { ...current, [name]: value } : current));

  const input = (
    label: string,
    name: keyof AccountUpdateForm,
    maxLength: number,
    size?: number,
  ) => (
    <Field
      label={label}
      name={name}
      value={form?.[name] ?? ''}
      flag={flags[name]}
      maxLength={maxLength}
      size={size}
      onChange={edit(name)}
    />
  );

  return (
    <ScreenFrame transaction="CAUP" program="COACTUPC" title="Update Account">
      <FieldRow label="Account Number" htmlFor="accountIdFilter">
        <Field
          label="Account Number"
          name="accountIdFilter"
          value={form ? form.accountId : accountId}
          maxLength={11}
          flag={flags.accountId}
          readOnly={Boolean(form)}
          onChange={setAccountId}
        />
        {form ? null : (
          <button type="button" className="primary" onClick={() => fetchAccount(accountId)}>
            Fetch
          </button>
        )}
      </FieldRow>

      <Messages info={info} error={error} />

      {form ? (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void submit('validate');
          }}
        >
          <fieldset>
            <legend>Account Details</legend>
            <FieldRow label="Active" htmlFor="activeStatus">
              {input('Active', 'activeStatus', 1)}
            </FieldRow>
            <FieldRow label="Opened (yyyy-mm-dd)">
              {input('Open year', 'openYear', 4)}
              {input('Open month', 'openMonth', 2)}
              {input('Open day', 'openDay', 2)}
            </FieldRow>
            <FieldRow label="Expiry (yyyy-mm-dd)">
              {input('Expiry year', 'expiryYear', 4)}
              {input('Expiry month', 'expiryMonth', 2)}
              {input('Expiry day', 'expiryDay', 2)}
            </FieldRow>
            <FieldRow label="Reissue (yyyy-mm-dd)">
              {input('Reissue year', 'reissueYear', 4)}
              {input('Reissue month', 'reissueMonth', 2)}
              {input('Reissue day', 'reissueDay', 2)}
            </FieldRow>
            <FieldRow label="Credit Limit" htmlFor="creditLimit">
              {input('Credit Limit', 'creditLimit', 15)}
            </FieldRow>
            <FieldRow label="Cash Credit Limit" htmlFor="cashCreditLimit">
              {input('Cash Credit Limit', 'cashCreditLimit', 15)}
            </FieldRow>
            <FieldRow label="Current Balance" htmlFor="currentBalance">
              {input('Current Balance', 'currentBalance', 15)}
            </FieldRow>
            <FieldRow label="Current Cycle Credit" htmlFor="currentCycleCredit">
              {input('Current Cycle Credit', 'currentCycleCredit', 15)}
            </FieldRow>
            <FieldRow label="Current Cycle Debit" htmlFor="currentCycleDebit">
              {input('Current Cycle Debit', 'currentCycleDebit', 15)}
            </FieldRow>
            <FieldRow label="Account Group" htmlFor="groupId">
              {input('Account Group', 'groupId', 10)}
            </FieldRow>
          </fieldset>

          <fieldset>
            <legend>Customer Details</legend>
            <FieldRow label="Customer id" htmlFor="customerId">
              <Field
                label="Customer id"
                name="customerId"
                value={form.customerId}
                flag={flags.customerId}
                readOnly
                maxLength={9}
              />
            </FieldRow>
            <FieldRow label="SSN">
              {input('SSN part 1', 'ssnPart1', 3)}
              {input('SSN part 2', 'ssnPart2', 2)}
              {input('SSN part 3', 'ssnPart3', 4)}
            </FieldRow>
            <FieldRow label="Date of birth (yyyy-mm-dd)">
              {input('Date of birth year', 'dobYear', 4)}
              {input('Date of birth month', 'dobMonth', 2)}
              {input('Date of birth day', 'dobDay', 2)}
            </FieldRow>
            <FieldRow label="FICO Score" htmlFor="ficoScore">
              {input('FICO Score', 'ficoScore', 3)}
            </FieldRow>
            <FieldRow label="First Name" htmlFor="firstName">
              {input('First Name', 'firstName', 25)}
            </FieldRow>
            <FieldRow label="Middle Name" htmlFor="middleName">
              {input('Middle Name', 'middleName', 25)}
            </FieldRow>
            <FieldRow label="Last Name" htmlFor="lastName">
              {input('Last Name', 'lastName', 25)}
            </FieldRow>
            <FieldRow label="Address Line 1" htmlFor="addressLine1">
              {input('Address Line 1', 'addressLine1', 50)}
            </FieldRow>
            <FieldRow label="Address Line 2" htmlFor="addressLine2">
              {input('Address Line 2', 'addressLine2', 50)}
            </FieldRow>
            <FieldRow label="City" htmlFor="city">
              {input('City', 'city', 50)}
            </FieldRow>
            <FieldRow label="State" htmlFor="state">
              {input('State', 'state', 2)}
            </FieldRow>
            <FieldRow label="Zip" htmlFor="zip">
              {input('Zip', 'zip', 10)}
            </FieldRow>
            <FieldRow label="Country" htmlFor="country">
              {input('Country', 'country', 3)}
            </FieldRow>
            <FieldRow label="Phone 1">
              {input('Phone 1 area code', 'phone1Area', 3)}
              {input('Phone 1 prefix', 'phone1Prefix', 3)}
              {input('Phone 1 line number', 'phone1Line', 4)}
            </FieldRow>
            <FieldRow label="Phone 2">
              {input('Phone 2 area code', 'phone2Area', 3)}
              {input('Phone 2 prefix', 'phone2Prefix', 3)}
              {input('Phone 2 line number', 'phone2Line', 4)}
            </FieldRow>
            <FieldRow label="Government Issued Id" htmlFor="governmentIssuedId">
              {input('Government Issued Id', 'governmentIssuedId', 20)}
            </FieldRow>
            <FieldRow label="EFT Account Id" htmlFor="eftAccountId">
              {input('EFT Account Id', 'eftAccountId', 10)}
            </FieldRow>
            <FieldRow label="Primary Card Holder" htmlFor="primaryCardHolder">
              {input('Primary Card Holder', 'primaryCardHolder', 1)}
            </FieldRow>
          </fieldset>

          <nav className="screen__nav" aria-label="Screen actions">
            <button type="submit" className="primary">
              Process (ENTER)
            </button>
            <button
              type="button"
              className="primary"
              disabled={!validated}
              onClick={() => void submit('confirm')}
            >
              Save (F5)
            </button>
            <button type="button" onClick={() => void fetchAccount(form.accountId)}>
              Cancel (F12)
            </button>
            <button type="button" onClick={onExit}>
              Exit (F3)
            </button>
          </nav>
        </form>
      ) : (
        <nav className="screen__nav" aria-label="Screen actions">
          <button type="button" onClick={onExit}>
            Exit (F3)
          </button>
        </nav>
      )}
    </ScreenFrame>
  );
}
