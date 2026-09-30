# Source Inventory and Coverage Report

Repository: `Cognizant-platform-seg/Aws_Card_Demo2`. Discovery method: full directory enumeration of
`app/`, then transitive closure of `COPY`, `CALL`, `XCTL`, `LINK`, CICS `FILE`/`DATASET` names, JCL
`EXEC PGM=`/`DD` names, CSD resource definitions and catalog entries, starting from the four seed
artifacts (COACTVWC, COACTUPC, READACCT.jcl, ACCTFILE.jcl).

This report covers the Account Management closure in sections 1-4 and the Credit Card Authorizations
closure in section 5. The User Administration closure (COSGN00C,
COADM01C, COUSR00C/01C/02C/03C, `CSUSR01Y`, `USRSEC`) is inventoried in
`BRE-Report-CARDDEMO-USER-ADMIN.md` and converted in the identity service.

## 1. Repository totals

| Type | Location | Total files |
|---|---|---|
| COBOL programs | `app/cbl` | 31 |
| Copybooks | `app/cpy` | 30 |
| BMS map source | `app/bms` | 17 |
| BMS symbolic maps | `app/cpy-bms` | 17 |
| JCL | `app/jcl` | 38 |
| PROCs | `app/proc` | 2 |
| CICS resource definitions | `app/csd` | 1 |
| Catalog listing | `app/catlg` | 1 |
| ASCII sample data | `app/data/ASCII` | 9 |
| EBCDIC sample data | `app/data/EBCDIC` | 13 |
| **Total** | | **159** |

## 2. Dependency closure of the seed scope (in-scope artifacts)

### 2.1 Programs (6)

| Artifact | Reached from | Role | Analysed | Notes |
|---|---|---|---|---|
| `cbl/COACTVWC.cbl` | seed | CICS transaction CAVW, account enquiry | Yes, in full | 23 rules — `BRD-COACTVWC.md` |
| `cbl/COACTUPC.cbl` | seed | CICS transaction CAUP, account update | Yes, in full | 56 rules — `BRD-COACTUPC.md` |
| `cbl/CBACT01C.cbl` | `READACCT.jcl` `EXEC PGM=CBACT01C` | batch account extract | Yes, in full | 16 rules — `BRD-CBACT01C.md` |
| `cbl/CSUTLDTC.cbl` | `CSUTLDPY` `CALL 'CSUTLDTC'` | date validation service | Yes, in full | 6 rules — `BRD-CSUTLDTC.md` |
| `cbl/COMEN01C.cbl` | `XCTL PROGRAM(LIT-MENUPGM)` from both seeds | main menu / access decision | Partially — option selection and access rules only | 7 rules — `BRD-COMEN01C.md`; the other nine menu options are out of capability scope |
| `cbl/COSGN00C.cbl` | `MOVE 'COSGN00C'` in COMEN01C exit path | sign-on | Reference only in this closure | Analysed and converted in the User Administration closure (identity service `SignOnService`); it is the source of the operator type used here |

External programs referenced but not present in the repository: `COBDATFT` (assembler date
formatter, called by CBACT01C), `CEEDAYS` and `CEE3ABD` (IBM Language Environment). Recorded as
UCR-04/UCR-06/UCR-08 in the Unsupported Construct Register.

### 2.2 Copybooks (18 in `app/cpy`, plus 3 symbolic maps)

