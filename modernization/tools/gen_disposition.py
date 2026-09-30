"""Generate the Source Artifact Disposition report.

Enumerates every artifact under ``app/`` and assigns exactly one disposition, so that no
discovered source artifact can be silently ignored. In-scope decisions are keyed by file name;
everything else falls back to the out-of-capability-scope disposition.
"""

from __future__ import annotations

import pathlib

APP = pathlib.Path(__file__).resolve().parents[2] / "app"
AUTH_MODULE = "app-authorization-ims-db2-mq"
OUT = pathlib.Path(__file__).resolve().parents[1] / "docs" / "13-artifact-disposition.md"

DIRS: list[tuple[str, str]] = [
    ("cbl", "COBOL program"),
    ("cpy", "Copybook"),
    ("bms", "BMS map source"),
    ("cpy-bms", "BMS symbolic map"),
    ("jcl", "JCL"),
    ("proc", "PROC"),
    ("csd", "CICS resource definition"),
    ("catlg", "Catalog listing"),
    ("data/ASCII", "ASCII sample data"),
    ("data/EBCDIC", "EBCDIC sample data"),
    (f"{AUTH_MODULE}/cbl", "COBOL program"),
    (f"{AUTH_MODULE}/cpy", "Copybook"),
    (f"{AUTH_MODULE}/bms", "BMS map source"),
    (f"{AUTH_MODULE}/cpy-bms", "BMS symbolic map"),
    (f"{AUTH_MODULE}/ims", "IMS DBD/PSB source"),
    (f"{AUTH_MODULE}/ddl", "DB2 DDL"),
    (f"{AUTH_MODULE}/dcl", "DB2 DCLGEN"),
    (f"{AUTH_MODULE}/jcl", "JCL"),
    (f"{AUTH_MODULE}/csd", "CICS resource definition"),
    (f"{AUTH_MODULE}/data/EBCDIC", "EBCDIC sample data"),
]

FILES: list[tuple[str, str]] = [
    (f"{AUTH_MODULE}/README.md", "Module documentation"),
]

OUT_OF_SCOPE = (
    "Manual Review Required",
    "Discovered under `app/` but not covered by an explicit conversion decision. Every artifact is "
    "expected to carry one, so this value means the decision table needs an entry: review before "
    "release.",
)

