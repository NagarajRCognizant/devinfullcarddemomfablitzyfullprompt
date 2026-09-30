# Data Conversion and Field-Level Mapping

Legacy stores: `ACCTDAT` (KSDS, `KEYS(11 0) RECORDSIZE(300 300)`), `CUSTDAT` (KSDS, `KEYS(9 0)
RECORDSIZE(500 500)`), `CARDXREF` (KSDS, `KEYS(16 0) RECORDSIZE(50 50)`) plus the alternate index
`CARDXREF.VSAM.AIX` (`KEYS(11,25)`) exposed to CICS as `CXACAIX` through its PATH. Definitions are
corroborated by `app/jcl/ACCTFILE.jcl`, `CUSTFILE.jcl`, `XREFFILE.jcl`, `app/csd/CARDDEMO.CSD` and
`app/catlg/LISTCAT.txt`.

## 1. Store-to-table mapping

| Legacy store | Copybook | Key | Record size | PostgreSQL object | Access path in the target |
|---|---|---|---|---|---|
| `ACCTDAT` KSDS | `CVACT01Y` | `ACCT-ID` (11 digits, offset 1) | 300 | table `account`, PK `acct_id` | `AccountRepository.findById`, `lockByAcctId` (`FOR UPDATE`) |
| `CUSTDAT` KSDS | `CVCUS01Y` | `CUST-ID` (9 digits, offset 1) | 500 | table `customer`, PK `cust_id` | `CustomerRepository.findById`, `lockByCustId` (`FOR UPDATE`) |
| `CARDXREF` KSDS | `CVACT03Y` | `XREF-CARD-NUM` (16 chars, offset 1) | 50 | table `card_xref`, PK `xref_card_num` | `CardXrefRepository.findById` |
| `CXACAIX` PATH over the AIX | `CVACT03Y` | `XREF-ACCT-ID` (11 digits, offset 25) | 50 | index `ix_card_xref_acct_id (xref_acct_id, xref_card_num)` | `CardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumAsc` |

Non-unique alternate index: an account may own several cards. Both online programs issue a single
`READ` against the path and take the first record of the key, so the target orders by card number
and takes the first row — the same observable behaviour for the supplied data (one card per account).

## 2. Type conversion rules applied

| Source form | Example field | Target form | Rationale |
|---|---|---|---|
| `PIC 9(11)` display key | `ACCT-ID` | `BIGINT` + `CHECK BETWEEN 0 AND 99999999999` | keeps every value the key allows and keeps ordering identical for the batch sequential read |
| `PIC 9(9)` display key | `CUST-ID` | `BIGINT` + range check | as above |
| `PIC S9(10)V99` (display/packed) | `ACCT-CURR-BAL` | `NUMERIC(12,2)` ↔ `BigDecimal` | exact decimal precision, scale and sign; no binary floating point anywhere |
| `PIC 9(4)` numeric text | `CUST-FICO-CREDIT-SCORE` | `INTEGER` + `CHECK BETWEEN 0 AND 999` | source declares 4 digits but the record holds 3-digit scores; the check documents the observed domain |
| `PIC X(n)` fixed text | names, addresses | `CHAR(n)` + Hibernate CHAR JDBC type | preserves blank padding, so length-sensitive comparisons and the batch extract stay byte-exact |
| `PIC X(10)` date text | `ACCT-OPEN-DATE` | `CHAR(10)` | the records store dates as text and the programs read, compare and rewrite them as text, including blank and zero-filled values a `DATE` column could not hold |
| `PIC X(1)` flag | `ACCT-ACTIVE-STATUS` | `CHAR(1)` | Y/N semantics preserved in `LookupTables` |
| `OCCURS` (batch array record) | `ARR-BAL-DEBIT(5)` | positional fields built by `AcctArrayRecordAggregator` | array exists only in the extract layout, never in a store |
| `REDEFINES` (text/numeric views of one field) | `ACUP-NEW-CUST-FICO-SCORE-X` | keyed text in the DTO, converted after the numeric edit passes | reproduces "edit as text, then interpret as number" |
| `COMP-3` | balances inside `CVACT01Y` | `BigDecimal` via `ZonedDecimalCodec`/`CobolNumeric` | the supplied sample data is unpacked display; the codec is exercised by unit tests for both forms |