| Artifact | Classification | Analysed | Target |
|---|---|---|---|
| `CVACT01Y` | File record layout — account master (300 bytes) | Yes | `AccountEntity`, table `account` |
| `CVACT02Y` | File record layout — card master (150 bytes) | Yes | Not persisted; only `CARD-NUM` is used by the enquiry path |
| `CVACT03Y` | File record layout — card/account cross-reference (50 bytes) | Yes | `CardXrefEntity`, table `card_xref` |
| `CVCUS01Y` | File record layout — customer master (500 bytes) | Yes | `CustomerEntity`, table `customer` |
| `CVCRD01Y` | Working-storage support — card/account key work area | Yes | method parameters in `AccountReadService` |
| `COCOM01Y` | COMMAREA / interface structure | Yes | replaced by explicit request/response DTOs |
| `COMEN02Y` | Constants / reference data — menu option table | Yes | `MenuAccessService` (in-scope options only) |
| `COTTL01Y` | Constants — screen titles | Yes | React `ScreenFrame.tsx` |
| `CSDAT01Y` | Working-storage support — current date/time work area | Yes | `ClockConfig` + `java.time` |
| `CSMSG01Y` | Constants — common messages | Yes | `ScreenMessages` |
| `CSMSG02Y` | Constants — ABEND message area | Yes | `ApiExceptionHandler` |
| `CSUSR01Y` | File record layout — user security record | Yes | Persisted by the identity service (`usrsec` table); account management consumes the operator type from the sign-on token |
| `CSLKPCDY` | Constants / reference data — area codes, state codes, state+zip combinations, Y/N codes | Yes | `LookupTables` |
| `CSUTLDPY` | Procedure copybook — date edit paragraphs | Yes, in full | `DateEditor` |
| `CSUTLDWY` | Working-storage support — date edit work area and flags | Yes | `DateEditResult`, `DateValidationResult` |
| `CSSETATY` | Procedure copybook — field attribute macro (40 expansions in COACTUPC) | Yes | `FieldFlag` + React field styling |
| `CSSTRPFY` | Procedure copybook — AID to function-key mapping | Yes | replaced by explicit REST operations (transport, not business logic) |
| `CODATECN` | Interface contract — `COBDATFT` parameter area | Yes | `CobdatftDateFormatter` |
| `cpy-bms/COACTVW.CPY` | Screen structure | Yes | React `AccountViewScreen.tsx` |
| `cpy-bms/COACTUP.CPY` | Screen structure | Yes | React `AccountUpdateScreen.tsx` |
| `cpy-bms/COMEN01.CPY` | Screen structure | Reference only | Menu screen is represented by a minimal `MenuScreen.tsx` carrying only the in-scope options |

IBM-supplied `DFHBMSCA` and `DFHAID` are not present in the repository and are platform plumbing;
per the engagement instruction they are modernised away and not reimplemented.

### 2.3 Screens (2 in scope)

`bms/COACTVW.bms` and `bms/COACTUP.bms` — both analysed field by field
(`06-screen-mapping.md`). `bms/COMEN01.bms` is reference only.

### 2.4 JCL and data-store definitions (4 in scope, 3 reachable-excluded)

| Artifact | Role | Analysed | Notes |
|---|---|---|---|
| `jcl/READACCT.jcl` | seed — runs CBACT01C, three extract DDs | Yes | `ReadAcctJobConfig` |
| `jcl/ACCTFILE.jcl` | seed — DELETE/DEFINE ACCT KSDS `KEYS(11 0) RECORDSIZE(300 300)`, REPRO load | Yes | Flyway V1 + V2 and the documented reset command |
| `jcl/CUSTFILE.jcl` | defines CUSTOMER KSDS `KEYS(9 0) RECORDSIZE(500 500)`, REPRO load — the file read by both seeds | Yes | table `customer`, seeded by V2 |
| `jcl/XREFFILE.jcl` | defines CARDXREF KSDS `KEYS(16 0) RECORDSIZE(50 50)`, the account alternate index `KEYS(11,25)` and its PATH — the access path used by both seeds | Yes | table `card_xref` + index `ix_card_xref_acct_id`, seeded by V2 |
| `jcl/OPENFIL.jcl`, `jcl/CLOSEFIL.jcl` | CEMT open/close of the CICS files including ACCTDAT/CUSTDAT/CXACAIX | Reference only | CICS operational procedure with no business rule; no local equivalent needed |
| `jcl/CARDFILE.jcl` | defines the CARD master referenced by `CVACT02Y` | Reference only | The card master is not read by either seed; only the cross-reference card number is used |

### 2.5 Resource and catalog evidence (2 in scope)

| Artifact | Evidence extracted | Analysed |
|---|---|---|
| `csd/CARDDEMO.CSD` | `FILE(ACCTDAT)`, `FILE(CUSTDAT)`, `FILE(CXACAIX)` → `…CARDXREF.VSAM.AIX.PATH`; `PROGRAM(COACTVWC)`/`TRANSACTION(CAVW)`; `PROGRAM(COACTUPC)`/`TRANSACTION(CAUP)` | Yes, in-scope definitions |
| `catlg/LISTCAT.txt` | cluster/AIX/path names, key positions and record sizes corroborating the JCL definitions | Yes, in-scope entries |

### 2.6 Sample data (6 in scope)

