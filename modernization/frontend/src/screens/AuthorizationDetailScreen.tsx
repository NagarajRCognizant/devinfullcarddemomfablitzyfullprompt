import { useCallback, useEffect, useState } from 'react';
import {
  ApiError,
  fetchAuthorizationDetail,
  toggleAuthorizationFraud,
} from '../api/client';
import type { AuthorizationDetailResponse } from '../api/types';
import { Messages } from '../components/Messages';
import { ScreenFrame } from '../components/ScreenFrame';

/** COPAUS1C PROCESS-PF8-KEY when the chain has no further authorization. */
const LAST_AUTHORIZATION = 'Already at the last Authorization...';

/**
 * COPAUS1C / map COPAU01 - View Pending Authorization Details (transaction CPVD).
 *
 * <p>The three PF keys of the map become explicit actions: PF3 is Back, PF8 is Next authorization
 * (the server returns the key it would have read), and PF5 is a POST that toggles the fraud flag.
 * The fraud rule itself is untouched - a confirmed authorization is removed and anything else is
 * marked - and the AUTH MARKED / AUTH FRAUD REMOVED messages come back from the service.
 */
export function AuthorizationDetailScreen({
  accountId,
  authKey,
  onExit,
}: {
  accountId: number;
  authKey: string;
  onExit: () => void;
}) {
  const [detail, setDetail] = useState<AuthorizationDetailResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  const load = useCallback(async (key: string) => {
    setError(null);
    setInfo(null);
    try {
      setDetail(await fetchAuthorizationDetail(accountId, key, 'S'));
    } catch (failure: unknown) {
      setError(
        failure instanceof ApiError ? failure.message : 'Unable to read the authorization',
      );
    }
  }, [accountId]);

  useEffect(() => {
    void load(authKey);
  }, [authKey, load]);

  /** PROCESS-PF8-KEY: the next child of the same root, or the end of chain message. */
  function next() {
    if (!detail?.nextAuthKey) {
      setInfo(LAST_AUTHORIZATION);
      return;
    }
    void load(detail.nextAuthKey);
  }

  /** PROCESS-PF5-KEY / MARK-AUTH-FRAUD, with the DB2 fraud report row written by the service. */
  async function toggleFraud() {
    if (!detail) {
      return;
    }
    setError(null);
    setInfo(null);
    try {
      setDetail(await toggleAuthorizationFraud(accountId, detail.authKey));
    } catch (failure: unknown) {
      setError(
        failure instanceof ApiError ? failure.message : 'Unable to update the fraud report',
      );
    }
  }

  return (
    <ScreenFrame transaction="CPVD" program="COPAUS1C" title="View Pending Authorization Details">
      {detail ? (
        <dl className="summary">
          <dt>Account ID</dt>
          <dd>{detail.accountId}</dd>
          <dt>Card Number</dt>
          <dd>{detail.cardNumber}</dd>
          <dt>Auth Date / Time</dt>
          <dd>
            {detail.authorizationDate} {detail.authorizationTime}
          </dd>
          <dt>Auth Type</dt>
          <dd>{detail.authorizationType}</dd>
          <dt>Approved / Declined</dt>
          <dd>{detail.approvalStatus}</dd>
          <dt>Response Code</dt>
          <dd>{detail.responseCode}</dd>
          <dt>Reason</dt>
          <dd>{detail.responseReason}</dd>
          <dt>Approved Amount</dt>
          <dd>{detail.approvedAmount.display}</dd>
          <dt>Transaction Amount</dt>
          <dd>{detail.transactionAmount.display}</dd>
          <dt>Card Expiry</dt>
          <dd>{detail.cardExpiryDate}</dd>
          <dt>Processing Code</dt>
          <dd>{detail.processingCode}</dd>
          <dt>POS Entry Mode</dt>
          <dd>{detail.posEntryMode}</dd>
          <dt>Message Source</dt>
          <dd>{detail.messageSource}</dd>
          <dt>Merchant Category</dt>
          <dd>{detail.merchantCategoryCode}</dd>
          <dt>Transaction ID</dt>
          <dd>{detail.transactionId}</dd>
          <dt>Match Status</dt>
          <dd>{detail.matchStatus}</dd>
          <dt>Fraud</dt>
          <dd>{detail.fraudStatus}</dd>
          <dt>Merchant</dt>
          <dd>
            {detail.merchantId} {detail.merchantName}
          </dd>
          <dt>Merchant Location</dt>
          <dd>
            {detail.merchantCity} {detail.merchantState} {detail.merchantZip}
          </dd>
        </dl>
      ) : null}

      <div className="field__group">
        <button type="button" onClick={onExit}>
          Back to summary
        </button>
        <button type="button" onClick={next}>
          Next authorization
        </button>
        <button type="button" className="primary" onClick={() => void toggleFraud()}>
          Toggle fraud
        </button>
      </div>

      <Messages info={info ?? detail?.message} error={error} />
    </ScreenFrame>
  );
}