Blank, zero, asterisk and LOW-VALUES semantics are preserved in the service layer, not in the
schema: a single asterisk or blanks in a screen field means "not entered" (COACTVWC BR-06, COACTUPC
BR-18), a zero account number is invalid (BR-09/BR-12), and `NOT NULL` columns hold blank-padded
text rather than SQL `NULL` so that a rewritten record is byte-comparable to the source record.

## 3. Account record — field mapping (`CVACT01Y` ↔ `account` ↔ `AccountEntity`)

| Source field | PIC | Column | Java | Screen / extract use |
|---|---|---|---|---|
| `ACCT-ID` | 9(11) | `acct_id` | `Long acctId` | key on both screens (display only), extract offset 1-11 |
| `ACCT-ACTIVE-STATUS` | X(1) | `active_status` | `String activeStatus` | `ACSTTUS`; editable on CAUP (Y/N) |
| `ACCT-CURR-BAL` | S9(10)V99 | `curr_bal` | `BigDecimal currBal` | `ACURBAL`; editable, signed 2dp |
| `ACCT-CREDIT-LIMIT` | S9(10)V99 | `credit_limit` | `BigDecimal creditLimit` | `ACRDLIM`; editable |
| `ACCT-CASH-CREDIT-LIMIT` | S9(10)V99 | `cash_credit_limit` | `BigDecimal cashCreditLimit` | `ACSHLIM`; editable |
| `ACCT-OPEN-DATE` | X(10) | `open_date` | `String openDate` | `ADTOPEN`, split into YYYY/MM/DD on CAUP |
| `ACCT-EXPIRAION-DATE` | X(10) | `expiration_date` | `String expirationDate` | `AEXPDT`; source misspelling kept in the source citation only |
| `ACCT-REISSUE-DATE` | X(10) | `reissue_date` | `String reissueDate` | `AREISDT`; reformatted in the extract (BR-08) |
| `ACCT-CURR-CYC-CREDIT` | S9(10)V99 | `curr_cyc_credit` | `BigDecimal currCycCredit` | `ACRCYCR`; editable |
| `ACCT-CURR-CYC-DEBIT` | S9(10)V99 | `curr_cyc_debit` | `BigDecimal currCycDebit` | `ACRCYDB`; editable; extract default 2525.00 when zero |
| `ACCT-ADDR-ZIP` | X(10) | `addr_zip` | `String addrZip` | never read or written by any in-scope program; retained for round-trip fidelity (column comment records this) |
| `ACCT-GROUP-ID` | X(10) | `group_id` | `String groupId` | `AADDGRP`; editable; compared case-insensitively |
| `FILLER` (178 bytes) | X(178) | not stored | — | padding only; regenerated when a record is reproduced |

## 4. Customer record — field mapping (`CVCUS01Y` ↔ `customer` ↔ `CustomerEntity`)