`data/ASCII/acctdata.txt`, `custdata.txt`, `cardxref.txt` and the EBCDIC equivalents
`AWS.M2.CARDDEMO.ACCTDATA.PS`, `AWS.M2.CARDDEMO.CUSTDATA.PS`, `AWS.M2.CARDDEMO.CARDXREF.PS`
(50 records each). All six were parsed, cross-reconciled and loaded; evidence in
`evidence/sample-data-reconciliation.log`. One byte-level difference between the ASCII and EBCDIC
account files is recorded as UCR-01.

### 2.7 In-scope totals

| Type | In scope | Analysed in full | Analysed partially | Reference only |
|---|---|---|---|---|
| Programs | 6 | 4 | 1 (COMEN01C) | 1 (COSGN00C — analysed in the User Administration closure) |
| Copybooks | 18 | 18 | 0 | 0 |
| BMS symbolic maps | 3 | 2 | 0 | 1 (COMEN01) |
| BMS map source | 3 | 2 | 0 | 1 (COMEN01) |
| JCL | 6 | 4 | 0 | 2 |
| CSD | 1 | 1 (in-scope definitions) | 0 | 0 |
| Catalog | 1 | 1 (in-scope entries) | 0 | 0 |
| Sample data | 6 | 6 | 0 | 0 |
| **Total** | **44** | **38** | **1** | **5** |

## 3. Coverage by artifact type

Coverage is stated two ways: against the dependency closure of the seed scope (the analysis
obligation), and against the whole repository (context).

| Type | In-scope analysed / in-scope total | In-scope coverage | Repository total | Repository coverage |
|---|---|---|---|---|
| COBOL programs | 5 / 6 (4 full + 1 partial) | 83% full+partial, 67% full | 31 | 16% |
| Copybooks | 18 / 18 | 100% | 30 | 60% |
| BMS map source | 2 / 3 | 67% | 17 | 12% |
| BMS symbolic maps | 2 / 3 | 67% | 17 | 12% |
| JCL | 4 / 6 | 67% | 38 | 11% |
| PROCs | 0 / 0 | not applicable | 2 | 0% |
| CSD | 1 / 1 | 100% | 1 | 100% |
| Catalog | 1 / 1 | 100% | 1 | 100% |
| ASCII data | 3 / 3 | 100% | 9 | 33% |
| EBCDIC data | 3 / 3 | 100% | 13 | 23% |
| **In-scope overall** | **39 / 44** | **89%** | 159 | 25% |

Business-rule extraction covers 100% of the artifacts that carry rules for this capability: the two
online programs, the batch program, the date service and both procedure copybooks were read line by
line; the menu program was read in full and its access rules extracted.

## 4. Excluded, missing, unreadable, incomplete or conflicting artifacts

| # | Artifact(s) | Category | Business impact | Technical impact | Disposition |
|---|---|---|---|---|---|
| E-01 | 25 COBOL programs outside the account-management capability (card list/detail/update, transactions, bill payment, reports, statements, interest, posting, import/export; the user-administration programs are converted in their own closure) | Out of capability scope | None for account view/update/extract; those capabilities remain on the mainframe | No target code generated; no shared logic was inferred from them | Retired from this scope (see `13-artifact-disposition.md`) |
| E-02 | COMEN01C options 3-11 and their programs | Reachable but deliberately excluded | Menu navigation to other capabilities is unavailable in the local build | `MenuAccessService` exposes only the two in-scope options | Manual Review Required |
| E-03 | COSGN00C (sign-on) | Reachable, converted in the User Administration closure | Operator identification is reproduced by the identity service | Legacy clear-text password comparison and distinct user-not-found/wrong-password messages preserved for parity (UCR-14) | Migrated |
| E-04 | 12 copybooks for out-of-scope records (CVTRA01Y-07Y, CVEXPORT, CUSTREC, COSTM01, COADM02Y, UNUSED1Y) | Out of capability scope | None | Not converted | Retired from this scope |
| E-05 | 14 BMS maps / 14 symbolic maps for out-of-scope screens | Out of capability scope | None | Not converted | Retired from this scope |
| E-06 | 32 JCL members and 2 PROCs for out-of-scope jobs | Out of capability scope | None | Not converted | Retired from this scope |
| E-07 | `COBDATFT` (assembler) | Missing from the repository | Reissue-date presentation in the batch extract cannot be verified against the original implementation | Behaviour inferred from the `CODATECN` interface and the observed `YYYY-MM-DD` sample data; conversion type `'2'` treated as identity for `YYYY-MM-DD` | Manual Review Required (UCR-04) |
| E-08 | `CEEDAYS`, `CEE3ABD`, `DFHBMSCA`, `DFHAID` | IBM-supplied, not in the repository | None for business behaviour | Replaced by `java.time` strict parsing and by exception handling; screen attribute constants replaced by UI styling | Replaced (UCR-06, UCR-08) |
| E-09 | Account/EBCDIC sample-data byte difference at record 49 | Conflicting evidence within supplied data | One demo account's zip/group-id differs between the two supplied copies of the same file | Loader uses the ASCII copy; the difference is asserted, not normalised, by the reconciliation script | Manual Review Required (UCR-01) |
| E-10 | Runtime evidence: production batch schedules, Control-M/CA-7 definitions, SMF/CICS statistics, runbooks | Absent from the repository | Operational timing and volume behaviour cannot be validated | Batch is launched manually locally; no scheduler is introduced (playbook §4.8) | Not applicable — recorded as a gap |
| E-11 | Functional/design specifications | Absent from the repository | Every rule is corroborated only against source code (and, where possible, a second artifact such as the CSD, catalog or sample data) | Confidence levels in the rule catalogue reflect single-artifact corroboration where that is all that exists | Recorded as a gap |

