# Legacy Discovery and Reverse Engineering

§1-§7 cover the account and user closure (VSAM/CICS/JCL). §8 covers the credit card authorization
closure (IMS DB, DB2, MQ, CICS, BMP).

## 1. Dependency map (textual)

```
CICS TRANSACTION CAVW ──► COACTVWC (941 lines)
  COPY  CVCRD01Y, COCOM01Y, DFHBMSCA*, DFHAID*, COTTL01Y, COACTVW(map),
        CSDAT01Y, CSMSG01Y, CSMSG02Y, CSUSR01Y, CVACT01Y, CVACT02Y, CVACT03Y,
        CVCUS01Y, CSSTRPFY
  READ  CXACAIX  (AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH)   ─┐
        ACCTDAT  (AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS)        ├─ read only
        CUSTDAT  (AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS)        ─┘
  XCTL  COMEN01C (menu, on exit) / CDEMO-FROM-PROGRAM (caller)
  ABEND '9999'

CICS TRANSACTION CAUP ──► COACTUPC (4,236 lines)
  COPY  CSUTLDWY, CVCRD01Y, CSLKPCDY, DFHBMSCA*, DFHAID*, COTTL01Y,
        COACTUP(map), CSDAT01Y, CSMSG01Y, CSMSG02Y, CSUSR01Y, CVACT01Y,
        CVACT03Y, CVCUS01Y, COCOM01Y, CSSETATY (x40), CSSTRPFY, CSUTLDPY
  CALL  CSUTLDTC ──► CEEDAYS*  (via CSUTLDPY EDIT-DATE-LE)
  READ  CXACAIX, ACCTDAT, CUSTDAT
  READ UPDATE + REWRITE  ACCTDAT, CUSTDAT
  SYNCPOINT ROLLBACK on customer rewrite failure
  XCTL  COMEN01C / caller
  ABEND '9999'

JOB READACCT ──► STEP PREDEL (IEFBR14, deletes 3 extracts)
             ──► STEP05 CBACT01C (430 lines)
  COPY  CVACT01Y, CODATECN
  CALL  COBDATFT*   (assembler, not in repository)
  DD    ACCTFILE = AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS   (input, sequential)
        OUTFILE  = …ACCTDATA.PSCOMP  LRECL 107 FB
        ARRFILE  = …ACCTDATA.ARRYPS  LRECL 110 FB
        VBRFILE  = …ACCTDATA.VBPS    LRECL 84  VB
  ABEND CEE3ABD* code 999

JOB ACCTFILE ──► IDCAMS DELETE/DEFINE CLUSTER KEYS(11 0) RECORDSIZE(300 300)
             ──► REPRO from AWS.M2.CARDDEMO.ACCTDATA.PS
JOB CUSTFILE ──► KEYS(9 0)  RECORDSIZE(500 500), REPRO from …CUSTDATA.PS
JOB XREFFILE ──► KEYS(16 0) RECORDSIZE(50 50) + AIX KEYS(11,25) + PATH,
                 REPRO from …CARDXREF.PS

MENU  COMEN01C ──► option table COMEN02Y
                   option 1 Account View   → COACTVWC, operator type 'U'
                   option 2 Account Update → COACTUPC, operator type 'U'
```
`*` = IBM-supplied or missing from the repository (see UCR-04, UCR-06, UCR-08).

## 2. COACTVWC — program discovery report

Divisions: IDENTIFICATION, ENVIRONMENT (empty), DATA (WORKING-STORAGE, LINKAGE), PROCEDURE.
Entry point: `DFHCOMMAREA` via CICS; `EIBCALEN = 0` distinguishes a direct start from a transfer.