# name -> (disposition, rationale)
DECISIONS: dict[str, tuple[str, str]] = {
    "COACTVWC.cbl": ("Migrated", "Converted to `AccountViewService`/`AccountReadService`/`AccountViewController` + React `AccountViewScreen`. All 23 rules implemented and tested."),
    "COACTUPC.cbl": ("Migrated", "Converted to `AccountUpdateService`/`AccountUpdateValidator`/`AccountChangeDetector`/`FieldEditor`/`AccountUpdateController` + React `AccountUpdateScreen`. All 56 rules implemented and tested."),
    "CBACT01C.cbl": ("Migrated", "Converted to Spring Batch job `readAcctJob` with three record aggregators reproducing LRECL 107 FB, 110 FB and 84 VB."),
    "CSUTLDTC.cbl": ("Refactored", "Severity/message-code contract and the ten outcome texts preserved in `DateValidationService`; the `CEEDAYS` call is replaced by strict `java.time` parsing."),
    "COMEN01C.cbl": ("Migrated", "Converted to `MenuAccessService` + React `MenuScreen`; all eleven options of the supplied table, the option-number validation and the admin-only access rule with its `No access - Admin Only option...` message are preserved."),
    "COSGN00C.cbl": ("Migrated", "Converted to `SignOnService`/`SignOnController` + React `SignOnScreen`; the USRSEC read, the uppercase folding, both rejection messages and the COADM01C/COMEN01C routing are preserved, with the token replacing the COMMAREA. Inherited credential weaknesses registered as UCR-14."),
    "COADM01C.cbl": ("Migrated", "Converted to `AdminMenuService`/`AdminMenuController` + React `AdminMenuScreen`; the six-option table and the option validation are preserved."),
    "COUSR00C.cbl": ("Migrated", "Converted to `UserListService` + React `UserListScreen`; ten rows a page, keyed FIRST/NEXT/PREVIOUS browsing and the U/D selection rule are preserved."),
    "COUSR01C.cbl": ("Migrated", "Converted to `UserAdminService.add` + React `UserAddScreen`; the five required-field checks in map order and the duplicate-key message are preserved."),
    "COUSR02C.cbl": ("Migrated", "Converted to `UserAdminService.update` + React `UserUpdateScreen`; the read, the field comparison and the no-change message are preserved."),
    "COUSR03C.cbl": ("Migrated", "Converted to `UserAdminService.delete` + React `UserDeleteScreen`; the PF5 confirmation and the source's `Unable to Update User...` failure wording are preserved (UCR-15, UCR-16)."),
    "CVACT01Y.cpy": ("Migrated", "Account master layout → `AccountEntity` / table `account`, field by field (`05-data-mapping.md`)."),
    "CVACT02Y.cpy": ("Migrated", "Card master layout → `CardEntity` / table `card`, field by field, now that the card list, view and update flows are converted."),
    "CVACT03Y.cpy": ("Migrated", "Cross-reference layout → `CardXrefEntity` / table `card_xref` including the account access path."),
    "CVCUS01Y.cpy": ("Migrated", "Customer master layout → `CustomerEntity` / table `customer`, field by field."),
    "CVCRD01Y.cpy": ("Refactored", "Card/account key work area replaced by explicit method parameters and DTO fields."),
    "COCOM01Y.cpy": ("Replaced", "COMMAREA transport replaced by explicit REST request/response state (playbook §4.5). Business content preserved in the DTOs."),
    "COMEN02Y.cpy": ("Migrated", "Menu option table reproduced entry for entry in `MenuAccessService`: the eleven option names, their programs and their operator types."),
    "COTTL01Y.cpy": ("Migrated", "Screen titles reproduced in React `ScreenFrame`."),
    "CSDAT01Y.cpy": ("Replaced", "Date/time work area replaced by `java.time` with an injectable `Clock`."),
    "CSMSG01Y.cpy": ("Migrated", "Common message literals reproduced verbatim in `ScreenMessages`."),
    "CSMSG02Y.cpy": ("Refactored", "ABEND message area mapped to the structured error response produced by `ApiExceptionHandler`."),
    "CSUSR01Y.cpy": ("Migrated", "User security record layout → `SecurityUserEntity` / table `security_user` in the identity service, the 8/20/20/8/1 fixed widths preserved as `CHAR` columns."),
    "COADM02Y.cpy": ("Migrated", "Admin menu option table reproduced verbatim in `AdminMenuService`, including the commented-out option name recorded in UCR-16."),
    "CSLKPCDY.cpy": ("Migrated", "Area code, state code, state+zip and Y/N tables reproduced verbatim in `LookupTables`."),
    "CSUTLDPY.cpy": ("Migrated", "Date edit paragraphs converted to `DateEditor` with identical messages and ordering."),
    "CSUTLDWY.cpy": ("Migrated", "Date edit work area and flags → `DateEditResult` / `DateValidationResult`."),
    "CSSETATY.cpy": ("Replaced", "Terminal attribute macro replaced by `FieldFlag` plus UI styling; highlighting semantics preserved."),
    "CSSTRPFY.cpy": ("Replaced", "AID/function-key mapping replaced by distinct REST operations (transport only, no business rule)."),
    "CODATECN.cpy": ("Migrated", "`COBDATFT` interface reproduced in `CobdatftDateFormatter` (see UCR-04 for the missing implementation)."),
    "COACTVW.bms": ("Migrated", "Field-for-field conversion to `AccountViewScreen.tsx` (`06-screen-mapping.md`)."),
    "COACTUP.bms": ("Migrated", "Field-for-field conversion to `AccountUpdateScreen.tsx` (`06-screen-mapping.md`)."),
    "COMEN01.bms": ("Migrated", "Field-for-field conversion to `MenuScreen.tsx`, including all eleven option lines and the message line."),
    "COSGN00.bms": ("Migrated", "Field-for-field conversion to `SignOnScreen.tsx`."),
    "COADM01.bms": ("Migrated", "Field-for-field conversion to `AdminMenuScreen.tsx`."),
    "COUSR00.bms": ("Migrated", "Ten-row list map converted to `UserListScreen.tsx`."),
    "COUSR01.bms": ("Migrated", "Field-for-field conversion to `UserAddScreen.tsx`."),
    "COUSR02.bms": ("Migrated", "Field-for-field conversion to `UserUpdateScreen.tsx`."),
    "COUSR03.bms": ("Migrated", "Field-for-field conversion to `UserDeleteScreen.tsx`."),
    "COACTVW.CPY": ("Migrated", "Symbolic map fields → `AccountViewResponse` / React field props."),
    "COACTUP.CPY": ("Migrated", "Symbolic map fields → `AccountUpdateForm` / React field props."),
    "COMEN01.CPY": ("Migrated", "Symbolic map fields → `MenuResponse` options and the selection field."),
    "COSGN00.CPY": ("Migrated", "Symbolic map fields → `SignOnRequest` / `SignOnResponse`."),
    "COADM01.CPY": ("Migrated", "Symbolic map fields → `MenuOptionResponse` / React field props."),
    "COUSR00.CPY": ("Migrated", "Symbolic map fields → `UserListResponse` rows and the selection field."),
    "COUSR01.CPY": ("Migrated", "Symbolic map fields → `UserAddForm`."),
    "COUSR02.CPY": ("Migrated", "Symbolic map fields → `UserUpdateForm` / `UserDetail`."),
    "COUSR03.CPY": ("Migrated", "Symbolic map fields → `UserDetail`."),
    "READACCT.jcl": ("Migrated", "Job and its three output DDs converted to `readAcctJob`; PREDEL reproduced by recreating the output files on a fresh run."),
    "ACCTFILE.jcl": ("Migrated", "DELETE/DEFINE/REPRO converted to Flyway V1 (schema) + V2 (seed) and the documented single-command reset."),
    "CUSTFILE.jcl": ("Migrated", "CUSTOMER KSDS definition and load converted to table `customer` and its seed load."),
    "DUSRSECJ.jcl": ("Migrated", "USRSEC KSDS definition (`KEYS(8,0)`, `RECORDSIZE(80,80)`) and its REPRO load converted to identity `V1__create_identity_schema.sql` + `V2__load_usrsec_data.sql`."),
    "AWS.M2.CARDDEMO.USRSEC.PS": ("Migrated", "The ten EBCDIC user records decoded field by field and loaded as identity seed data by Flyway V2."),
    "XREFFILE.jcl": ("Migrated", "CARDXREF KSDS, its account alternate index and PATH converted to table `card_xref` plus `ix_card_xref_acct_id`."),
    "OPENFIL.jcl": ("Retired", "CEMT file open procedure; a relational database needs no equivalent. No business rule."),
    "CLOSEFIL.jcl": ("Retired", "CEMT file close procedure; no target equivalent required. No business rule."),
    "CARDFILE.jcl": ("Migrated", "CARDDAT KSDS definition and its REPRO load converted to table `card` and its Flyway seed load."),
    "CARDDEMO.CSD": ("Consolidated", "In-scope FILE/PROGRAM/TRANSACTION definitions (ACCTDAT, CUSTDAT, CXACAIX, CAVW, CAUP) consolidated into the JPA/Flyway model and the REST operations; out-of-scope definitions untouched."),
    "LISTCAT.txt": ("Consolidated", "Key positions and record sizes used to corroborate the JCL definitions; folded into `05-data-mapping.md`."),
    "acctdata.txt": ("Migrated", "Loaded as seed data by Flyway V2 and reconciled record by record."),
    "custdata.txt": ("Migrated", "Loaded as seed data by Flyway V2 and reconciled record by record."),
    "cardxref.txt": ("Migrated", "Loaded as seed data by Flyway V2 and reconciled record by record."),
    "AWS.M2.CARDDEMO.ACCTDATA.PS": ("Consolidated", "EBCDIC copy used as the reconciliation reference for the ASCII load; one record differs (UCR-01, Manual Review Required for that record)."),
    "AWS.M2.CARDDEMO.CUSTDATA.PS": ("Consolidated", "EBCDIC copy reconciled against the ASCII load; no differences."),
    "AWS.M2.CARDDEMO.CARDXREF.PS": ("Consolidated", "EBCDIC copy reconciled against the ASCII load; no differences."),
    # --- Transaction, credit card, statement and utility closure ----------------------------
    "COTRN00C.cbl": ("Migrated", "Converted to `TransactionListService` + `TransactionController` + React `TransactionListScreen`; ten rows a page, the transaction-id filter, the F7/F8 keyset browsing, the selection rule and every screen message are preserved, with the COMMAREA page state carried as request parameters (§8.4.5)."),
    "COTRN01C.cbl": ("Migrated", "Converted to `TransactionViewService` + React `TransactionViewScreen`; the transaction-id edit, the `Transaction ID NOT found...` message and the field-by-field display are preserved."),
    "COTRN02C.cbl": ("Migrated", "Converted to `TransactionAddService` + React `TransactionAddScreen`; the account/card cross-reference lookup, the field edits in map order, the amount and date formats, the generated transaction id and the two-step confirmation are preserved."),
    "COBIL00C.cbl": ("Migrated", "Converted to `BillPaymentService` + React `BillPaymentScreen`; the balance read, the zero-balance refusal, the confirmation step and the transaction written for the full balance are preserved. The single unit of recovery over ACCTDAT and TRANSACT becomes a local transaction plus an idempotent, compensated call to the account service (CMD-14)."),
    "CORPT00C.cbl": ("Migrated", "Converted to `ReportRequestService` + React `ReportRequestScreen`; the monthly/yearly/custom selection, the date edits and the confirmation are preserved. The `TRANREPT` job submission through the internal reader becomes a persisted report request consumed by `transactionReportJob` (CMD-15)."),
    "COCRDLIC.cbl": ("Migrated", "Converted to `CardListService` + React `CardListScreen`; seven rows a page, the account/card filters, the admin and regular operator paths, the keyset browsing and the selection rules are preserved."),
    "COCRDSLC.cbl": ("Migrated", "Converted to `CardDetailService` + React `CardViewScreen`; the account and card number edits and the not-found messages are preserved."),
    "COCRDUPC.cbl": ("Migrated", "Converted to `CardUpdateService` + React `CardUpdateScreen`; the field edits, the change detection, the confirmation step and the optimistic concurrency check on the stored record are preserved."),
    "CBTRN01C.cbl": ("Migrated", "Converted to Spring Batch job `verifyDailyTransactionsJob`; the daily transaction read, the cross-reference, account and category-balance lookups and the file-status abend paths are preserved."),
    "CBTRN02C.cbl": ("Migrated", "Converted to Spring Batch job `postTransactionsJob`; the validation order, the four rejection reasons, the category-balance accumulation, the account balance and cycle updates and the reject file are preserved."),
    "CBTRN03C.cbl": ("Migrated", "Converted to Spring Batch job `transactionReportJob`; the 133-character report lines, the page, account and grand totals and the date-range selection are preserved by `ReportLines`."),
    "CBACT02C.cbl": ("Migrated", "Converted to Spring Batch job `printCardFileJob`; the sequential read and the record print layout are preserved by `MasterFileRecords`."),
    "CBACT03C.cbl": ("Migrated", "Converted to Spring Batch job `printCardXrefFileJob`, with the same record print layout."),
    "CBACT04C.cbl": ("Migrated", "Converted to Spring Batch job `interestCalculationJob`; the disclosure-group lookup with its `DEFAULT` fallback, the monthly interest computation, the interest transaction written per category and the balance update are preserved."),
    "CBCUS01C.cbl": ("Migrated", "Converted to Spring Batch job `printCustomerFileJob`, with the same record print layout."),
    "CBSTM03A.CBL": ("Migrated", "Converted to Spring Batch job `accountStatementJob`; the plain-text 80-character statement and the 100-character HTML statement are reproduced line for line by `StatementLines` and `StatementHtmlLines`. The cross-file read of customer and account data becomes a read of the account service's statement-party endpoint (CMD-16)."),
    "CBSTM03B.CBL": ("Consolidated", "The called file-handling subroutine has no separate target artifact: its open/read/close contract and the status codes it returns are provided by the Spring Batch readers of `accountStatementJob`."),
    "CBEXPORT.cbl": ("Migrated", "Converted to `exportBranchMigrationJob` (customer, account, cross-reference and card) and `exportBranchMigrationTransactionsJob` (transactions); the 500-byte record, the 40-byte header, the `C`/`A`/`X`/`T`/`D` types and the binary, packed and zoned encodings are preserved. The hard-coded branch `0001` and region `NORTH` are carried over and flagged for sign-off, and the single global sequence becomes one sequence per context (CMD-17)."),
    "CBIMPORT.cbl": ("Migrated", "Converted to `importBranchMigrationJob`; the type dispatch, the fixed-format master output, the unknown-record error file with the source's `Unknown record type encountered` text and the fatal truncated record are preserved, including the intentionally empty validation step (UCR-31)."),
    "COBSWAIT.cbl": ("Retired", "Batch wait utility whose only function is an MVS `WAIT` for the centiseconds in the PARM; a scheduler dependency replaces it and no business rule is carried."),
    "COSTM01.CPY": ("Migrated", "Statement print layout reproduced line for line in `StatementLines`; the 80-character record and every literal are preserved."),
    "CUSTREC.cpy": ("Migrated", "Statement customer record layout → the `StatementParty` projection read from the account service."),
    "CVEXPORT.cpy": ("Migrated", "Branch migration record layout → `MigrationExportRecord` / `MigrationExportHeader`; the 500-byte length, the 40-byte data offset and every field width are preserved."),
    "CVTRA01Y.cpy": ("Migrated", "Transaction category balance layout → `TransactionCategoryBalanceEntity` / table `transaction_category_balance`."),
    "CVTRA02Y.cpy": ("Migrated", "Disclosure group layout → `DisclosureGroupEntity` / table `disclosure_group`, including the `DEFAULT` group key."),
    "CVTRA03Y.cpy": ("Migrated", "Transaction type layout → `TransactionTypeEntity` / table `transaction_type`."),
    "CVTRA04Y.cpy": ("Migrated", "Transaction category layout → `TransactionCategoryEntity` / table `transaction_category`."),
    "CVTRA05Y.cpy": ("Migrated", "Transaction master layout → `TransactionEntity` / table `transaction`, field by field including the 16-character key."),
    "CVTRA06Y.cpy": ("Migrated", "Daily transaction layout → `DailyTransaction` with its fixed-width reader."),
    "CVTRA07Y.cpy": ("Migrated", "Daily rejected transaction layout → `DailyTransactionRejectEntity` and the reject file record."),
    "UNUSED1Y.cpy": ("Retired", "Declared in the source tree but referenced by no program; recorded and not converted."),
    "COBIL00.bms": ("Migrated", "Field-for-field conversion to `BillPaymentScreen.tsx`."),
    "COTRN00.bms": ("Migrated", "Ten-row list map converted to `TransactionListScreen.tsx`."),
    "COTRN01.bms": ("Migrated", "Field-for-field conversion to `TransactionViewScreen.tsx`."),
    "COTRN02.bms": ("Migrated", "Field-for-field conversion to `TransactionAddScreen.tsx`."),
    "CORPT00.bms": ("Migrated", "Field-for-field conversion to `ReportRequestScreen.tsx`."),
    "COCRDLI.bms": ("Migrated", "Seven-row list map converted to `CardListScreen.tsx`."),
    "COCRDSL.bms": ("Migrated", "Field-for-field conversion to `CardViewScreen.tsx`."),
    "COCRDUP.bms": ("Migrated", "Field-for-field conversion to `CardUpdateScreen.tsx`."),
    "COBIL00.CPY": ("Migrated", "Symbolic map fields → `BillPaymentRequest` / `BillPaymentResponse`."),
    "COTRN00.CPY": ("Migrated", "Symbolic map fields → `TransactionListResponse` rows and the filter and selection fields."),
    "COTRN01.CPY": ("Migrated", "Symbolic map fields → `TransactionDetailResponse`."),
    "COTRN02.CPY": ("Migrated", "Symbolic map fields → `TransactionAddRequest` / `TransactionAddResponse`."),
    "CORPT00.CPY": ("Migrated", "Symbolic map fields → `ReportRequestForm` / `ReportRequestResponse`."),
    "COCRDLI.CPY": ("Migrated", "Symbolic map fields → `CardListResponse` rows and the filter and selection fields."),
    "COCRDSL.CPY": ("Migrated", "Symbolic map fields → `CardDetailResponse`."),
    "COCRDUP.CPY": ("Migrated", "Symbolic map fields → `CardUpdateRequest` / `CardUpdateResponse`."),
    "TRANFILE.jcl": ("Migrated", "TRANSACT KSDS definition and load converted to table `transaction` and its Flyway seed load."),
    "TRANIDX.jcl": ("Migrated", "Transaction alternate index and PATH converted to the card-number index used by the statement and report reads."),
    "TRANCATG.jcl": ("Migrated", "TRANCATG KSDS definition and load converted to table `transaction_category` and its seed load."),
    "TRANTYPE.jcl": ("Migrated", "TRANTYPE KSDS definition and load converted to table `transaction_type` and its seed load."),
    "TCATBALF.jcl": ("Migrated", "TCATBALF KSDS definition and load converted to table `transaction_category_balance` and its seed load."),
    "DISCGRP.jcl": ("Migrated", "DISCGRP KSDS definition and load converted to table `disclosure_group` and its seed load."),
    "DALYREJS.jcl": ("Migrated", "Reject file definition converted to the `postTransactionsJob` reject output and `daily_transaction_reject`."),
    "POSTTRAN.jcl": ("Migrated", "Posting job and its DDs converted to `postTransactionsJob`."),
    "INTCALC.jcl": ("Migrated", "Interest job and its DDs converted to `interestCalculationJob`."),
    "TRANREPT.jcl": ("Migrated", "Report job, its date parameter cards and its output DD converted to `transactionReportJob` and the persisted report request."),
    "TRANREPT.prc": ("Migrated", "The report procedure's steps and DD substitutions are folded into `transactionReportJob`; the PROC/symbolic mechanism has no target counterpart."),
    "REPTFILE.jcl": ("Migrated", "Report output file definition converted to the report writer's output file."),
    "CREASTMT.JCL": ("Migrated", "Statement job and its plain-text and HTML output DDs converted to `accountStatementJob`."),
    "COMBTRAN.jcl": ("Consolidated", "The daily/master transaction merge is performed by the posting job reading the daily file and writing the transaction table; the GDG concatenation has no target counterpart."),
    "CBEXPORT.jcl": ("Migrated", "Export job and its 500-byte output DD converted to `exportBranchMigrationJob` and `exportBranchMigrationTransactionsJob`."),
    "CBIMPORT.jcl": ("Migrated", "Import job, its input DD and its error DD converted to `importBranchMigrationJob`."),
    "READCARD.jcl": ("Migrated", "Card print job (`CBACT02C`) and its report DD converted to `printCardFileJob`."),
    "READXREF.jcl": ("Migrated", "Cross-reference print job (`CBACT03C`) converted to `printCardXrefFileJob`."),
    "READCUST.jcl": ("Migrated", "Customer print job (`CBCUS01C`) converted to `printCustomerFileJob`."),
    "PRTCATBL.jcl": ("Retired", "IDCAMS backup and DFSORT print of the category balance file; the data is a table in the target and the sort/report utility steps carry no business rule."),
    "DEFCUST.jcl": ("Migrated", "Statement customer file definition converted to the statement-party read from the account service (CMD-16)."),
    "WAITSTEP.jcl": ("Retired", "Runs `COBSWAIT` to pause between steps; step sequencing is the scheduler's responsibility in the target and the job carries no business rule."),
    "TRANBKP.jcl": ("Retired", "IDCAMS backup of the transaction KSDS; database backup replaces it and no business rule is carried."),
    "DEFGDGB.jcl": ("Retired", "GDG base definition for the transaction backups; generation data groups have no target counterpart."),
    "DEFGDGD.jcl": ("Retired", "GDG base definition for the daily transaction files; as above."),
    "ESDSRRDS.jcl": ("Retired", "Defines ESDS/RRDS scratch files used by no analysed program."),
    "FTPJCL.JCL": ("Retired", "Transmits the produced statement files off-platform; file delivery is an operational concern outside the converted application."),
    "TXT2PDF1.JCL": ("Retired", "Converts the plain-text statement to PDF with a site utility that is not supplied; the statement content itself is converted and the PDF rendering is out of scope (UCR-32)."),
    "INTRDRJ1.JCL": ("Replaced", "Internal-reader submission skeleton used by `CORPT00C`; replaced by the persisted report request that `transactionReportJob` consumes (CMD-15)."),
    "INTRDRJ2.JCL": ("Replaced", "Second internal-reader skeleton, replaced by the same mechanism."),
    "CBADMCDJ.jcl": ("Retired", "Administrative card job that no analysed flow submits; inventoried and not converted."),
    "REPROC.prc": ("Consolidated", "Generic IDCAMS REPRO procedure used by the file-load jobs; its function is carried by the Flyway seed migrations."),
    "carddata.txt": ("Migrated", "Loaded as seed data by Flyway and reconciled record by record."),
    "dailytran.txt": ("Migrated", "Input of `verifyDailyTransactionsJob` and `postTransactionsJob`, read with the source's fixed-width layout."),
    "discgrp.txt": ("Migrated", "Loaded as transaction seed data by Flyway and reconciled record by record."),
    "tcatbal.txt": ("Migrated", "Loaded as transaction seed data by Flyway and reconciled record by record."),
    "trancatg.txt": ("Migrated", "Loaded as transaction seed data by Flyway and reconciled record by record."),
    "trantype.txt": ("Migrated", "Loaded as transaction seed data by Flyway and reconciled record by record."),
    "AWS.M2.CARDDEMO.CARDDATA.PS": ("Consolidated", "EBCDIC copy reconciled against the ASCII load; no differences."),
    "AWS.M2.CARDDEMO.DALYTRAN.PS": ("Consolidated", "EBCDIC copy of the daily transaction file, decoded to corroborate the ASCII input of the posting jobs."),
    "AWS.M2.CARDDEMO.DALYTRAN.PS.INIT": ("Consolidated", "Initial-state copy of the same file, used to re-run the posting jobs from a known input."),
    "AWS.M2.CARDDEMO.DISCGRP.PS": ("Consolidated", "EBCDIC copy reconciled against the ASCII load; no differences."),
    "AWS.M2.CARDDEMO.TCATBALF.PS": ("Consolidated", "EBCDIC copy reconciled against the ASCII load; no differences."),
    "AWS.M2.CARDDEMO.TRANCATG.PS": ("Consolidated", "EBCDIC copy reconciled against the ASCII load; no differences."),
    "AWS.M2.CARDDEMO.TRANTYPE.PS": ("Consolidated", "EBCDIC copy reconciled against the ASCII load; no differences."),
    "AWS.M2.CARDDEMO.EXPORT.DATA.PS": ("Consolidated", "EBCDIC sample of the 500-byte export file, used as the reference for the converted record codec."),
    "AWS.M2.CARDDEMO.ACCDATA.PS": ("Consolidated", "Second EBCDIC copy of the account file; the difference against `ACCTDATA.PS` is UCR-01."),
    # --- Credit Card Authorizations closure -------------------------------------------------
    "COPAUA0C.cbl": ("Migrated", "Converted to `AuthorizationRequestListener` + `AuthorizationRequestProcessor` + `AuthorizationDecisionEngine` + `AuthorizationReplyPublisher`; the MQ trigger becomes a Kafka listener, the reply becomes a transactional-outbox publication (CMD-11), and the decision, the ten decline literals, the complemented keys and the counter updates are preserved. All 27 rules implemented and tested."),
    "COPAUS0C.cbl": ("Migrated", "Converted to `AuthorizationInquiryService.summary` + `AuthorizationController` + React `AuthorizationSummaryScreen`; five rows a page, the account-id edits, the S/s selection rule and all three navigation messages are preserved, with PF7/PF8 replaced by a keyset query parameter (§8.4.5)."),
    "COPAUS1C.cbl": ("Migrated", "Converted to `AuthorizationInquiryService.detail` + React `AuthorizationDetailScreen`; the MM/DD/YY, HH:MM:SS, MM/YY and fraud-status formatting and the next-authorization walk are preserved."),
    "COPAUS2C.cbl": ("Migrated", "Converted to `FraudMarkingService` + `POST /api/authorizations/{accountId}/{authKey}/fraud`; the DB2 `AUTHFRDS` insert/delete becomes `auth_fraud` in this service's own database and the confirmed→removed / other→confirmed toggle is preserved (CMD-13)."),
    "CBPAUP0C.cbl": ("Migrated", "Converted to Spring Batch job `purgeExpiredAuthorizationsJob`; the GN/GNP scan becomes a keyset reader plus child walk, the IMS CHKP becomes the chunk commit, the expiry arithmetic and the RC=16 abend path are preserved. The discarded counter reversal is remediated behind a switch (UCR-27)."),
    "PAUDBLOD.CBL": ("Refactored", "Its function — loading the authorization hierarchy from the sequential extracts — is performed by the Flyway seed migration and `tools/reconcile_sample_data.py` against `carddemo_authorization`; the silent skip of non-numeric root keys is reported instead of swallowed (UCR-28)."),
    "PAUDBUNL.CBL": ("Refactored", "Unload of the hierarchy to GSAM is replaced by the reconciliation extract used to compare source and target rows; no IMS/GSAM equivalent is needed."),
    "DBUNLDGS.CBL": ("Refactored", "GSAM unload driver folded into the same reconciliation extract; the PCB/GSAM plumbing has no target counterpart."),
    "CCPAURQY.cpy": ("Migrated", "MQ request layout → `AuthorizationRequestMessage` record with its fixed-width parser; field order, widths and the implied decimal are preserved."),
    "CCPAURLY.cpy": ("Migrated", "MQ reply layout → `AuthorizationReplyMessage` record with the same field order and widths."),
    "CCPAUERY.cpy": ("Migrated", "The decline-reason table → `DeclineReason` enum, the ten code/text literals verbatim."),
    "CIPAUSMY.cpy": ("Migrated", "IMS segment layout PAUTSUM0 → `AuthorizationSummaryEntity` / table `pending_auth_summary`, field by field (`05-data-mapping.md`)."),
    "CIPAUDTY.cpy": ("Migrated", "IMS segment layout PAUTDTL1 → `AuthorizationDetailEntity` / table `pending_auth_detail`, field by field."),
    "IMSFUNCS.cpy": ("Replaced", "DL/I function-code constants (GU, GN, GNP, ISRT, REPL, DLET, CHKP) have no target counterpart: the calls become repository and Spring Batch operations. The status-code handling they support is preserved in the converted programs."),
    "PAUTBPCB.CPY": ("Replaced", "PCB linkage area for DBPAUTP0; database addressability is provided by the JPA `EntityManager`. No business rule."),
    "PASFLPCB.CPY": ("Replaced", "GSAM PCB linkage for the summary extract file; replaced by the reconciliation extract. No business rule."),
    "PADFLPCB.CPY": ("Replaced", "GSAM PCB linkage for the detail extract file; replaced by the reconciliation extract. No business rule."),
    "COPAU00.bms": ("Migrated", "Field-for-field conversion to `AuthorizationSummaryScreen.tsx`, including the five-row list and the message line (`06-screen-mapping.md`)."),
    "COPAU01.bms": ("Migrated", "Field-for-field conversion to `AuthorizationDetailScreen.tsx`, including the formatted date, time, expiry and fraud fields."),
    "COPAU00.cpy": ("Migrated", "Symbolic map fields → `AuthorizationSummaryResponse` rows and the account-id and selection fields."),
    "COPAU01.cpy": ("Migrated", "Symbolic map fields → `AuthorizationDetailResponse`."),
    "DBPAUTP0.dbd": ("Migrated", "HIDAM database definition → tables `pending_auth_summary` and `pending_auth_detail` with the parent/child foreign key; root key `ACCNTID` and child key `PAUT9CTS` become the primary keys."),
    "DBPAUTX0.dbd": ("Replaced", "The HIDAM primary index database over root key `ACCNTID` has no target artifact: PostgreSQL maintains the `pending_auth_summary` primary-key b-tree itself (UCR-29)."),
    "PSBPAUTB.psb": ("Replaced", "PSB scheduling and `PROCOPT=AP` sensitivity are replaced by the service's transaction boundaries and JPA repositories; the segment sensitivity carries no business rule."),
    "PSBPAUTL.psb": ("Manual Review Required", "Load PSB (`PROCOPT=L`, `LANG=ASSEM`) that no supplied JCL uses — `LOADPADB.JCL` runs `PAUDBLOD` under `PSBPAUTB`. Inventory item E-18: if production loads run under this PSB, the load path differs from the one analysed."),
    "PAUTBUNL.PSB": ("Replaced", "Unload PSB (`PROCOPT=GOTP`) replaced by the reconciliation extract; no business rule."),
    "DLIGSAMP.PSB": ("Replaced", "PSB combining the database PCB with two GSAM PCBs; replaced by the reconciliation extract reading the target tables directly."),
    "PASFLDBD.DBD": ("Replaced", "GSAM DBD for the summary extract file; the extract is produced by the reconciliation tooling instead."),
    "PADFLDBD.DBD": ("Replaced", "GSAM DBD for the detail extract file; the extract is produced by the reconciliation tooling instead."),
    "AUTHFRDS.ddl": ("Migrated", "DB2 table → table `auth_fraud` in `carddemo_authorization`, column for column, with `DECIMAL`→`numeric`, `CHAR`→`char` and the `(CARD_NUM, AUTH_TS)` primary key preserved."),
    "XAUTHFRD.ddl": ("Migrated", "Unique index `(CARD_NUM ASC, AUTH_TS DESC)` → `ux_auth_fraud_card_ts` with the same column order and direction, which is the newest-first read order `COPAUS2C` relies on."),
    "AUTHFRDS.dcl": ("Migrated", "DCLGEN host structure → `FraudReportEntity` / `FraudReportId`; the null indicators become nullable columns and `Optional` returns."),
    "CBPAUP0J.jcl": ("Migrated", "BMP step `PARM='BMP,CBPAUP0C,PSBPAUTB'` and its SYSIN parameter card `00,00001,00001,Y` → `purgeExpiredAuthorizationsJob` and `carddemo.batch.authpurge.*`, including each SYSIN default."),
    "DBPAUTP0.jcl": ("Replaced", "IMS DBDGEN/ACBGEN and VSAM DEFINE for the authorization database → Flyway `V1__create_authorization_schema.sql`; the physical IMS parameters have no target counterpart."),
    "LOADPADB.JCL": ("Migrated", "Load step and its input DDs → the Flyway seed migration plus `tools/reconcile_sample_data.py`; the DELETE/DEFINE reproduces as a clean-database migration run."),
    "UNLDPADB.JCL": ("Refactored", "Unload job → the reconciliation extract that compares source and target rows; the GSAM output DDs have no target counterpart."),
    "UNLDGSAM.JCL": ("Refactored", "GSAM unload variant folded into the same reconciliation extract."),
    "CRDDEMO2.csd": ("Consolidated", "The in-scope PROGRAM/TRANSACTION/DB2ENTRY definitions (CP00→COPAUA0C, CPVS→COPAUS0C, CPVD→COPAUS1C, plan `AWS01PLN`, `DB2TRAN(CPVDTRAN)`) are consolidated into the Kafka listener, the REST operations and the service's own datasource. The supplied plan name differs from the engagement brief's `DB201PLN`; the CSD is treated as authoritative (`02-source-inventory-and-coverage.md`)."),
    "AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat": ("Migrated", "EBCDIC hierarchy image decoded segment by segment (packed keys, packed amounts, trailing blanks) and loaded as authorization seed data, then reconciled row by row by `tools/reconcile_sample_data.py`."),
    f"{AUTH_MODULE}/README.md": ("Consolidated", "Corroborating documentation: the request/reply message contracts, the transaction list and the batch function statement are used as the second evidence source throughout the authorization BRDs, and fold into `02-source-inventory-and-coverage.md` and the parity evidence."),
}

