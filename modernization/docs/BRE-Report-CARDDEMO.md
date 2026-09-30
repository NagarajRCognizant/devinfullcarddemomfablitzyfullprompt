# Consolidated Business Rule Report — CardDemo (whole application)

Application-wide view of the **517 rules** extracted from the 31 COBOL programs of AWS CardDemo
plus their copybooks, BMS maps, JCL and IMS/DB2/MQ artifacts. The three closure reports
(`BRE-Report-CARDDEMO-ACCOUNT.md`, `BRE-Report-CARDDEMO-USER-ADMIN.md`,
`BRE-Report-CARDDEMO-AUTHORIZATION.md`) hold the cross-program analysis of their own closures; this
report is the single index over all of them. Rule IDs are sequential **per program**, so a rule is
always cited as `<PROGRAM> BR-nn`.

## 1. Catalogue index

| Closure | Program(s) | Role | Rules | Catalogue |
|---|---|---|---|---|
| Account | `COACTVWC` | CICS account view (CAVW) | 23 | `BRD-COACTVWC.md` |
| Account | `COACTUPC` | CICS account update (CAUP) | 56 | `BRD-COACTUPC.md` |
| Account | `CBACT01C` | READACCT batch extract | 16 | `BRD-CBACT01C.md` |
| Account | `CSUTLDTC` | date validation service | 6 | `BRD-CSUTLDTC.md` |
| Account | `COMEN01C` | main menu access rules | 7 | `BRD-COMEN01C.md` |
| User admin | `COSGN00C` | sign-on (CC00) | 12 | `BRD-COSGN00C.md` |
| User admin | `COADM01C` | admin menu (CA00) | 9 | `BRD-COADM01C.md` |
| User admin | `COUSR00C` | user list (CU00) | 17 | `BRD-COUSR00C.md` |
| User admin | `COUSR01C`, `COUSR02C`, `COUSR03C` | user add, update, delete | 27 | `BRD-COUSR01C-02C-03C.md` |
| Authorization | `COPAUA0C` | authorization request processor (CP00) | 38 | `BRD-COPAUA0C.md` |
| Authorization | `COPAUS0C` | pending authorization summary (CPVS) | 30 | `BRD-COPAUS0C.md` |
| Authorization | `COPAUS1C` | authorization detail and fraud marking (CPVD) | 21 | `BRD-COPAUS1C.md` |
| Authorization | `COPAUS2C` | fraud report recording | 9 | `BRD-COPAUS2C.md` |
| Authorization | `CBPAUP0C` | expired authorization purge | 24 | `BRD-CBPAUP0C.md` |
| Authorization | `PAUDBLOD`, `PAUDBUNL`, `DBUNLDGS` | IMS load and unload utilities | 13 | `BRD-PAUDBLOD-PAUDBUNL-DBUNLDGS.md` |
| Transactions | `COTRN00C`, `COTRN01C`, `COTRN02C` | transaction list, view, add | 38 | `BRD-COTRN00C-01C-02C.md` |
| Transactions | `COBIL00C` | bill payment (CB00) | 18 | `BRD-COBIL00C.md` |
| Transactions | `CORPT00C` | transaction report request (CR00) | 14 | `BRD-CORPT00C.md` |
| Cards | `COCRDLIC`, `COCRDSLC`, `COCRDUPC` | card list, view, update | 39 | `BRD-COCRDLIC-SLC-UPC.md` |
| Transactions | `CBTRN01C`, `CBTRN02C`, `CBTRN03C` | daily verification, posting, report | 34 | `BRD-CBTRN01C-02C-03C.md` |
| Transactions | `CBACT04C` | monthly interest calculation | 20 | `BRD-CBACT04C.md` |
| Master prints | `CBACT02C`, `CBACT03C`, `CBCUS01C` | card, cross-reference, customer prints | 8 | `BRD-CBACT02C-CBACT03C-CBCUS01C.md` |
| Statements | `CBSTM03A`, `CBSTM03B` | account statements and the file handler | 16 | `BRD-CBSTM03A-CBSTM03B.md` |
| Migration | `CBEXPORT`, `CBIMPORT`, `COBSWAIT` | branch export, import, wait utility | 22 | `BRD-CBEXPORT-CBIMPORT-COBSWAIT.md` |

