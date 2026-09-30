import type {
  AccountUpdateForm,
  AccountUpdateResponse,
  AccountViewResponse,
  AuthorizationDetailResponse,
  AuthorizationSummaryResponse,
  BillPaymentResponse,
  BrowseDirection,
  CardDetailResponse,
  CardListResponse,
  CardUpdateForm,
  CardUpdateResponse,
  CurrentUserResponse,
  ErrorResponse,
  MenuOptionResponse,
  MenuResponse,
  ReportRequestForm,
  ReportRequestResponse,
  TransactionAddForm,
  TransactionAddResponse,
  TransactionDetailResponse,
  TransactionListResponse,
  UserActionResponse,
  UserAddForm,
  UserDetail,
  UserListResponse,
  UserUpdateForm,
} from './types';

/**
 * Thin fetch wrapper over the two services, addressed through the gateway.
 *
 * <p>The validation, not-found and conflict responses carry the same message and field-flag shape
 * the BMS maps showed, so they are surfaced as data rather than thrown away: an {@link ApiError}
 * keeps both so the screens can redisplay the message line and highlight the offending fields
 * exactly as the source programs did.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: ErrorResponse,
  ) {
    super(body?.message ?? `Request failed with status ${status}`);
  }
}

/**
 * Where the bearer token of the operator comes from, asked once per request.
 *
 * <p>It replaces the CDEMO-USER-ID and CDEMO-USER-TYPE fields of the COMMAREA, which CICS carried
 * from screen to screen. This module never mints or reads it: the realm signs it, the services verify
 * it, and the auth module renews it, so nothing here has to know how a token is made.
 *
 * <p>A token is asked for rather than held, because the auth module renews it while the page stays
 * open: a token read once would be sent until the operator reloaded, and refused as expired long
 * before that.
 */
type AccessTokens = () => string | null | Promise<string | null>;

let accessTokens: AccessTokens = () => null;

export function setAccessToken(token: string | null): void {
  accessTokens = () => token;
}

/** The renewing source of the token, which is the session of the realm. */
export function setAccessTokenSource(tokens: AccessTokens): void {
  accessTokens = tokens;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = await accessTokens();
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new ApiError(response.status, body as ErrorResponse);
  }
  return body as T;
}

/**
 * What is left of COSGN00C: the routing decision, for a caller the realm has already authenticated
 * with a password and a TOTP. There is no endpoint to post credentials to any more.
 */
export function fetchCurrentUser(): Promise<CurrentUserResponse> {
  return request<CurrentUserResponse>('/api/me');
}

/** COMEN01C: the options the signed-on user type may select. */
export function fetchMenu(): Promise<MenuResponse> {
  return request<MenuResponse>('/api/menu');
}

/** COADM01C and its option table in app/cpy/COADM02Y.cpy. */
export function fetchAdminMenu(): Promise<MenuOptionResponse[]> {
  return request<MenuOptionResponse[]>('/api/admin-menu');
}

export function selectAdminMenuOption(option: string): Promise<MenuOptionResponse> {
  const query = new URLSearchParams({ option });
  return request<MenuOptionResponse>(`/api/admin-menu/selection?${query.toString()}`);
}

/** COUSR00C: one page of the keyed browse. FIRST is ENTER, NEXT is PF8 and PREVIOUS is PF7. */
export function listUsers(
  direction: BrowseDirection,
  userId: string,
  pageNumber: number,
): Promise<UserListResponse> {
  const query = new URLSearchParams({
    direction,
    userId,
    pageNumber: String(pageNumber),
  });
  return request<UserListResponse>(`/api/users?${query.toString()}`);
}

export function fetchUser(userId: string): Promise<UserDetail> {
  return request<UserDetail>(`/api/users/${encodeURIComponent(userId)}`);
}

/** COUSR01C. */
export function addUser(form: UserAddForm): Promise<UserActionResponse> {
  return request<UserActionResponse>('/api/users', {
    method: 'POST',
    body: JSON.stringify(form),
  });
}

/** COUSR02C. */
export function updateUser(userId: string, form: UserUpdateForm): Promise<UserActionResponse> {
  return request<UserActionResponse>(`/api/users/${encodeURIComponent(userId)}`, {
    method: 'PUT',
    body: JSON.stringify(form),
  });
}

