/** Mirrors of the DTOs of the account service and the identity service. */

export type FieldFlag = 'VALID' | 'NOT_OK' | 'BLANK';

export type AccountUpdateStatus =
  | 'DETAILS_FETCHED'
  | 'NO_CHANGES'
  | 'VALIDATION_ERROR'
  | 'CHANGES_VALIDATED'
  | 'CHANGES_COMMITTED'
  | 'RECORD_CHANGED'
  | 'LOCK_ERROR'
  | 'UPDATE_FAILED';

export interface MoneyValue {
  amount: number;
  display: string;
}

export interface AccountDetails {
  accountId: string;
  activeStatus: string;
  currentBalance: MoneyValue;
  creditLimit: MoneyValue;
  cashCreditLimit: MoneyValue;
  currentCycleCredit: MoneyValue;
  currentCycleDebit: MoneyValue;
  openDate: string;
  expirationDate: string;
  reissueDate: string;
  groupId: string;
  customerId: string;
  cardNumber: string;
  ssn: string;
  ssnFormatted: string;
  ficoScore: number | null;
  dateOfBirth: string;
  firstName: string;
  middleName: string;
  lastName: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  zip: string;
  country: string;
  phone1: string;
  phone2: string;
  governmentIssuedId: string;
  eftAccountId: string;
  primaryCardHolder: string;
}

export interface AccountViewResponse {
  details: AccountDetails | null;
  infoMessage: string | null;
}

/** The keyed characters of every input field of the COACTUP map. */
export interface AccountUpdateForm {
  accountId: string;
  activeStatus: string;
  currentBalance: string;
  creditLimit: string;
  cashCreditLimit: string;
  openYear: string;
  openMonth: string;
  openDay: string;
  expiryYear: string;
  expiryMonth: string;
  expiryDay: string;
  reissueYear: string;
  reissueMonth: string;
  reissueDay: string;
  currentCycleCredit: string;
  currentCycleDebit: string;
  groupId: string;
  customerId: string;
  ssnPart1: string;
  ssnPart2: string;
  ssnPart3: string;
  dobYear: string;
  dobMonth: string;
  dobDay: string;
  ficoScore: string;
  firstName: string;
  middleName: string;
  lastName: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  zip: string;
  country: string;
  phone1Area: string;
  phone1Prefix: string;
  phone1Line: string;
  phone2Area: string;
  phone2Prefix: string;
  phone2Line: string;
  governmentIssuedId: string;
  eftAccountId: string;
  primaryCardHolder: string;
}

export interface AccountUpdateResponse {
  status: AccountUpdateStatus;
  infoMessage: string | null;
  errorMessage: string | null;
  original: AccountUpdateForm | null;
  updated: AccountUpdateForm | null;
  details: AccountDetails | null;
  fieldFlags: Partial<Record<keyof AccountUpdateForm, FieldFlag>>;
}

export interface MenuOption {
  number: number;
  name: string;
  program: string;
  userType: string;
  accessible: boolean;
}

export interface MenuResponse {
  userId: string;
  userType: string;
  userTypeName: string;
  options: MenuOption[];
}

/** Body of every non-2xx response of the API. */
export interface ErrorResponse {
  status: string;
  message: string;
  /** The map fields the screen would highlight, keyed by field name. */
  fieldFlags: Record<string, FieldFlag | undefined>;
}

/** SEC-USR-TYPE of app/cpy/CSUSR01Y.cpy. */
export type UserType = 'A' | 'U';

/** FIRST is the ENTER key on the filter, NEXT is PF8 and PREVIOUS is PF7. */
export type BrowseDirection = 'FIRST' | 'NEXT' | 'PREVIOUS';

export interface SignOnResponse {
  userId: string;
  firstName: string;
  lastName: string;
  userType: UserType;
  /** The program COSGN00C transferred to: COADM01C for an administrator, COMEN01C otherwise. */
  nextScreen: 'COADM01C' | 'COMEN01C';
  accessToken: string;
  expiresInSeconds: number;
}

/**
 * The same COMMAREA fields for a caller the realm has already signed on; no token, because the token
 * is the one the realm issued to this page.
 */
export interface CurrentUserResponse {
  userId: string;
  firstName: string;
  lastName: string;
  userType: UserType;
  nextScreen: 'COADM01C' | 'COMEN01C';
}

export interface MenuOptionResponse {
  optionNumber: number;
  name: string;
  program: string;
}

export interface UserSummary {
  userId: string;
  firstName: string;
  lastName: string;
  userType: UserType;
}

export interface UserListResponse {
  users: UserSummary[];
  firstUserId: string | null;
  lastUserId: string | null;
  pageNumber: number;
  nextPageAvailable: boolean;
  message: string | null;
}

export interface UserDetail {
  userId: string;
  firstName: string;
  lastName: string;
  password: string;
  userType: UserType;
}

export interface UserAddForm {
  firstName: string;
  lastName: string;
  userId: string;
  password: string;
  userType: string;
}

export interface UserUpdateForm {
  firstName: string;
  lastName: string;
  password: string;
  userType: string;
}

export interface UserActionResponse {
  message: string;
  user: UserDetail | null;
}

/** Mirrors of the DTOs of the authorization service (transactions CPVS and CPVD). */

/** One of the five detail lines of map COPAU0A. */
export interface AuthorizationListRow {
  authKey: string;
  transactionId: string;
  authorizationDate: string;
  authorizationTime: string;
  authorizationType: string;
  approvalStatus: string;
  matchStatus: string;
  approvedAmount: MoneyValue;
}