| Paragraph | Line | Responsibility |
|---|---|---|
| `0000-MAIN` | 262 | state initialisation, function-key decision, entry/re-entry routing, abend on unknown state |
| `COMMON-RETURN` | 397 | save COMMAREA state, `RETURN TRANSID` |
| `1000-SEND-MAP` | 419 | orchestrate screen build |
| `1100-SCREEN-INIT` | 431 | titles, transaction/program name, current date and time |
| `1200-SETUP-SCREEN-VARS` | 460 | move account and customer values to the map, format SSN, choose the prompt message |
| `1300-SETUP-SCREEN-ATTRS` | 541 | cursor position, colour, asterisk redisplay, info-message attribute |
| `1400-SEND-SCREEN` | 577 | `SEND MAP` |
| `2000-PROCESS-INPUTS` | 596 | receive map, run edits, copy navigation state |
| `2100-RECEIVE-MAP` | 611 | `RECEIVE MAP` |
| `2200-EDIT-MAP-INPUTS` | 622 | asterisk/blank normalisation, call account edit, no-search-criteria state |
| `2210-EDIT-ACCOUNT` | 649 | account-number validation and message |
| `9000-READ-ACCT` | 687 | XREF → ACCT → CUST sequencing with short-circuit |
| `9200-GETCARDXREF-BYACCT` | 723 | read `CXACAIX`, NORMAL/NOTFND/OTHER |
| `9300-GETACCTDATA-BYACCT` | 774 | read `ACCTDAT`, NORMAL/NOTFND/OTHER |
| `9400-GETCUSTDATA-BYCUST` | 825 | read `CUSTDAT`, NORMAL/NOTFND/OTHER |
| `SEND-PLAIN-TEXT` | 895 | plain-text send used by the abend path |
| `ABEND-ROUTINE` | 916 | `ABEND ABCODE('9999')` |

Working storage of note: 88-level flag groups (`FLG-ACCTFILTER-*`, `FOUND-*`, `WS-PROMPT-*`,
`INPUT-ERROR`), literal group `LIT-*` (program/transaction/map/file names), `WS-FILE-ERROR-MESSAGE`,
`WS-RETURN-MSG`, `WS-INFO-MSG`. No `COMP-3` in this program's own storage; packed fields arrive
through `CVACT01Y`/`CVCUS01Y`. No `EXEC SQL`, no MQ, no sort, no scheduler constructs.

## 3. COACTUPC — program discovery report

44 paragraphs (full list with line numbers in `09-cobol-to-java-mapping.md`). Structure:

- `0000-MAIN` (859) — state machine over `ACUP-*` conversation states and the AID.
- `1000-PROCESS-INPUTS` (1025) → `1100-RECEIVE-MAP` (1039) — receive and normalise (asterisks,
  case, blanks) all 30 input fields.
- `1200-EDIT-MAP-INPUTS` (1429) — key edit, change detection, then the fixed-order field edits,
  delegating to `1210`-`1280` and to the `CSUTLDPY` date paragraphs.
- `2000-DECIDE-ACTION` (2562) — decide read / re-edit / write / return / abend.
- `3000-SEND-MAP` (2649) → `3100`-`3400` — screen build, three value-display variants (initial,
  original, updated), 40 `CSSETATY` attribute expansions, protect/unprotect.
- `9000-READ-ACCT` (3608) → `9200`/`9300`/`9400` — read sequence; `9500-STORE-FETCHED-DATA` (3801)
  captures the baseline.
- `9600-WRITE-PROCESSING` (3888) — lock, compare, build, rewrite account then customer, rollback.
- `9700-CHECK-CHANGE-IN-REC` (4109) — optimistic concurrency comparison.
- `ABEND-ROUTINE` (4203).

Working storage of note: `ACUP-OLD-*` / `ACUP-NEW-*` mirrored field groups (the source of change
detection and of the concurrency baseline), `WS-EDIT-*-FLGS` per field, `WS-NON-KEY-FLAGS` group
reset, the `CSLKPCDY` tables (`SEARCH ALL`), `CSUTLDWY` date work area. `REDEFINES` are used to view
each editable field both as text and as a number (for example `ACUP-NEW-CUST-FICO-SCORE-X`
redefining the numeric score) — reproduced in Java by holding the keyed text and converting only
after the numeric edit passes.

## 4. CBACT01C — program discovery report

Sequential-access indexed input file plus three output files; 20 paragraphs. Layouts and field
offsets are in `07-batch.md`. `OCCURS 5` array record (`ARR-ARRAY-REC`), `COMP-3` balances via
`CVACT01Y`, variable-length writes with explicit lengths.

## 5. Error handling and exceptional flows