/** COUSR03C. */
export function deleteUser(userId: string): Promise<UserActionResponse> {
  return request<UserActionResponse>(`/api/users/${encodeURIComponent(userId)}`, {
    method: 'DELETE',
  });
}

export function fetchViewPrompt(): Promise<AccountViewResponse> {
  return request<AccountViewResponse>('/api/accounts/view');
}

export function viewAccount(accountId: string): Promise<AccountViewResponse> {
  // The search operation carries the filter as keyed - including blank - so the account id edit
  // runs on the server for every value the map could send.
  const query = new URLSearchParams({ accountId });
  return request<AccountViewResponse>(`/api/accounts/search?${query.toString()}`);
}

export function fetchAccountForUpdate(accountId: string): Promise<AccountUpdateResponse> {
  const query = new URLSearchParams({ accountId });
  return request<AccountUpdateResponse>(`/api/accounts/update?${query.toString()}`);
}

export function submitUpdate(
  accountId: string,
  action: 'validate' | 'confirm',
  original: AccountUpdateForm,
  updated: AccountUpdateForm,
): Promise<AccountUpdateResponse> {
  return request<AccountUpdateResponse>(
    `/api/accounts/${encodeURIComponent(accountId)}/update/${action}`,
    { method: 'POST', body: JSON.stringify({ original, updated }) },
  );
}

/**
 * CPVS / COPAUS0C: one page of five authorizations for an account.
 *
 * <p>The account id is sent as keyed text, including blank, so the two edits of PROCESS-ENTER-KEY
 * keep producing their own messages on the server. `afterAuthKey` is the key of the last row of
 * the previous page, which is what the PF8 path read forward from.
 */
export function fetchAuthorizationSummary(
  accountId: string,
  afterAuthKey?: string | null,
): Promise<AuthorizationSummaryResponse> {
  const query = new URLSearchParams({ accountId });
  if (afterAuthKey) {
    query.set('afterAuthKey', afterAuthKey);
  }
  return request<AuthorizationSummaryResponse>(`/api/authorizations?${query.toString()}`);
}

/** CPVD / COPAUS1C: the selected authorization. The selection character is still edited. */
export function fetchAuthorizationDetail(
  accountId: number,
  authKey: string,
  selection?: string,
): Promise<AuthorizationDetailResponse> {
  const query = new URLSearchParams();
  if (selection) {
    query.set('selection', selection);
  }
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return request<AuthorizationDetailResponse>(
    `/api/authorizations/${accountId}/${encodeURIComponent(authKey)}${suffix}`,
  );
}

/** PF5 of CPVD, as an explicit state change: confirmed becomes removed, anything else confirmed. */
export function toggleAuthorizationFraud(
  accountId: number,
  authKey: string,
): Promise<AuthorizationDetailResponse> {
  return request<AuthorizationDetailResponse>(
    `/api/authorizations/${accountId}/${encodeURIComponent(authKey)}/fraud`,
    { method: 'POST' },
  );
}

/**
 * CT00 / COTRN00C: one screenful of the list.
 *
 * <p>PF7 and PF8 are retired per Section 8.4.5: the page is asked for by the identifier at the
 * edge of the page on display, which is the browse position the COMMAREA carried.
 */
export function fetchTransactions(params: {
  tranId?: string;
  direction?: 'FIRST' | 'NEXT' | 'PREVIOUS';
  firstTranId?: string | null;
  lastTranId?: string | null;
  pageNumber?: number;
}): Promise<TransactionListResponse> {
  const query = new URLSearchParams();
  if (params.tranId) {
    query.set('tranId', params.tranId);
  }
  query.set('direction', params.direction ?? 'FIRST');
  if (params.firstTranId) {
    query.set('firstTranId', params.firstTranId);
  }
  if (params.lastTranId) {
    query.set('lastTranId', params.lastTranId);
  }
  query.set('pageNumber', String(params.pageNumber ?? 1));
  return request<TransactionListResponse>(`/api/transactions?${query.toString()}`);
}

/** CT01 / COTRN01C. The selection character of the list is edited on the server, as it was. */
export function fetchTransaction(
  tranId: string,
  selection?: string,
): Promise<TransactionDetailResponse> {
  const query = selection ? `?selection=${encodeURIComponent(selection)}` : '';
  return request<TransactionDetailResponse>(
    `/api/transactions/${encodeURIComponent(tranId)}${query}`,
  );
}