No artifact in the repository was unreadable, and no in-scope artifact was skipped for convenience.
No behaviour was inferred from an artifact that was not analysed.

---

## 5. Authorization closure — `app/app-authorization-ims-db2-mq/`

Discovery method for this closure: full enumeration of the module directory, then the transitive
closure of `COPY`, `CALL`, `LINK`, `EXEC DLI`/`CBLTDLI` PCB names, `EXEC SQL` table names, MQ queue
names, CSD resource definitions and JCL `EXEC PGM=`/`DD` names, starting from the five seed programs
named in the engagement (`COPAUA0C`, `COPAUS0C`, `COPAUS1C`, `COPAUS2C`, `CBPAUP0C`) plus the three
data utilities (`PAUDBLOD`, `PAUDBUNL`, `DBUNLDGS`). The module `README.md` was used as corroborating
documentation for the MQ message contracts and the trigger path.

The module is self-contained: it holds its own CSD, IMS DBDs and PSBs, DB2 DDL/DCLGEN, JCL and
EBCDIC data, and it references the account closure only through the card cross-reference, account
and customer records, which are already owned by the account service.

### 5.1 Module totals

| Type | Location | Files | In scope | Analysed in full | Reference only |
|---|---|---|---|---|---|
| COBOL programs | `cbl/` | 8 | 8 | 8 | 0 |
| Copybooks | `cpy/` | 9 | 9 | 9 | 0 |
| BMS map source | `bms/` | 2 | 2 | 2 | 0 |
| BMS symbolic maps | `cpy-bms/` | 2 | 2 | 2 | 0 |
| IMS DBDs and PSBs | `ims/` | 8 | 8 | 8 | 0 |
| DB2 DDL | `ddl/` | 2 | 2 | 2 | 0 |
| DB2 DCLGEN | `dcl/` | 1 | 1 | 1 | 0 |
| JCL | `jcl/` | 5 | 5 | 5 | 0 |
| CICS resource definitions | `csd/` | 1 | 1 | 1 | 0 |
| EBCDIC sample data | `data/EBCDIC/` | 1 | 1 | 1 | 0 |
| Module documentation | `README.md` | 1 | 1 | 1 | 0 |
| **Total** | | **40** | **40** | **40** | **0** |

Every file in the module is in scope and every one was read. 7,596 lines of source, of which 3,292
are COBOL.

### 5.2 Programs (8)