| Program | Mechanism | Constructs |
|---|---|---|
| COACTVWC / COACTUPC | `RESP`/`RESP2` on every CICS command, `EVALUATE` over `DFHRESP(...)`, `WS-FILE-ERROR-MESSAGE`, `ABEND ABCODE('9999')`, `HANDLE ABEND` | no `HANDLE CONDITION`, no `NOHANDLE` in the in-scope paths |
| COACTUPC only | `SYNCPOINT ROLLBACK` after a failed customer rewrite | asymmetric with the account rewrite path (BR-50) |
| CBACT01C | `FILE STATUS` checks after every open/read/write/close, `DISPLAY` of the status, `CALL 'CEE3ABD'` with code 999 | abend is unconditional on any unexpected status |
| CSUTLDTC | feedback code severity from `CEEDAYS`, returned in `RETURN-CODE` | caller checks severity only |

## 6. Data lineage

```
acctdata.txt / …ACCTDATA.PS ─REPRO─► ACCT KSDS ─┬─ CAVW read ──► account fields on screen
                                                ├─ CAUP read/rewrite ──► updated account
                                                └─ READACCT sequential read ──► PSCOMP/ARRYPS/VBPS
cardxref.txt / …CARDXREF.PS ─REPRO─► CARDXREF KSDS + AIX(acct) ─► customer id + card number
custdata.txt / …CUSTDATA.PS ─REPRO─► CUSTOMER KSDS ─┬─ CAVW read ──► customer fields on screen
                                                    └─ CAUP read/rewrite ──► updated customer
```

Target equivalent: the same three inputs are loaded by Flyway V2 into `account`, `card_xref` and
`customer`; the enquiry and update read through `card_xref (xref_acct_id)`; the batch reads
`account` ordered by `acct_id`.

## 7. Constructs inventoried

| Construct | Count in scope | Handling |
|---|---|---|
| `EXEC CICS SEND MAP` / `RECEIVE MAP` | 6 | replaced by REST + React |
| `EXEC CICS READ` | 6 | repository finder methods |
| `EXEC CICS READ … UPDATE` | 2 | `SELECT … FOR UPDATE` (pessimistic write lock) |
| `EXEC CICS REWRITE` | 2 | JPA entity save inside one transaction |
| `EXEC CICS SYNCPOINT ROLLBACK` | 1 | Spring transaction rollback |
| `EXEC CICS XCTL` / `RETURN TRANSID` | 6 | client-side navigation |
| `EXEC CICS ABEND` / `HANDLE ABEND` | 4 | exception → HTTP 500 (UCR-08) |
| `CALL` (validation/format) | 2 (`CSUTLDTC`, `COBDATFT`) | `DateValidationService`, `CobdatftDateFormatter` |
| `SEARCH ALL` | 4 | `LookupTables` binary lookups |
| `COPY … REPLACING` | 40 | `FieldFlag` per screen field |
| `EXEC SQL` / IMS / MQ / sort / scheduler | 0 | none in the account and user programs — messaging is **NOT APPLICABLE** to that closure. The authorization closure does use IMS DB, DB2 and MQ; its inventory is §8.2 |

## 8. Credit Card Authorizations — discovery report

### 8.1 Dependency map (textual)

```
MQ AWS.M2.CARDDEMO.PAUTH.REQUEST --trigger(MQTM)--> CICS CP00 = COPAUA0C
                                                      |
   reads  CARDXREF / ACCTDAT / CUSTDAT (VSAM, owned by the account scope)
   reads/writes IMS DBPAUTP0: PAUTSUM0 (GU/REPL/ISRT) and PAUTDTL1 (ISRT)
   writes MQ AWS.M2.CARDDEMO.PAUTH.REPLY using MQMD-CORRELID / MQMD-REPLYTOQ
   SYNCPOINT spans IMS + MQ (two-phase)

CICS CPVS = COPAUS0C  --GU/GN/GNP on DBPAUTP0--> summary + 5 detail rows per page
      |  XCTL, key in COMMAREA (COCOM01Y-like area)
      v
CICS CPVD = COPAUS1C  --GU/GNP--> one detail, PF8 chains to the next
      |  LINK
      v
COPAUS2C --EXEC SQL INSERT/DELETE--> DB2 AUTHFRDS (+ index XAUTHFRD), plan DB201PLN,
                                     DB2ENTRY/DB2TRAN CPVDTRAN
      |  REPL on PAUTDTL1 to set the fraud flag

CBPAUP0J --DFSRRC00, PSB PSBPAUTB--> CBPAUP0C (BMP): GN roots, GNP children, DLET, CHKP

PAUDBUNL / DBUNLDGS --PSB PAUTBUNL--> EBCDIC unload files under data/EBCDIC
LOADPADB --PSB PSBPAUTL--> PAUDBLOD --ISRT--> DBPAUTP0
```