MANUAL_REVIEW: list[tuple[str, str, str, str, str]] = [
    ("COMEN01C.cbl (options 3-11)", "Navigation to the other nine capabilities is unavailable locally.",
     "Medium — an operator cannot reach out-of-scope functions from the local menu.",
     "Extend `MenuAccessService` when those capabilities are modernised.", "High"),
    ("COBDATFT (missing)", "Reissue-date presentation in the batch extract is inferred from the `CODATECN` interface and the sample data.",
     "Low — affects presentation of one extract field only.",
     "Obtain the assembler source or a sample of production extract output and compare.", "Medium"),
    ("acctdata record 49 (UCR-01)", "The two supplied copies of the account file disagree on zip/group-id for one demo account.",
     "Low — demo data only, but it blocks a byte-exact claim across the two copies.",
     "Confirm with the data owner which copy is authoritative.", "High"),
    ("COACTVWC BR-04 (operator type reset on exit)", "Exiting the enquiry declares the operator a regular user.",
     "Medium — an administrator returning to the menu would lose administrator rights.",
     "Confirm intent; behaviour is preserved AS-IS and covered by `MenuAccessServiceTest`.", "Medium"),
    ("COACTVWC BR-16 (account fields shown when only the customer was found)", "Account fields can be displayed from a stale work area.",
     "Low — only reachable when the account master is missing while the cross-reference exists.",
     "Confirm intent; behaviour preserved AS-IS and covered by `AccountViewServiceTest`.", "Medium"),
    ("COACTUPC BR-48 (date-of-birth offsets in the concurrency check)", "The concurrency comparison reads the stored birth date at different offsets from the saved copy.",
     "Medium — a concurrent birth-date change may not be detected.",
     "Confirm intent; the target compares the whole date, which is a superset of the source check, and the difference is recorded.", "Medium"),
    ("COACTUPC BR-50 (no rollback after a failed account rewrite)", "A failed account rewrite exits without an explicit rollback.",
     "Medium — on the mainframe the implicit unit of work still backs out at task end.",
     "Confirm intent; the target rolls back both writes (delta CMD-03).", "High"),
    ("CBACT01C BR-09/BR-11/BR-14 (hard-coded amounts and lengths)", "Default cycle debit 2525.00, array amounts 1005.00/1525.00/-1025.00/-2500.00 and record lengths 12/39 are literals.",
     "High for reporting — extract consumers receive demo values.",
     "Business sign-off required before the extract is used for anything but demonstration.", "High"),
    ("CBACT01C BR-16 (output files never closed)", "The three extract files are not explicitly closed.",
     "Low — the runtime closes them at termination.",
     "No action required in the target; Spring Batch closes its writers.", "High"),
    ("COSGN00C credential handling (UCR-14)", "Passwords are stored and compared in clear text, capped at eight characters, and an unknown user is reported differently from a wrong password.",
     "High — credential exposure and user-existence disclosure.",
     "Hash the stored password and merge the two messages before production use; preserved AS-IS with the token issued over the same check.", "High"),
    ("COUSR03C delete sequencing (UCR-15)", "The record is read and then deleted without testing the outcome of the read.",
     "Low — a concurrent delete produces a different message than on the mainframe.",
     "Confirm intent; the converted transaction reports the failure instead of losing it.", "High"),
    ("COUSR03C failure message (UCR-16)", "A failed delete reports `Unable to Update User...`.",
     "Low — misleading operator message only.",
     "Confirm the wording before correcting; preserved AS-IS and asserted by test.", "High"),
    ("CBPAUP0C counter reversal never written (UCR-27)", "The purge computes the approved/declined counter and total reversals and then discards them, because PAUTSUM0 is never rewritten; available credit is therefore not released.",
     "High — approved totals and the credit balance keep rising as authorizations age out, so later authorization decisions decline earlier than they should.",
     "Business sign-off on the remediation: `carddemo.batch.authpurge.persist-summary-adjustments` defaults to true (corrected), false reproduces AS-IS. Both modes are covered by `AuthPurgeWriterTest`.", "High"),
    ("CBPAUP0C expiry arithmetic across year end (UCR-25)", "Expiry is `CURRENT-YYDDD - (99999 - PA-AUTH-DATE-9C)`, a plain subtraction of Julian dates that is wrong for any authorization spanning 1 January.",
     "High — around year end authorizations either survive indefinitely or are purged immediately.",
     "Business sign-off required before correcting; preserved AS-IS and pinned by `ExpiredAuthorizationProcessorTest`.", "High"),
    ("CBPAUP0C duplicated summary-delete condition (UCR-26)", "`IF PA-APPROVED-AUTH-CNT <= 0 AND PA-APPROVED-AUTH-CNT <= 0` tests the approved counter twice; the declined counter is never tested, so a root with declined authorizations left can still be deleted.",
     "Medium — declined authorizations can be orphaned or lost with the root.",
     "Confirm whether the second test was meant to be the declined counter; preserved AS-IS and pinned by test.", "High"),
    ("COPAUA0C declined total accumulation (UCR-30)", "The declined amount is added from the detail area before the current authorization has been moved into it, so the previous authorization's amount is accumulated.",
     "High — the declined total on the summary screen and in the purge reversal is wrong.",
     "Remediated: the target accumulates the current request amount, corroborated by the approved branch and the copybook. Recorded in the parity evidence.", "High"),
    ("COPAUA0C unreachable decline branches (UCR-18)", "The card-inactive, account-closed, card-fraud and merchant-fraud reason branches exist but no analysed path selects them.",
     "Medium — four documented decline reasons are unreachable, so those declines never occur.",
     "Confirm whether the selecting logic is missing or the reasons are reserved; the reasons are implemented in `DeclineReason` and reachable through the engine, and the gap is recorded as UCR-18.", "High"),
    ("PSBPAUTL unused by the supplied JCL (E-18)", "The load PSB is `PROCOPT=L`/`LANG=ASSEM`, but `LOADPADB.JCL` runs `PAUDBLOD` under `PSBPAUTB`.",
     "Medium — if production loads run under this PSB the analysed load path is not the production one.",
     "Obtain the production load job and confirm which PSB it schedules.", "High"),
    ("MQ trigger attributes not supplied (UCR-17)", "The `MQTM` trigger definition for `AWS.M2.CARDDEMO.PAUTH.REQUEST` (trigger type, depth, process) is not in the supplied artifacts.",
     "Medium — consumer concurrency and batching are inferred from the program and the README rather than from the queue definition.",
     "Obtain the MQ object definitions and compare against the Kafka listener concurrency and `max.poll.records` settings.", "High"),
]