/**
 * Map COPAU0A. `lastAuthKey` and `morePages` are the paging cursor that replaces the COMMAREA
 * array of page start keys COPAUS0C carried between PF7 and PF8 presses.
 */
export interface AuthorizationSummaryResponse {
  accountId: number;
  customerId: number | null;
  customerName: string;
  addressLine1: string;
  addressLine2: string;
  phone: string;
  creditLimit: MoneyValue;
  cashCreditLimit: MoneyValue;
  approvedCount: number;
  declinedCount: number;
  creditBalance: MoneyValue;
  cashBalance: MoneyValue;
  approvedAmount: MoneyValue;
  declinedAmount: MoneyValue;
  summaryFound: boolean;
  authorizations: AuthorizationListRow[];
  pageStartAuthKey: string | null;
  lastAuthKey: string | null;
  morePages: boolean;
  message: string | null;
}

/** Map COPAU1A, with the key PF8 would have read next. */
export interface AuthorizationDetailResponse {
  accountId: number;
  authKey: string;
  cardNumber: string;
  authorizationDate: string;
  authorizationTime: string;
  approvedAmount: MoneyValue;
  transactionAmount: MoneyValue;
  approvalStatus: string;
  responseCode: string;
  responseReason: string;
  processingCode: string;
  posEntryMode: string;
  messageSource: string;
  merchantCategoryCode: string;
  cardExpiryDate: string;
  authorizationType: string;
  transactionId: string;
  matchStatus: string;
  fraudStatus: string;
  merchantName: string;
  merchantId: string;
  merchantCity: string;
  merchantState: string;
  merchantZip: string;
  nextAuthKey: string | null;
  message: string | null;
}

/** One of the ten lines of the COTRN00C list, as POPULATE-TRAN-DATA fills it. */
export interface TransactionListRow {
  tranId: string;
  date: string;
  description: string;
  amount: MoneyValue;
}

/**
 * Map COTRN0A. The two boundary identifiers and the next-page flag are CDEMO-CT00-TRNID-FIRST,
 * CDEMO-CT00-TRNID-LAST and CDEMO-CT00-NEXT-PAGE-FLG, which the COMMAREA carried between the
 * pseudo-conversational turns of PF7 and PF8.
 */
export interface TransactionListResponse {
  rows: TransactionListRow[];
  pageNumber: number;
  firstTranId: string | null;
  lastTranId: string | null;
  nextPageAvailable: boolean;
  message: string | null;
}

/** Map COTRN1A: the TRANSACT record as COTRN01C displays it. */
export interface TransactionDetailResponse {
  tranId: string;
  cardNumber: string;
  typeCode: string;
  categoryCode: string;
  source: string;
  description: string;
  amount: MoneyValue;
  originalTimestamp: string;
  processedTimestamp: string;
  merchantId: string;
  merchantName: string;
  merchantCity: string;
  merchantZip: string;
}

/** Map COTRN2A as it is keyed. Every field is text because COTRN02C edits the keyed characters. */
export interface TransactionAddForm {
  accountId: string;
  cardNumber: string;
  typeCode: string;
  categoryCode: string;
  source: string;
  description: string;
  amount: string;
  originalDate: string;
  processedDate: string;
  merchantId: string;
  merchantName: string;
  merchantCity: string;
  merchantZip: string;
  confirm: string;
}

/** The outcome of the add, with the key fields the cross reference filled in. */
export interface TransactionAddResponse {
  tranId: string | null;
  accountId: string;
  cardNumber: string;
  message: string;
}

/** Map COBIL0A: the balance on display and the transaction a confirmed payment wrote. */
export interface BillPaymentResponse {
  accountId: string;
  currentBalance: MoneyValue;
  tranId: string | null;
  paid: boolean;
  message: string | null;
}

/** Map CORPT0A as it is keyed: one report type and the two date triples of the custom range. */
export interface ReportRequestForm {
  reportType: string;
  startMonth: string;
  startDay: string;
  startYear: string;
  endMonth: string;
  endDay: string;
  endYear: string;
  confirm: string;
}

/** The report request that replaces the job CORPT00C wrote to the internal reader. */
export interface ReportRequestResponse {
  requestId: number | null;
  reportName: string;
  startDate: string;
  endDate: string;
  submitted: boolean;
  message: string | null;
}

/** One row of map CCRDLIA: the card list of COCRDLIC shows seven of these. */
export interface CardListRow {
  cardNumber: string;
  accountId: string;
  activeStatus: string;
}

/** Map CCRDLIA with the page keys the COMMAREA carried so PF7 and PF8 can restart the browse. */
export interface CardListResponse {
  rows: CardListRow[];
  pageNumber: number;
  firstCardNumber: string | null;
  lastCardNumber: string | null;
  nextPageAvailable: boolean;
  errorMessage: string | null;
  infoMessage: string | null;
}

/** Map CCRDSLA, and the fetched values map CCRDUPA starts from. */
export interface CardDetailResponse {
  accountId: string;
  cardNumber: string;
  embossedName: string;
  activeStatus: string;
  expiryYear: string;
  expiryMonth: string;
  expiryDay: string;
  cvvCode: string;
  infoMessage: string | null;
}

/** Map CCRDUPA as it is keyed, with the record the previous turn displayed. */
export interface CardUpdateForm {
  accountId: string;
  cardNumber: string;
  embossedName: string;
  activeStatus: string;
  expiryYear: string;
  expiryMonth: string;
  fetched: CardDetailResponse | null;
}

/** The outcome of one COCRDUPC turn. */
export interface CardUpdateResponse {
  card: CardDetailResponse;
  updated: boolean;
  message: string | null;
}