| Source field | PIC | Column | Java | Screen use |
|---|---|---|---|---|
| `CUST-ID` | 9(9) | `cust_id` | `Long custId` | `ACSTNUM`, display only |
| `CUST-FIRST-NAME` | X(25) | `first_name` | `String firstName` | `ACSFNAM`, required alphabetic |
| `CUST-MIDDLE-NAME` | X(25) | `middle_name` | `String middleName` | `ACSMNAM`, optional alphabetic |
| `CUST-LAST-NAME` | X(25) | `last_name` | `String lastName` | `ACSLNAM`, required alphabetic |
| `CUST-ADDR-LINE-1` | X(50) | `addr_line_1` | `String addrLine1` | `ACSADL1`, mandatory |
| `CUST-ADDR-LINE-2` | X(50) | `addr_line_2` | `String addrLine2` | `ACSADL2`, accepted unvalidated (BR-34) |
| `CUST-ADDR-LINE-3` | X(50) | `addr_line_3` | `String addrLine3` | `ACSCITY` — holds the city on both maps |
| `CUST-ADDR-STATE-CD` | X(2) | `addr_state_cd` | `String addrStateCd` | `ACSSTTE`, table-checked |
| `CUST-ADDR-COUNTRY-CD` | X(3) | `addr_country_cd` | `String addrCountryCd` | `ACSCTRY`, required alphabetic |
| `CUST-ADDR-ZIP` | X(10) | `addr_zip` | `String addrZip` | `ACSZIPC`, 5 numeric digits, cross-checked against the state |
| `CUST-PHONE-NUM-1` | X(15) | `phone_num_1` | `String phoneNum1` | `ACSPH1A/B/C`, stored as `(AAA)PPP-LLLL` |
| `CUST-PHONE-NUM-2` | X(15) | `phone_num_2` | `String phoneNum2` | `ACSPH2A/B/C`, same format |
| `CUST-SSN` | 9(9) | `ssn` | `String ssn` | `ACSSN1/2/3`; displayed 3-2-4 on CAVW |
| `CUST-GOVT-ISSUED-ID` | X(20) | `govt_issued_id` | `String govtIssuedId` | `ACSGOVT` |
| `CUST-DOB-YYYY-MM-DD` | X(10) | `dob_yyyy_mm_dd` | `String dobYyyyMmDd` | `ACSDOB…`, cannot be in the future |
| `CUST-EFT-ACCOUNT-ID` | X(10) | `eft_account_id` | `String eftAccountId` | `ACSEFTC`, numeric non-zero |
| `CUST-PRI-CARD-HOLDER-IND` | X(1) | `pri_card_holder_ind` | `String priCardHolderInd` | `ACSPFLG`, Y/N |
| `CUST-FICO-CREDIT-SCORE` | 9(4) | `fico_credit_score` | `Integer ficoCreditScore` | `ACSTFCO`, 300-850 |
| `FILLER` (168 bytes) | X(168) | not stored | — | padding only |

## 5. Cross-reference record — field mapping (`CVACT03Y` ↔ `card_xref`)

| Source field | PIC | Column | Java | Use |
|---|---|---|---|---|
| `XREF-CARD-NUM` | X(16) | `xref_card_num` | `String cardNum` | primary key; carried to the screens as the card number |
| `XREF-CUST-ID` | 9(9) | `xref_cust_id` | `Long custId` | drives the customer read |
| `XREF-ACCT-ID` | 9(11) | `xref_acct_id` | `Long acctId` | alternate-index key used by both screens |
| `FILLER` (14 bytes) | X(14) | not stored | — | padding only |

## 6. Schema creation and seed load (ACCTFILE/CUSTFILE/XREFFILE equivalent)

| IDCAMS step | Target equivalent |
|---|---|
| `DELETE` cluster | `DROP DATABASE` / `CREATE DATABASE` in the documented reset command |
| `DEFINE CLUSTER … KEYS … RECORDSIZE` | `V1__create_account_management_schema.sql` (tables, primary keys, range checks, comments) |
| `DEFINE ALTERNATEINDEX` + `DEFINE PATH` | `CREATE INDEX ix_card_xref_acct_id` |
| `REPRO INFILE(…PS)` | `V2__load_sample_data.sql`, generated from `app/data/ASCII/*.txt` |

Reset is a single documented command (`docs/15-local-execution-guide.md` §5).

## 7. Reconciliation

`modernization/tools/reconcile_sample_data.py` parses the three ASCII files and the three EBCDIC
data sets with the copybook offsets, compares them record by record, then compares the loaded
database rows against the ASCII source field by field.

Result (`evidence/sample-data-reconciliation.log`): account 50/50, customer 50/50, card
cross-reference 50/50; 550 account field comparisons all matched; one known difference between the
two supplied copies of the account file (record 49, `ACCT-ADDR-ZIP`/`ACCT-GROUP-ID`) is asserted as
a known source-data discrepancy rather than normalised — UCR-01. Final status
`RECONCILIATION PASSED`.

