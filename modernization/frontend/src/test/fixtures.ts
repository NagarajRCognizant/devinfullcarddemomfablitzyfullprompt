import type { AccountDetails, AccountUpdateForm } from '../api/types';

const money = (display: string, amount: number) => ({ amount, display });

export const accountDetails: AccountDetails = {
  accountId: '00000000009',
  activeStatus: 'Y',
  currentBalance: money('+      1,025.50', 1025.5),
  creditLimit: money('+      5,000.00', 5000),
  cashCreditLimit: money('+      1,000.00', 1000),
  currentCycleCredit: money('+        120.00', 120),
  currentCycleDebit: money('+         80.00', 80),
  openDate: '2016-08-27',
  expirationDate: '2024-12-27',
  reissueDate: '2024-12-27',
  groupId: '',
  customerId: '000000009',
  cardNumber: '9680294154603697',
  ssn: '123456789',
  ssnFormatted: '123-45-6789',
  ficoScore: 699,
  dateOfBirth: '1975-11-07',
  firstName: 'Alice',
  middleName: 'B',
  lastName: 'Carter',
  addressLine1: '1 Main St',
  addressLine2: '',
  city: 'Raleigh',
  state: 'NC',
  zip: '27610',
  country: 'USA',
  phone1: '(201)456-1404',
  phone2: '(201)440-3130',
  governmentIssuedId: '00000000000568299451',
  eftAccountId: '0000000009',
  primaryCardHolder: 'Y',
};

export const updateForm: AccountUpdateForm = {
  accountId: '00000000009',
  activeStatus: 'Y',
  currentBalance: '1025.50',
  creditLimit: '5000.00',
  cashCreditLimit: '1000.00',
  openYear: '2016',
  openMonth: '08',
  openDay: '27',
  expiryYear: '2024',
  expiryMonth: '12',
  expiryDay: '27',
  reissueYear: '2024',
  reissueMonth: '12',
  reissueDay: '27',
  currentCycleCredit: '120.00',
  currentCycleDebit: '80.00',
  groupId: '',
  customerId: '000000009',
  ssnPart1: '123',
  ssnPart2: '45',
  ssnPart3: '6789',
  dobYear: '1975',
  dobMonth: '11',
  dobDay: '07',
  ficoScore: '699',
  firstName: 'Alice',
  middleName: 'B',
  lastName: 'Carter',
  addressLine1: '1 Main St',
  addressLine2: '',
  city: 'Raleigh',
  state: 'NC',
  zip: '27610',
  country: 'USA',
  phone1Area: '201',
  phone1Prefix: '456',
  phone1Line: '1404',
  phone2Area: '201',
  phone2Prefix: '440',
  phone2Line: '3130',
  governmentIssuedId: '00000000000568299451',
  eftAccountId: '0000000009',
  primaryCardHolder: 'Y',
};

/** Minimal fetch stub: maps "METHOD /path" to a status and a JSON body. */
export function stubFetch(routes: Record<string, { status?: number; body: unknown }>) {
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString();
    const key = `${init?.method ?? 'GET'} ${url.split('?')[0]}`;
    const route = routes[key];
    if (!route) {
      throw new Error(`unexpected request ${key}`);
    }
    return {
      ok: (route.status ?? 200) < 400,
      status: route.status ?? 200,
      text: async () => JSON.stringify(route.body),
    } as Response;
  });
}

export const adminMenuOptions = [
  { optionNumber: 1, name: 'User List (Security)', program: 'COUSR00C' },
  { optionNumber: 2, name: 'User Add (Security)', program: 'COUSR01C' },
  { optionNumber: 3, name: 'User Update (Security)', program: 'COUSR02C' },
  { optionNumber: 4, name: 'User Delete (Security)', program: 'COUSR03C' },
  { optionNumber: 5, name: 'Transaction Type List/Update (Db2)', program: 'COTRTLIC' },
  { optionNumber: 6, name: 'Transaction Type Maintenance (Db2)', program: 'COTRTUPC' },
];

export const userPage = {
  users: [
    { userId: 'ADMIN001', firstName: 'Admin', lastName: 'User', userType: 'A' },
    { userId: 'USER0002', firstName: 'Regular', lastName: 'User', userType: 'U' },
  ],
  firstUserId: 'ADMIN001',
  lastUserId: 'USER0002',
  pageNumber: 1,
  nextPageAvailable: true,
  message: null,
};

export const userDetail = {
  userId: 'ADMIN001',
  firstName: 'Admin',
  lastName: 'User',
  password: 'PASSWORD',
  userType: 'A',
};

export const regularUserMenu = {
  userId: 'USER0001',
  userType: 'U',
  userTypeName: 'Regular user',
  options: [
    {
      number: 1,
      name: 'Account View',
      program: 'COACTVWC',
      userType: 'U',
      accessible: true,
    },
  ],
};