### 8.2 Constructs inventoried (authorization scope)

| Construct | Count | Target treatment |
|---|---|---|
| `EXEC DLI` calls | 26 across 4 programs (`GU`, `GN`, `GNP`, `ISRT`, `REPL`, `DLET`, `SCHD`, `TERM`) | Spring Data repositories; `SCHD`/`TERM` have no counterpart |
| `CBLTDLI` calls | 9 in the load/unload utilities | the migration pipeline (`tools/ims_authorization_unload.py`) |
| `EXEC SQL` | 4 in `COPAUS2C` | `FraudReportRepository` |
| MQ API calls | `MQCONN`-less CICS bridge: 1 `MQGET`, 1 `MQPUT1`, 1 `MQOPEN`, 1 `MQCLOSE` plus the `MQMD`/`MQGMO`/`MQPMO` option blocks | `@KafkaListener` / `KafkaTemplate` with headers (see `23-messaging-modernization-mapping.md`) |
| `EXEC CICS SEND MAP` / `RECEIVE MAP` | `COPAU00`, `COPAU01` | React screens + JSON DTOs |
| `EXEC CICS XCTL` / `RETURN TRANSID` / `LINK` | CP00/CPVS/CPVD chain | client-side navigation; `LINK` becomes an in-process service call |
| `EXEC CICS SYNCPOINT` (two-phase over IMS + DB2 + MQ) | 3 | one local transaction + transactional outbox (CMD-10 … CMD-15) |
| PCB masks / status-code checks | `PADFLPCB`, `PASFLPCB`, `PAUTBPCB` | `Optional` / exception; abend paths unexercisable (see `10-testing-and-parity.md` §8) |
| JCL steps | `CBPAUP0J`, `DBPAUTP0`, `LOADPADB` | Spring Batch job, Flyway DDL, Flyway seed |
| Scheduler definitions | 0 — no Control-M/CA-7 member is supplied | the purge job's schedule is a documented assumption (`21-cutover-runbook.md`) |
| Security definitions | 0 — no RACF member is supplied for CP00/CPVS/CPVD | the existing JWT roles are reused (`18-coding-standards-checklist.md` §7) |

### 8.3 Error handling and exceptional flows

| Source flow | Target |
|---|---|
| `COPAUA0C`: a card, account or customer read that returns not-found declines with `3100` and still replies | `AuthorizationDecisionEngine` — a business decline, not an error |
| `COPAUA0C`: an MQ or DL/I failure abends after the `SYNCPOINT`, leaving the message on the queue | the transaction rolls back and the record is retried, then dead-lettered (CMD-12) |
| `COPAUS0C`/`COPAUS1C`: `GE` on the first read shows the empty-summary screen with zero metrics | `AuthorizationInquiryService` returns the zeroed response |
| `COPAUS0C`/`COPAUS1C`: any other DL/I status writes the status to the message line and returns | `ScreenValidationException` / 500 with the same message text where one exists |
| `COPAUS2C`: a non-zero `SQLCODE` is returned to the caller in the error field | `FraudMarkingService` propagates; the screen shows the source's message |
| `CBPAUP0C`: unexpected root or child status abends with RETURN-CODE 16 | step failure, non-zero exit status |

### 8.4 Data lineage

```
data/EBCDIC/*.unload  --decode_authorization_unload.py-->  V2 seed SQL
                                                             |
PAUTSUM0 root  ------------------------------------------>  pending_auth_summary
PAUTDTL1 child ------------------------------------------>  pending_auth_detail (FK to the summary)
AUTHFRDS       ------------------------------------------>  authorization_fraud_report
PAUTINDX       ------------------------------------------>  no counterpart — the primary key b-tree
                                                            replaces the HIDAM index (UCR-29)
CARDXREF/ACCTDAT/CUSTDAT ------------------------------->  read through the account service API,
                                                            never from this service's database
```

The reconciliation of this lineage is `tools/reconcile_authorization_data.py`
(`evidence/authorization-data-reconciliation.txt`).