/** CT02 / COTRN02C: the two-step add, driven by the confirmation field. */
export function addTransaction(form: TransactionAddForm): Promise<TransactionAddResponse> {
  return request<TransactionAddResponse>('/api/transactions', {
    method: 'POST',
    body: JSON.stringify(form),
  });
}

/** PF5 of COTRN02C, which copied the last transaction of the account into the map. */
export function fetchLastTransaction(params: {
  accountId?: string;
  cardNumber?: string;
}): Promise<TransactionAddForm> {
  const query = new URLSearchParams();
  if (params.accountId) {
    query.set('accountId', params.accountId);
  }
  if (params.cardNumber) {
    query.set('cardNumber', params.cardNumber);
  }
  return request<TransactionAddForm>(`/api/transactions/last?${query.toString()}`);
}

/** CB00 / COBIL00C: the balance the screen offers to pay. */
export function fetchBillPaymentBalance(accountId: string): Promise<BillPaymentResponse> {
  return request<BillPaymentResponse>(
    `/api/bill-payments/${encodeURIComponent(accountId)}`,
  );
}

/** The confirmed payment. The key makes a resubmission of the same screen safe. */
export function payBill(
  accountId: string,
  confirm: string,
  idempotencyKey: string,
): Promise<BillPaymentResponse> {
  return request<BillPaymentResponse>('/api/bill-payments', {
    method: 'POST',
    body: JSON.stringify({ accountId, confirm, idempotencyKey }),
  });
}

/** CR00 / CORPT00C: the report request that replaces the submission to the internal reader. */
export function submitReportRequest(form: ReportRequestForm): Promise<ReportRequestResponse> {
  return request<ReportRequestResponse>('/api/transaction-reports', {
    method: 'POST',
    body: JSON.stringify(form),
  });
}

/** CCLI / COCRDLIC: one page of the card list, positioned by the keys of the page on display. */
export function fetchCards(params: {
  accountId?: string;
  cardNumber?: string;
  direction?: BrowseDirection;
  firstCardNumber?: string | null;
  lastCardNumber?: string | null;
  pageNumber?: number;
}): Promise<CardListResponse> {
  const query = new URLSearchParams();
  if (params.accountId) {
    query.set('accountId', params.accountId);
  }
  if (params.cardNumber) {
    query.set('cardNumber', params.cardNumber);
  }
  query.set('direction', params.direction ?? 'FIRST');
  if (params.firstCardNumber) {
    query.set('firstCardNumber', params.firstCardNumber);
  }
  if (params.lastCardNumber) {
    query.set('lastCardNumber', params.lastCardNumber);
  }
  query.set('pageNumber', String(params.pageNumber ?? 1));
  return request<CardListResponse>(`/api/cards?${query.toString()}`);
}

/** CCDL / COCRDSLC: the card detail, which is searched for by both keys. */
export function fetchCard(accountId: string, cardNumber: string): Promise<CardDetailResponse> {
  return request<CardDetailResponse>(
    `/api/cards/${encodeURIComponent(cardNumber)}?accountId=${encodeURIComponent(accountId)}`,
  );
}

/** CCUP / COCRDUPC, first turn: the record the map is filled from. */
export function fetchCardForUpdate(
  accountId: string,
  cardNumber: string,
): Promise<CardDetailResponse> {
  return request<CardDetailResponse>(
    `/api/cards/${encodeURIComponent(cardNumber)}/update?accountId=${encodeURIComponent(accountId)}`,
  );
}

/** The ENTER turn of COCRDUPC, which edits the changes and asks for the save confirmation. */
export function validateCardUpdate(form: CardUpdateForm): Promise<CardUpdateResponse> {
  return request<CardUpdateResponse>('/api/cards/validate', {
    method: 'POST',
    body: JSON.stringify(form),
  });
}

/** The PF5 turn of COCRDUPC, which rewrites the record. */
export function saveCardUpdate(form: CardUpdateForm): Promise<CardUpdateResponse> {
  return request<CardUpdateResponse>('/api/cards', {
    method: 'POST',
    body: JSON.stringify(form),
  });
}