/** One page of map COPAU0A for account 99, with a further page behind it. */
export const authorizationPage = {
  accountId: 99,
  customerId: 55,
  customerName: 'Alice B Carter',
  addressLine1: '1 Main St,',
  addressLine2: 'Raleigh,NC,27610',
  phone: '(201)456-1404',
  creditLimit: money('+      5,000.00', 5000),
  cashCreditLimit: money('+        500.00', 500),
  approvedCount: 2,
  declinedCount: 1,
  creditBalance: money('+        300.00', 300),
  cashBalance: money('+          0.00', 0),
  approvedAmount: money('+        300.00', 300),
  declinedAmount: money('+         40.00', 40),
  summaryFound: true,
  authorizations: [
    {
      authKey: '75930899999999',
      transactionId: 'TRAN0000000001',
      authorizationDate: '03/01/24',
      authorizationTime: '10:15:00',
      authorizationType: '01',
      approvalStatus: 'A',
      matchStatus: 'P',
      approvedAmount: money('+        100.00', 100),
    },
    {
      authKey: '75930899999998',
      transactionId: 'TRAN0000000002',
      authorizationDate: '03/01/24',
      authorizationTime: '10:16:00',
      authorizationType: '01',
      approvalStatus: 'D',
      matchStatus: 'D',
      approvedAmount: money('+          0.00', 0),
    },
  ],
  pageStartAuthKey: '75930899999999',
  lastAuthKey: '75930899999998',
  morePages: true,
  message: null,
};

/** Map COPAU1A for the first authorization of that page. */
export const authorizationDetail = {
  accountId: 99,
  authKey: '75930899999999',
  cardNumber: '4111111111111111',
  authorizationDate: '03/01/24',
  authorizationTime: '10:15:00',
  approvedAmount: money('+        100.00', 100),
  transactionAmount: money('+        100.00', 100),
  approvalStatus: 'A',
  responseCode: '00',
  responseReason: '0000-APPROVED',
  processingCode: '000000',
  posEntryMode: '01',
  messageSource: 'POS',
  merchantCategoryCode: '5411',
  cardExpiryDate: '12/25',
  authorizationType: '01',
  transactionId: 'TRAN0000000001',
  matchStatus: 'P',
  fraudStatus: '-',
  merchantName: 'CORNER STORE',
  merchantId: '000000000123456',
  merchantCity: 'RALEIGH',
  merchantState: 'NC',
  merchantZip: '27610',
  nextAuthKey: '75930899999998',
  message: null,
};

export const transactionPage = {
  rows: [
    {
      tranId: '0000000000000001',
      date: '06/01/22',
      description: 'Groceries',
      amount: money('+        100.00', 100),
    },
    {
      tranId: '0000000000000002',
      date: '06/02/22',
      description: 'Fuel',
      amount: money('+         25.50', 25.5),
    },
  ],
  pageNumber: 1,
  firstTranId: '0000000000000001',
  lastTranId: '0000000000000002',
  nextPageAvailable: true,
  message: null,
};

export const transactionDetail = {
  tranId: '0000000000000001',
  cardNumber: '4111111111111111',
  typeCode: '01',
  categoryCode: '0001',
  source: 'POS TERM',
  description: 'Groceries',
  amount: money('+        100.00', 100),
  originalTimestamp: '2022-06-01-10.00.00.000000',
  processedTimestamp: '2022-06-02-10.00.00.000000',
  merchantId: '000000123',
  merchantName: 'Corner Store',
  merchantCity: 'Raleigh',
  merchantZip: '27610',
};

export const billPaymentBalance = {
  accountId: '00000000009',
  currentBalance: money('+      1,025.50', 1025.5),
  tranId: null,
  paid: false,
  message: 'Confirm to make bill payment...',
};

export const cardPage = {
  rows: [
    { cardNumber: '4111111111111111', accountId: '11111111111', activeStatus: 'Y' },
    { cardNumber: '4111111111111112', accountId: '11111111111', activeStatus: 'N' },
  ],
  pageNumber: 1,
  firstCardNumber: '4111111111111111',
  lastCardNumber: '4111111111111112',
  nextPageAvailable: true,
  errorMessage: null,
  infoMessage: 'TYPE S FOR DETAIL, U TO UPDATE ANY RECORD',
};

export const cardDetail = {
  accountId: '11111111111',
  cardNumber: '4111111111111111',
  embossedName: 'JOHN Q PUBLIC',
  activeStatus: 'Y',
  expiryYear: '2026',
  expiryMonth: '03',
  expiryDay: '01',
  cvvCode: '123',
  infoMessage: '   Displaying requested details',
};