31 programs, 24 catalogues, 517 rules. Program coverage is reconciled against
`02-source-inventory-and-coverage.md`; artifact-by-artifact decisions are in
`13-artifact-disposition.md`.

## 2. Business capability → FR → BR

Each closure report numbers its own functional requirements, so an FR is cited with its report
(`BRE-Report-CARDDEMO-ACCOUNT.md` FR-01 … FR-18, `-USER-ADMIN.md` FR-19 … FR-39,
`-AUTHORIZATION.md` FR-19 … FR-36). The capabilities of the transactions, cards, statements and
migration closure are numbered `FR-T01` … `FR-T22` here:

| Capability | FR | Requirement | Rules |
|---|---|---|---|
| Transaction enquiry | FR-T01 | An operator may browse transactions ten to a page in transaction-id order, forwards and backwards | COTRN00C BR-01 … BR-04, BR-10 … BR-13 |
| Transaction enquiry | FR-T02 | A transaction id filter positions the browse and a row may be selected for display | COTRN00C BR-05 … BR-09 |
| Transaction enquiry | FR-T03 | One transaction is displayed field for field by its 16-character id | COTRN01C BR-01 … BR-11 |
| Transaction capture | FR-T04 | A transaction may be added against an account or a card, validated field by field in source order | COTRN02C BR-01 … BR-11 |
| Transaction capture | FR-T05 | A confirmed transaction is written with the next transaction id | COTRN02C BR-12 … BR-16 |
| Bill payment | FR-T06 | An operator may pay the whole balance of an existing account, refused when the balance is not positive | COBIL00C BR-01 … BR-07 |
| Bill payment | FR-T07 | The payment is recorded as a classified transaction and the balance is reduced by it, once | COBIL00C BR-08 … BR-14 |
| Reporting | FR-T08 | A transaction report may be requested for the current month, the current year or a validated custom range | CORPT00C BR-01 … BR-09 |
| Reporting | FR-T09 | A confirmed report request is handed to batch processing | CORPT00C BR-10 … BR-12 |
| Card enquiry | FR-T10 | Cards are browsed seven to a page in card-number order, filtered by account or card | COCRDLIC BR-01 … BR-14 |
| Card enquiry | FR-T11 | One card is displayed with its account, and its security code is never shown | COCRDSLC BR-01 … BR-08 |
| Card maintenance | FR-T12 | Card name, status and expiry are validated, confirmed, then written under a concurrency check | COCRDUPC BR-01 … BR-17 |
| Daily posting | FR-T13 | Every daily transaction is verified against the cross-reference, the account and the credit limit, and rejected with a reason code | CBTRN01C BR-01 … BR-07; CBTRN02C BR-01 … BR-08 |
| Daily posting | FR-T14 | An accepted transaction is posted to the transaction store, the category balance and the account | CBTRN02C BR-09 … BR-14 |
| Reporting | FR-T15 | Posted transactions are reported with page, account and grand totals on 133-character lines | CBTRN03C BR-01 … BR-11 |
| Interest | FR-T16 | Monthly interest is calculated per category balance from the account's disclosure group, with a default group fallback | CBACT04C BR-01 … BR-11 |
| Interest | FR-T17 | Interest is written as a transaction and settled to the account, clearing the cycle amounts | CBACT04C BR-12 … BR-20 |
| Operations | FR-T18 | The card, cross-reference and customer files can be printed in key order | CBACT02C / CBACT03C / CBCUS01C BR-01 … BR-08 |
| Statements | FR-T19 | A statement is produced per card with its transactions and total, in plain text and HTML | CBSTM03A BR-01 … BR-14; CBSTM03B BR-01, BR-02 |
| Branch migration | FR-T20 | Customer, account, cross-reference, transaction and card data can be exported for another branch | CBEXPORT BR-01 … BR-11 |
| Branch migration | FR-T21 | An export can be imported, dispatching on record type and rejecting unknown types without failing | CBIMPORT BR-01 … BR-10 |
| Operations | FR-T22 | A job step can pause for a supplied interval | COBSWAIT BR-01 |