| Artifact | Lines | Reached from | Role | Rules | Catalogue |
|---|---|---|---|---|---|
| `cbl/COPAUA0C.cbl` | 1026 | seed; CSD `TRANSACTION(CP00)` | MQ-triggered authorization request processor | 38 | `BRD-COPAUA0C.md` |
| `cbl/COPAUS0C.cbl` | 1032 | seed; CSD `TRANSACTION(CPVS)` | pending authorization summary screen | 30 | `BRD-COPAUS0C.md` |
| `cbl/COPAUS1C.cbl` | 604 | seed; CSD `TRANSACTION(CPVD)` | authorization detail screen and fraud toggle | 21 | `BRD-COPAUS1C.md` |
| `cbl/COPAUS2C.cbl` | 244 | `COPAUS1C` `EXEC CICS LINK PROGRAM('COPAUS2C')` | fraud report writer, DB2 `AUTHFRDS` | 9 | `BRD-COPAUS2C.md` |
| `cbl/CBPAUP0C.cbl` | 386 | `jcl/CBPAUP0J.jcl` `PARM='BMP,CBPAUP0C,PSBPAUTB'` | expired authorization purge (BMP) | 24 | `BRD-CBPAUP0C.md` |
| `cbl/PAUDBLOD.CBL` | 369 | `jcl/LOADPADB.JCL` | loads the `DBPAUTP0` hierarchy from sequential files | 13 (shared catalogue) | `BRD-PAUDBLOD-PAUDBUNL-DBUNLDGS.md` |
| `cbl/PAUDBUNL.CBL` | 317 | `jcl/UNLDPADB.JCL` | unloads the hierarchy to sequential files | shared | same |
| `cbl/DBUNLDGS.CBL` | 366 | `jcl/UNLDGSAM.JCL` | unloads the hierarchy through GSAM | shared | same |

No program in this closure calls an artifact that is missing from the repository. `COPAUS1C` links to
`COPAUS2C`, which is present; the IMS and MQ interfaces are `CBLTDLI`/`MQI` stubs supplied by the
platform, not application programs, and are recorded as replaced constructs rather than missing ones.

### 5.3 Copybooks (9) and their classification

| Artifact | Classification | Target representation |
|---|---|---|
| `CCPAURQY` | Interface contract — MQ request message (comma-delimited, 18 fields) | `AuthorizationRequestMessage` (a Java record, parsed from the Kafka payload) |
| `CCPAURLY` | Interface contract — MQ reply message (fixed-width) | `AuthorizationReplyMessage` (record + `toBuffer()` keeping the copybook widths) |
| `CCPAUERY` | Working-storage support — MQ/IMS error log area | structured logging fields; no persistence |
| `CIPAUSMY` | Database host structure — IMS segment `PAUTSUM0` | `AuthorizationSummaryEntity`, table `pending_auth_summary` |
| `CIPAUDTY` | Database host structure — IMS segment `PAUTDTL1` | `AuthorizationDetailEntity`, table `pending_auth_detail` |
| `IMSFUNCS` | Constants — DL/I function codes (`GU`, `GN`, `GNP`, `ISRT`, `REPL`, `DLET`, `CHKP`) | replaced by Spring Data repository methods; the mapping is in `09-cobol-to-java-mapping.md` |
| `PAUTBPCB.CPY` | Database host structure — PCB mask for the authorization database | replaced by the JPA `EntityManager`; status codes become exceptions |
| `PADFLPCB.CPY` | Database host structure — GSAM PCB, detail output file | replaced by the migration pipeline's writer |
| `PASFLPCB.CPY` | Database host structure — GSAM PCB, summary output file | replaced by the migration pipeline's writer |

None of the nine was converted into a persistence entity by default: only the two segment
structures are entities, the two message layouts are records, and the five remaining are platform
plumbing that is replaced rather than represented (playbook §8.3.9).

### 5.4 Screens (2)

| Artifact | Fields | Target |
|---|---|---|
| `bms/COPAU00.bms` + `cpy-bms/COPAU00.cpy` | account header, 8 summary metrics, 5 authorization rows × 6 fields, selection field, message line | React `AuthorizationSummaryScreen.tsx` |
| `bms/COPAU01.bms` + `cpy-bms/COPAU01.cpy` | account header, 21 authorization detail fields, fraud status, message line | React `AuthorizationDetailScreen.tsx` |

Both were read field by field; the mapping, including every retired PF key, is in
`06-screen-mapping.md`.

### 5.5 Data-store definitions (11)