## 8. Authorization context — store-to-table mapping

Legacy stores: IMS HIDAM database `DBPAUTP0` (`ims/DBPAUTP0.dbd`, VSAM), its HIDAM primary index
database `DBPAUTX0` (`ims/DBPAUTX0.dbd`, `INDEX=ACCNTID`) and DB2 table `CARDDEMO.AUTHFRDS`
(`ddl/AUTHFRDS.ddl`, `dcl/AUTHFRDS.dcl`) with unique index `XAUTHFRD` (`CARD_NUM ASC, AUTH_TS
DESC`). All three are owned by `authorization-service` in database `carddemo_authorization`; no
other service reads them and this service reads no other database.

| Legacy store | Copybook / DCLGEN | Key | Length | PostgreSQL object | Access path in the target |
|---|---|---|---|---|---|
| `DBPAUTP0` root `PAUTSUM0` | `CIPAUSMY` | `ACCNTID` `S9(11) COMP-3`, unique | 100 | table `pending_auth_summary`, PK `acct_id` | `AuthorizationSummaryRepository.findById`, `findByIdForUpdate` (`FOR UPDATE`) |
| `DBPAUTP0` child `PAUTDTL1` | `CIPAUDTY` | `PAUT9CTS` `X(8)` (date/time nines complement), unique within parent | 200 | table `pending_auth_detail`, PK `(acct_id, auth_key)`, FK to the summary | `findPage` (five rows, keyset), `findChildren`, `findById` |
| `DBPAUTX0` segment `PAUTINDX` | — | `INDXSEQ` = `ACCNTID` | 6 | no separate object | PostgreSQL maintains `pk_pending_auth_summary`; a physical index database has no target counterpart |
| DB2 `AUTHFRDS` | `AUTHFRDS.dcl` | `(CARD_NUM, AUTH_TS)` | row | table `auth_fraud`, PK `(card_num, auth_ts)` | `FraudReportRepository` |
| DB2 index `XAUTHFRD` | — | `(CARD_NUM ASC, AUTH_TS DESC)` | — | `pk_auth_fraud` | primary-key b-tree; descending reads are served by a backward scan |

The IMS hierarchy becomes a parent/child pair with a foreign key rather than one denormalised
table, because `COPAUS0C` reads the root alone for its six metrics (BR-07/BR-08), `COPAUS1C` reads
one child by key, and `CBPAUP0C` walks children under a positioned root — three access paths the
relational parent/child model serves directly. The child key stays the stored 14-character nines
complement instead of being recomputed into a timestamp: ascending order on it is exactly the
newest-first order the summary screen pages through (COPAUS0C BR-11) and `CBPAUP0C` reverses it to
recover the Julian date (BR-09).

## 9. Authorization type conversion rules