Rule-by-rule traceability to the target (class, API, table, job, test, expected and actual result)
is in `14-rtm.md`: Part A account and user administration, Part B authorization, Part C
transactions, cards, statements and branch migration.

## 3. Rules by classification

| Classification | Share | Where it concentrates |
|---|---|---|
| Validation | ~30% | the CICS update programs (`COACTUPC`, `COCRDUPC`, `COTRN02C`, `COUSR01C`) |
| Control Flow | ~20% | PF-key dispatch, pseudo-conversational state, batch key breaks |
| Exception Handling | ~18% | file-status and CICS/IMS response checks in every program |
| Formatting/Conversion | ~13% | screen and report edited fields, fixed-width record images |
| Derivation/Default | ~9% | timestamps, sequence numbers, fixed classifications |
| Data Selection | ~6% | browse windows, filters, sequential reads |
| Calculation | ~4% | balances, credit limits, interest, report totals |

## 4. Cross-program rules

- **Date validation** is one service everywhere: `CSUTLDTC` BR-01 … BR-06 back `COACTUPC`
  BR-35 … BR-40, `COCRDUPC` expiry validation and `CORPT00C` BR-07 … BR-09.
- **Account resolution** is always cross-reference → account → customer, stopping at the first
  failure (`COACTVWC` BR-10, BR-11; reused by the card, transaction and statement flows).
- **The asterisk convention** — a field holding `*` means "nothing supplied" — is shared by
  `COACTVWC` BR-06, `COACTUPC` BR-18 and `COCRDLIC` filter clearing.
- **Operator type** governs every menu (`COMEN01C` BR-01 … BR-07, `COADM01C`), and the same two
  types drive the target's role mapping.
- **Balance movement** has exactly three sources — posting, bill payment and interest settlement —
  and all three go through one idempotent operation in the target
  (`BalanceAdjustmentService`), recorded as CMD-16 and CMD-18.

## 5. Application-wide risk register

Every flag below is carried in `11-unsupported-construct-register.md` (UCR) or
`12-consistency-model-delta-register.md` (CMD) and listed for sign-off in
`17-limitations-and-review-required.md`. None was silently fixed.

| Risk | Instances | Register |
|---|---|---|
| Hard-coded literal amounts, rates, ids, branch and region | `CBACT01C` default amount, `COBIL00C` placeholder merchant and classification, `CBACT04C` interest classification, `CBEXPORT` branch `0001` / region `NORTH` | UCR-07 |
| Unchecked return code from a called module | `COBSWAIT` `MVSWAIT`, `CBSTM03B` unrecognised file name | UCR-07, per-catalogue |
| Fields declared but never or selectively populated | `CBACT01C` extract layouts, `COACTVWC` stale screen values, `CBACT04C` fee fields | UCR-10, per-catalogue |
| Missing `ELSE` / `WHEN OTHER` on a business decision | `CBTRN02C` reason overwrite, `CBSTM03B` dispatch | per-catalogue |
| Unreachable or dead logic | `COACTUPC` unknown-state abend, `CBACT04C` `1400-COMPUTE-FEES`, `CBIMPORT` `3000-VALIDATE-IMPORT` | UCR-11, per-catalogue |
| Source defect deliberately corrected | `CBTRN03C` BR-09 closing totals, `CBSTM03A` BR-11 last card group, `CBTRN02C` post-category rewrite failure | `17-limitations-and-review-required.md` |
| Sensitive data in clear | `CBEXPORT` BR-08 personal data and card security code, `CBACT02C` / `CBCUS01C` record images in the log | `17-limitations-and-review-required.md` |
| Consistency model changed | bill payment, report submission, posting and interest settlement, authorization reply, purge counters | CMD-11, CMD-16, CMD-17, CMD-18 |

## 6. Exception scenarios

File and I/O behaviour is business logic throughout: status `'00'` is success, `'10'` is normal end
of file, anything else is fatal and abends with the status displayed. Each catalogue documents its
own open, read, write and close failure paths and abend codes in its "Exception scenarios" section;
the target equivalent is always a failed step or a mapped HTTP error, never a silent skip, except
where the source itself continued (`CBIMPORT` BR-05 and BR-08).