| Artifact | Evidence extracted | Target |
|---|---|---|
| `ims/DBPAUTP0.dbd` | HIDAM database, `SEGM NAME=PAUTSUM0,PARENT=0,BYTES=100` keyed by `FIELD NAME=(ACCNTID,SEQ,U),START=1,BYTES=6,TYPE=P` (packed account id), child `SEGM NAME=PAUTDTL1,BYTES=200` keyed by `FIELD NAME=(PAUT9CTS,SEQ,U),BYTES=8,TYPE=C` (the complemented date+time), `POINTER=(TWINBWD)` | tables `pending_auth_summary` / `pending_auth_detail` with the foreign key and the complemented-key ordering |
| `ims/DBPAUTX0.dbd` | the HIDAM primary index database — `ACCESS=(INDEX,VSAM,PROT)`, `LCHILD NAME=(PAUTSUM0,DBPAUTP0),INDEX=ACCNTID`; it indexes the root key, it is not a card-number index | no artifact: PostgreSQL maintains the `pending_auth_summary` primary-key b-tree (UCR-29) |
| `ims/PSBPAUTB.psb` | `PCB TYPE=DB,PROCOPT=AP,KEYLEN=14`, both segments sensitive — named by both `CBPAUP0J.jcl` (`PARM='BMP,CBPAUP0C,PSBPAUTB'`) and `LOADPADB.JCL` (`PARM='BMP,PAUDBLOD,PSBPAUTB'`) | the purge job's read/update/delete access and the load step's insert access |
| `ims/PSBPAUTL.psb` | `PROCOPT=L`, `PSBGEN LANG=ASSEM` — a true load-mode PSB that **no in-scope JCL names**: `LOADPADB.JCL` runs the loader as a BMP under `PSBPAUTB` instead, so the load runs in update mode rather than load mode | the migration pipeline's load step, which likewise inserts into an existing schema rather than initial-loading it (see E-18) |
| `ims/PAUTBUNL.PSB` | `PROCOPT=GOTP` — unload PSB used by `PAUDBUNL` | the migration pipeline's extract step |
| `ims/DLIGSAMP.PSB` | `PROCOPT=GOTP` plus two `PCB TYPE=GSAM,PROCOPT=LS` (summary and detail output) — used by `DBUNLDGS` | the migration pipeline's file writer |
| `ims/PADFLDBD.DBD`, `ims/PASFLDBD.DBD` | GSAM DBDs for the detail and summary unload files (record lengths) | the extract file layouts in `05-data-mapping.md` |
| `ddl/AUTHFRDS.ddl` | DB2 table `CARDDEMO.AUTHFRDS`, 26 columns, `PRIMARY KEY(CARD_NUM,AUTH_TS)`, `TRANSACTION_AMT`/`APPROVED_AMT` `DECIMAL(12,2)`, `ACCT_ID DECIMAL(11)`, `CUST_ID DECIMAL(9)`, `POS_ENTRY_MODE SMALLINT`, `MERCHANT_NAME VARCHAR(22)` | table `auth_fraud`, same 26 columns and the same key |
| `ddl/XAUTHFRD.ddl` | `CREATE UNIQUE INDEX CARDDEMO.XAUTHFRD … (CARD_NUM ASC, AUTH_TS DESC)` | the `pk_auth_fraud` primary-key index (PostgreSQL scans a b-tree backwards, so the DESC declaration needs no counterpart) |
| `dcl/AUTHFRDS.dcl` | DCLGEN host structure — the COBOL types and null handling actually used by `COPAUS2C` | `FraudReportEntity` field types and nullability |

### 5.6 Transaction, queue and job definitions (6)

| Artifact | Evidence extracted | Target |
|---|---|---|
| `csd/CRDDEMO2.csd` | `TRANSACTION(CP00)`/`PROGRAM(COPAUA0C)`, `TRANSACTION(CPVS)`/`PROGRAM(COPAUS0C)`, `TRANSACTION(CPVD)`/`PROGRAM(COPAUS1C)`, `DB2ENTRY(AWS01PLN)` with `PLAN(AWS01PLN)`, `AUTHTYPE(USERID)`, `DROLLBACK(YES)`, and `DB2TRAN(CPVDTRAN)` | gateway routes and REST operations; the DB2 entry/tran pair is replaced by the HikariCP pool and `@Transactional` rollback. The engagement brief named this entry `DB201PLN`; the shipped CSD defines `AWS01PLN`, and the CSD is taken as authoritative |
| MQ `AWS.M2.CARDDEMO.PAUTH.REQUEST` | request queue drained by CP00 | topic `carddemo.authorization.request` |
| MQ `AWS.M2.CARDDEMO.PAUTH.REPLY` | default reply destination | topic `carddemo.authorization.reply` |
| `jcl/CBPAUP0J.jcl` | BMP step, `PARM='BMP,CBPAUP0C,PSBPAUTB'`, `SYSIN` parameter card `00,00001,00001,Y` | `PurgeAuthJobConfig` + `carddemo.batch.authpurge.*` properties |
| `jcl/DBPAUTP0.jcl` | IMS database define/initialise | Flyway `V1__create_authorization_schema.sql` |
| `jcl/LOADPADB.JCL`, `jcl/UNLDPADB.JCL`, `jcl/UNLDGSAM.JCL` | load and unload steps, DD layouts | the data migration pipeline in `05-data-mapping.md` |