| Source form | Example field | Target form | Rationale |
|---|---|---|---|
| `PIC S9(11) COMP-3` key | `PA-ACCT-ID` | `BIGINT` | packed decimal is decoded once, at migration; the value range is unchanged |
| `PIC 9(9)` display | `PA-CUST-ID` | `BIGINT` | zoned decimal decoded to an integer |
| `PIC S9(5) COMP-3` complemented date | `PA-AUTH-DATE-9C` | `INTEGER` | kept complemented, as the source stores it; `AuthorizationKey` performs `99999 −` in both directions |
| `PIC S9(9) COMP-3` complemented time | `PA-AUTH-TIME-9C` | `BIGINT` | as above with `999999999 −` |
| `PIC X(8)` derived key `PAUT9CTS` | segment key | `CHAR(14)` `auth_key` | the two complemented components concatenated, zero padded, so ordering and equality match the IMS key |
| `PIC S9(9)V99 COMP-3` | `PA-CREDIT-LIMIT`, `PA-CREDIT-BALANCE` | `NUMERIC(11,2)` ↔ `BigDecimal` | exact decimal, sign preserved; 11 digits is the declared precision |
| `PIC S9(10)V99 COMP-3` | `PA-TRANSACTION-AMT`, `PA-APPROVED-AMT` | `NUMERIC(12,2)` ↔ `BigDecimal` | as above with the wider declared precision |
| `PIC S9(4) COMP` counter | `PA-APPROVED-AUTH-CNT` | `SMALLINT` ↔ `short` | binary halfword; `smallint` has the same range, so the counter's declared domain is neither narrowed nor silently widened |
| `PIC X(n)` fixed text | card number, merchant fields, response code | `CHAR(n)` | trailing blanks are part of the value: `PA-MESSAGE-TYPE` is `'1234  '` in the supplied data and the reply layout is position sensitive |
| `PIC 9(6)` display | `PA-PROCESSING-CODE` | `CHAR(6)` | the programs move it as text and never compute with it; `CHAR(6)` keeps leading zeros |
| `PIC 9(2)` display | `PA-POS-ENTRY-MODE` | `CHAR(2)` in the detail, `SMALLINT` in `auth_fraud` | the DB2 DCLGEN declares it `SMALLINT`, the IMS segment declares it display; each side keeps its own declared form |
| `OCCURS 5 TIMES PIC X(2)` | `PA-ACCOUNT-STATUS` | `CHAR(10)` | the five occurrences are never indexed individually by any in-scope program; the group is stored as one fixed-width value |
| DB2 `TIMESTAMP` | `AUTH-TS` | `TIMESTAMP` | unchanged |
| DB2 `DATE` | `FRAUD-RPT-DATE` | `DATE` in `auth_fraud`, `CHAR(8)` in the detail | each source declaration is preserved; `COPAUS1C` formats the detail value as text |

## 10. `PAUTSUM0` — field mapping (`CIPAUSMY` ↔ `pending_auth_summary` ↔ `AuthorizationSummaryEntity`)

| Source field | PIC | Offset | Column | Java | Use |
|---|---|---|---|---|---|
| `PA-ACCT-ID` | S9(11) COMP-3 | 1-6 | `acct_id` | `Long acctId` | root key; `ACCNTID` of the DBD |
| `PA-CUST-ID` | 9(9) | 7-15 | `cust_id` | `Long custId` | CPVS header (BR-04) |
| `PA-AUTH-STATUS` | X(1) | 16 | `auth_status` | `String authStatus` | declared, never populated by any in-scope program (UCR-32) |
| `PA-ACCOUNT-STATUS` | X(2) OCCURS 5 | 17-26 | `account_status` | `String accountStatus` | declared, never read by any in-scope program (UCR-32) |
| `PA-CREDIT-LIMIT` | S9(9)V99 COMP-3 | 27-32 | `credit_limit` | `BigDecimal creditLimit` | available credit (COPAUA0C BR-15) |
| `PA-CASH-LIMIT` | S9(9)V99 COMP-3 | 33-38 | `cash_limit` | `BigDecimal cashLimit` | CPVS header |
| `PA-CREDIT-BALANCE` | S9(9)V99 COMP-3 | 39-44 | `credit_balance` | `BigDecimal creditBalance` | available credit; incremented on approval (BR-27) |
| `PA-CASH-BALANCE` | S9(9)V99 COMP-3 | 45-50 | `cash_balance` | `BigDecimal cashBalance` | reset to zero on approval (BR-28) |
| `PA-APPROVED-AUTH-CNT` | S9(4) COMP | 51-52 | `approved_auth_cnt` | `Short approvedAuthCnt` | incremented on approval, decremented by the purge |
| `PA-DECLINED-AUTH-CNT` | S9(4) COMP | 53-54 | `declined_auth_cnt` | `Short declinedAuthCnt` | incremented on decline |
| `PA-APPROVED-AUTH-AMT` | S9(9)V99 COMP-3 | 55-60 | `approved_auth_amt` | `BigDecimal approvedAuthAmt` | accumulated approvals |
| `PA-DECLINED-AUTH-AMT` | S9(9)V99 COMP-3 | 61-66 | `declined_auth_amt` | `BigDecimal declinedAuthAmt` | accumulated declines |
| `FILLER` | X(34) | 67-100 | not stored | — | padding only |