def _rows() -> list[tuple[str, str, str, str]]:
    """Return one (path, type, disposition, rationale) row per discovered artifact."""
    rows: list[tuple[str, str, str, str]] = []
    for directory, kind in DIRS:
        base = APP / directory
        for path in sorted(base.iterdir()):
            if not path.is_file() or path.name.startswith("."):
                continue
            disposition, rationale = DECISIONS.get(path.name, OUT_OF_SCOPE)
            rows.append((f"app/{directory}/{path.name}", kind, disposition, rationale))
    for relative, kind in FILES:
        disposition, rationale = DECISIONS.get(relative, OUT_OF_SCOPE)
        rows.append((f"app/{relative}", kind, disposition, rationale))
    return rows


def main() -> None:
    """Write the disposition report to docs/13-artifact-disposition.md."""
    rows = _rows()
    counts: dict[str, int] = {}
    for _, _, disposition, _ in rows:
        counts[disposition] = counts.get(disposition, 0) + 1

    lines: list[str] = [
        "# Source Artifact Disposition Report",
        "",
        "Every artifact discovered under `app/` carries exactly one disposition. Generated by",
        "`modernization/tools/gen_disposition.py` from the discovery decisions, so the report cannot",
        "drift from the inventory and no artifact can be silently ignored.",
        "",
        f"Total artifacts: **{len(rows)}**.",
        "",
        "| Disposition | Count |",
        "|---|---|",
    ]
    for disposition in sorted(counts):
        lines.append(f"| {disposition} | {counts[disposition]} |")
    lines += [
        "",
        "Every one of the 31 COBOL programs and every supporting artifact is in scope: none carries",
        '"Retired from this scope". "Retired" means the artifact is a platform facility (CEMT open and',
        "close, GDG definitions, IDCAMS backups, the wait utility) whose function belongs to the",
        "platform or the scheduler in the target, so no business rule is carried; the source is left",
        "in place and not converted.",
        "",
        "## Dispositions",
        "",
        "| Artifact | Type | Disposition | Rationale |",
        "|---|---|---|---|",
    ]
    for path, kind, disposition, rationale in rows:
        lines.append(f"| `{path}` | {kind} | {disposition} | {rationale} |")

    lines += [
        "",
        "## Manual Review Required items",
        "",
        "| Item | Description | Business impact | Recommended action | Confidence |",
        "|---|---|---|---|---|",
    ]
    for item, description, impact, action, confidence in MANUAL_REVIEW:
        lines.append(f"| {item} | {description} | {impact} | {action} | {confidence} |")
    lines.append("")
    lines.append(
        "Each item is also carried in `11-unsupported-construct-register.md` or "
        "`17-limitations-and-review-required.md` with its register id and status."
    )
    lines.append("")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT} ({len(rows)} artifacts)")


if __name__ == "__main__":
    main()