### 5.7 Sample data (1)

`data/EBCDIC/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat` (51,736 bytes) is the EBCDIC image of the
authorization hierarchy, mixing summary and detail records with packed-decimal numeric fields. It
was parsed field by field against `CIPAUSMY`/`CIPAUDTY`; the conversion rules (code page 037,
packed-decimal decoding, trailing-blank preservation) and the reconciliation counts are in
`05-data-mapping.md`.

### 5.8 Coverage for this closure

| Type | In-scope analysed / total | Coverage |
|---|---|---|
| COBOL programs | 8 / 8 | 100% |
| Copybooks | 9 / 9 | 100% |
| BMS map source | 2 / 2 | 100% |
| BMS symbolic maps | 2 / 2 | 100% |
| IMS DBD/PSB | 8 / 8 | 100% |
| DB2 DDL/DCLGEN | 3 / 3 | 100% |
| JCL | 5 / 5 | 100% |
| CSD | 1 / 1 | 100% |
| EBCDIC data | 1 / 1 | 100% |
| Documentation | 1 / 1 | 100% |
| **Total** | **40 / 40** | **100%** |

Business-rule extraction covers 100% of the rule-carrying artifacts: all eight programs were read
line by line and 135 rules were catalogued (`BRE-Report-CARDDEMO-AUTHORIZATION.md`).

### 5.9 Excluded, missing or conflicting artifacts in this closure

| # | Artifact(s) | Category | Business impact | Technical impact | Disposition |
|---|---|---|---|---|---|
| E-12 | MQ queue *attributes* (trigger type, trigger depth, process definition, queue depth limits, persistence defaults) | Missing — the CSD names the queues but the MQ object definitions are not in the repository | The exact condition that started CP00, and therefore its concurrency and latency profile, cannot be reconstructed | Listener concurrency and topic partitioning are target-side decisions | Manual Review Required (UCR-17) |
| E-13 | The card status, account status and fraud-list data that would set the `4200`, `4300`, `5100`, `5200` decline reasons | Absent — no in-scope artifact sets them | Those four reasons cannot be produced by the source or the target | The reason codes are implemented and reachable in `DeclineReason`; nothing selects them | Manual Review Required (UCR-18) |
| E-14 | Profile data read by `5600-READ-PROFILE-DATA` | Absent — the paragraph is empty and no copybook or data store exists | None today | Not represented | Retired (UCR-20) |
| E-15 | `CEE3ABD` / IMS abend services used by `CBPAUP0C` | IBM-supplied, not in the repository | None for business behaviour | Replaced by a failed Spring Batch step and exit code | Replaced |
| E-16 | Production MQ message volumes, CP00 trigger statistics, `CBPAUP0J` schedule and elapsed times | Absent from the repository | Throughput and retention behaviour cannot be validated against production | Partition count, consumer concurrency and chunk size are documented defaults, not measured | Not applicable — recorded as a gap, see `19-test-strategy-and-quality-gates.md` §6 |
| E-18 | `ims/PSBPAUTL.psb` | Unused — `PROCOPT=L` load PSB with no JCL reference | none observed; the loader runs in update mode under `PSBPAUTB`, which is what the target reproduces | The target load step is an ordinary transactional insert, not a bulk initial load; for a full-volume migration a `COPY`-based bulk path may be needed instead | Manual Review Required — confirm whether a load-mode run exists in the production procedure set |
| E-17 | An authoritative value for the purge retention period | Conflicting — `CBPAUP0J` supplies `00` (purge everything) while the program's own default is 5 days | Running the shipped JCL unchanged would purge every authorization | The target default is the program's 5 days, and the value is a configuration property | Manual Review Required |

No file in the module was unreadable, and nothing was skipped. As in the account closure, there are
no functional or design specifications: the module `README.md` is the only non-code documentation, and
it corroborates the message contracts, the trigger path and the transaction names.