The entity adds one column with no source field: `version`, the JPA optimistic-lock counter. It
exists because the IMS unit of work that protected a read-modify-write of the root
(`COPAUA0C` BR-06) is gone; pessimistic `FOR UPDATE` selection plus this counter is the
compensating control recorded as CMD-14.

## 11. `PAUTDTL1` — field mapping (`CIPAUDTY` ↔ `pending_auth_detail` ↔ `AuthorizationDetailEntity`)

| Source field | PIC | Offset | Column | Java |
|---|---|---|---|---|
| `PA-AUTH-DATE-9C` | S9(5) COMP-3 | 1-3 | `auth_date_9c` | `Integer authDate9c` |
| `PA-AUTH-TIME-9C` | S9(9) COMP-3 | 4-8 | `auth_time_9c` | `Long authTime9c` |
| (the two above, as `PAUT9CTS`) | X(8) key | 1-8 | `auth_key` | `AuthorizationDetailId.authKey` |
| `PA-AUTH-ORIG-DATE` | X(6) | 9-14 | `auth_orig_date` | `String authOrigDate` |
| `PA-AUTH-ORIG-TIME` | X(6) | 15-20 | `auth_orig_time` | `String authOrigTime` |
| `PA-CARD-NUM` | X(16) | 21-36 | `card_num` | `String cardNum` |
| `PA-AUTH-TYPE` | X(4) | 37-40 | `auth_type` | `String authType` |
| `PA-CARD-EXPIRY-DATE` | X(4) | 41-44 | `card_expiry_date` | `String cardExpiryDate` |
| `PA-MESSAGE-TYPE` | X(6) | 45-50 | `message_type` | `String messageType` |
| `PA-MESSAGE-SOURCE` | X(6) | 51-56 | `message_source` | `String messageSource` |
| `PA-AUTH-ID-CODE` | X(6) | 57-62 | `auth_id_code` | `String authIdCode` |
| `PA-AUTH-RESP-CODE` | X(2) | 63-64 | `auth_resp_code` | `String authRespCode` |
| `PA-AUTH-RESP-REASON` | X(4) | 65-68 | `auth_resp_reason` | `String authRespReason` |
| `PA-PROCESSING-CODE` | 9(6) | 69-74 | `processing_code` | `String processingCode` |
| `PA-TRANSACTION-AMT` | S9(10)V99 COMP-3 | 75-81 | `transaction_amt` | `BigDecimal transactionAmt` |
| `PA-APPROVED-AMT` | S9(10)V99 COMP-3 | 82-88 | `approved_amt` | `BigDecimal approvedAmt` |
| `PA-MERCHANT-CATAGORY-CODE` | X(4) | 89-92 | `merchant_category_code` | `String merchantCategoryCode` |
| `PA-ACQR-COUNTRY-CODE` | X(3) | 93-95 | `acqr_country_code` | `String acqrCountryCode` |
| `PA-POS-ENTRY-MODE` | 9(2) | 96-97 | `pos_entry_mode` | `String posEntryMode` |
| `PA-MERCHANT-ID` | X(15) | 98-112 | `merchant_id` | `String merchantId` |
| `PA-MERCHANT-NAME` | X(22) | 113-134 | `merchant_name` | `String merchantName` |
| `PA-MERCHANT-CITY` | X(13) | 135-147 | `merchant_city` | `String merchantCity` |
| `PA-MERCHANT-STATE` | X(2) | 148-149 | `merchant_state` | `String merchantState` |
| `PA-MERCHANT-ZIP` | X(9) | 150-158 | `merchant_zip` | `String merchantZip` |
| `PA-TRANSACTION-ID` | X(15) | 159-173 | `transaction_id` | `String transactionId` |
| `PA-MATCH-STATUS` | X(1) | 174 | `match_status` | `String matchStatus` |
| `PA-AUTH-FRAUD` | X(1) | 175 | `auth_fraud` | `String authFraud` |
| `PA-FRAUD-RPT-DATE` | X(8) | 176-183 | `fraud_rpt_date` | `String fraudRptDate` |
| `FILLER` | X(17) | 184-200 | not stored | — |

## 12. `AUTHFRDS` — field mapping (DCLGEN ↔ `auth_fraud` ↔ `FraudReportEntity`)

`COPAUS2C` inserts one row per authorization a service representative marks as fraudulent. The
DB2 columns are carried over one to one, so the DCLGEN remains the field-level reference: `CARD_NUM
CHAR(16)` and `AUTH_TS TIMESTAMP` form the key, the authorization attributes (`AUTH_TYPE`,
`CARD_EXPIRY_DATE`, `MESSAGE_TYPE`, `MESSAGE_SOURCE`, `AUTH_ID_CODE`, `AUTH_RESP_CODE`,
`AUTH_RESP_REASON`, `PROCESSING_CODE`) and merchant attributes keep their DB2 declarations,
`TRANSACTION_AMT`/`APPROVED_AMT` become `NUMERIC(12,2)`, `POS_ENTRY_MODE` stays `SMALLINT`,
`FRAUD_RPT_DATE` stays `DATE`, and `ACCT_ID`/`CUST_ID` stay `NUMERIC(11,0)`/`NUMERIC(9,0)` as the
DCLGEN declares them. The column misspelling `MERCHANT_CATAGORY_CODE` is kept in `auth_fraud`
because it is the DB2 name; the IMS-derived detail table uses the corrected spelling, matching each
source artifact.

## 13. Authorization migration pipeline (extract, transform, load, reconcile)

The supplied data set is
`app/app-authorization-ims-db2-mq/data/EBCDIC/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat`, an EBCDIC
unload image of `DBPAUTP0`: variable-length records carrying one segment each in hierarchic order,
so every `PAUTSUM0` root is followed by its `PAUTDTL1` children.

| Stage | Source job | Target implementation |
|---|---|---|
| Extract | `UNLDPADB.JCL` → `PAUDBUNL`, `UNLDGSAM.JCL` → `DBUNLDGS` (GSAM unload) | `tools/ims_authorization_unload.py` reads the supplied unload image; segment records are located by the record-length and segment-name fields |
| Transform | copybook layouts in `PAUDBLOD` | the same module applies `CIPAUSMY`/`CIPAUDTY`: cp037 EBCDIC → UTF-8, `COMP-3` → `Decimal` with the implied scale, `COMP` → signed integer, zoned decimal → integer, `PIC X(n)` kept fixed-width |
| Load | `LOADPADB.JCL` → `PAUDBLOD` (`ISRT` root then children) | `tools/generate_authorization_seed_sql.py` writes `V2__load_authorization_sample_data.sql`; Flyway inserts the roots, then the children, so the foreign key reproduces the hierarchic insert order |
| Reconcile | none in source | `tools/reconcile_authorization_data.py` re-decodes the image and compares counts, per-account parent/child fan-out and the fields of every row, including the derived `auth_key` |
| Audit | IMS log | Flyway schema history plus the reconciliation log under `docs/evidence/` |
| Rollback | image copy / `DBPAUTP0.jcl` reorganisation | drop and recreate `carddemo_authorization`, re-run `flyway migrate`; the seed migration is deterministic, so a reload is byte-identical |

EBCDIC conversion is explicit and single-sourced: the decoder is the only place code page 037 is
applied, and both the seed generator and the reconciliation report import it, so the loaded rows and
the reconciled expectation cannot drift apart.

Result of the supplied image: 21 `PAUTSUM0` roots, 202 `PAUTDTL1` children, one rejected record.
The rejected record is a trailing root whose packed key field is blank (`X'404040404040'`), which no
valid `PIC S9(11) COMP-3` field can be. It is reported rather than normalised — inventing an account
id would invent business data — and recorded as UCR-31.
