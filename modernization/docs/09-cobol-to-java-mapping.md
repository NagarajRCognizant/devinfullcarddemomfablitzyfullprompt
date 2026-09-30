# COBOL-to-Java Mapping Guide

§1-§5 map the account, user and date-utility programs; §6 maps the authorization programs.

## 1. Program to module

| Source | Lines | Target | Note |
|---|---|---|---|
| `COACTVWC` | 941 | `AccountViewService`, `AccountReadService`, `AccountScreenMapper`, `AccountViewController` | inquiry path |
| `COACTUPC` | 4,236 | `AccountUpdateService`, `AccountUpdateValidator`, `AccountChangeDetector`, `FieldEditor`, `DateEditor`, `AccountUpdateController` | update path; the read sequence is shared with the inquiry path |
| `CBACT01C` + `READACCT.jcl` | 430 | `ReadAcctJobConfig` and the three aggregators | batch extract |
| `CSUTLDTC` (+ `CSUTLDPY`, `CSUTLDWY`) | 155 | `DateValidationService`, `DateEditor`, `DateEditResult`, `DateValidationResult` | `CEEDAYS` replaced by `java.time` |
| `COMEN01C` (in-scope part) | 273 | `MenuAccessService`, `MenuController` | access rule only |
| `CSLKPCDY` | 999 | `LookupTables` + `resources/reference/*.txt` | state, ZIP-prefix and area-code tables |
| `CSMSG01Y`, `CSMSG02Y` | — | `ScreenMessages` | message text verbatim |
| `CSSETATY`, `CSSTRPFY`, `DFHBMSCA`, `DFHAID`, `COCOM01Y` | — | `FieldFlag` / response envelope / dropped | terminal plumbing modernised away |

## 2. COACTVWC — paragraph to Java method

| Paragraph | Line | Java |
|---|---|---|
| `0000-MAIN` | 262 | `AccountViewController.prompt` / `view`, `AccountViewService.view` |
| `COMMON-RETURN` | 397 | HTTP response (no state to save) |
| `1000-SEND-MAP` / `1400-SEND-SCREEN` | 419 / 577 | serialisation of `AccountViewResponse` |
| `1100-SCREEN-INIT` | 431 | `ScreenFrame.tsx` (titles, names, date, time) |
| `1200-SETUP-SCREEN-VARS` | 460 | `AccountScreenMapper.toDetails` |
| — SSN 3-2-4 formatting | 507 | `AccountScreenMapper.toDetails` (`ssnFormatted`) |
| `1300-SETUP-SCREEN-ATTRS` | 541 | `ScreenValidationException.fields` → `ErrorResponse.fields` + `Field.tsx` |
| `2000-PROCESS-INPUTS` | 596 | `AccountViewController.view` argument binding |
| `2100-RECEIVE-MAP` | 611 | request binding |
| `2200-EDIT-MAP-INPUTS` | 622 | `CobolText.normaliseAsterisk` in `AccountReadService.editAccountId` |
| `2210-EDIT-ACCOUNT` | 649 | `AccountReadService.editAccountId(keyed, false)` |
| `9000-READ-ACCT` | 687 | `AccountReadService.readAccount` |
| `9200-GETCARDXREF-BYACCT` | 723 | `CardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumAsc` |
| `9300-GETACCTDATA-BYACCT` | 774 | `AccountRepository.findById` |
| `9400-GETCUSTDATA-BYCUST` | 825 | `CustomerRepository.findById` |
| `SEND-PLAIN-TEXT` | 895 | `ApiExceptionHandler` 500 body |
| `ABEND-ROUTINE` | 916 | `StorageAccessException` → HTTP 500 (UCR-08) |

## 3. COACTUPC — paragraph to Java method

| Paragraph | Line | Java |
|---|---|---|
| `0000-MAIN` | 859 | `AccountUpdateService` entry methods + `AccountUpdateStatus` |
| `1000-PROCESS-INPUTS` | 1025 | `AccountUpdateController.validate` / `confirm` binding |
| `1100-RECEIVE-MAP` | 1039 | `AccountUpdateValidator.normalise` (asterisk, case, blank handling) |
| `1200-EDIT-MAP-INPUTS` | 1429 | `AccountUpdateValidator.validate` |
| `1205-COMPARE-OLD-NEW` | 1681 | `AccountChangeDetector.hasChanges` |
| `1210-EDIT-ACCOUNT` | 1783 | `AccountReadService.editAccountId(keyed, true)` |
| `1215-EDIT-MANDATORY` | 1824 | `FieldEditor.editMandatory` |
| `1220-EDIT-YESNO` | 1856 | `FieldEditor.editYesNo` |
| `1225-EDIT-ALPHA-REQD` | 1898 | `FieldEditor.editAlphaRequired` |
| `1230-EDIT-ALPHANUM-REQD` | 1955 | `FieldEditor.editAlphanumRequired` (unreachable in the source — UCR-05) |
| `1235-EDIT-ALPHA-OPT` | 2012 | `FieldEditor.editAlphaOptional` |
| `1240-EDIT-ALPHANUM-OPT` | 2061 | `FieldEditor.editAlphanumOptional` (unreachable in the source — UCR-05) |
| `1245-EDIT-NUM-REQD` | 2109 | `FieldEditor.editNumericRequired` |
| `1250-EDIT-SIGNED-9V2` | 2180 | `FieldEditor.editSignedAmount` → `CobolNumeric` |
| `1260-EDIT-US-PHONE-NUM` | 2225 | `FieldEditor.editUsPhoneNumber` + `LookupTables.isPhoneAreaCode` |
| `1265-EDIT-US-SSN` | 2431 | `FieldEditor.editUsSsn` |
| `1270-EDIT-US-STATE-CD` | 2493 | `FieldEditor.editUsStateCode` + `LookupTables.isStateCode` |
| `1275-EDIT-FICO-SCORE` | 2514 | `FieldEditor.editFicoScore` |
| `1280-EDIT-US-STATE-ZIP-CD` | 2536 | `FieldEditor.editUsStateZipCombo` + `LookupTables.isStateZipCombo` |
| `2000-DECIDE-ACTION` | 2562 | status transitions in `AccountUpdateService.validate` / `confirm` |
| `3000-SEND-MAP` | 2649 | `AccountUpdateResponse` serialisation |
| `3100-SCREEN-INIT` | 2668 | `ScreenFrame.tsx` |
| `3200-SETUP-SCREEN-VARS` | 2698 | `AccountScreenMapper.toForm` / `toDetails` |
| `3201-SHOW-INITIAL-VALUES` | 2731 | mapper branch for `DETAILS_FETCHED` |
| `3202-SHOW-ORIGINAL-VALUES` | 2787 | mapper branch for `NO_CHANGES` / `RECORD_CHANGED` |
| `3203-SHOW-UPDATED-VALUES` | 2870 | mapper branch for `VALIDATION_ERROR` / `CHANGES_VALIDATED` |
| `3250-SETUP-INFOMSG` | 2955 | `ScreenMessages` selection in the mapper |
| `3300-SETUP-SCREEN-ATTRS` | 2986 | `fieldFlags` + `Field.tsx` |
| `3310-PROTECT-ALL-ATTRS` | 3441 | read-only rendering before a fetch |
| `3320-UNPROTECT-FEW-ATTRS` | 3500 | the editable field set of `AccountUpdateForm` |
| `3390-SETUP-INFOMSG-ATTRS` | 3566 | `Messages.tsx` dim/normal state |
| `3400-SEND-SCREEN` | 3589 | HTTP response |
| `9000-READ-ACCT` | 3608 | `AccountReadService.readAccount` |
| `9200-GETCARDXREF-BYACCT` | 3650 | `CardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumAsc` |
| `9300-GETACCTDATA-BYACCT` | 3701 | `AccountRepository.findById`, `lockByAcctId` on the write path |
| `9400-GETCUSTDATA-BYCUST` | 3752 | `CustomerRepository.findById`, `lockByCustId` on the write path |
| `9500-STORE-FETCHED-DATA` | 3801 | `AccountUpdateService.fetch` returning the baseline form to the client |
| `9600-WRITE-PROCESSING` | 3888 | `AccountUpdateService.confirm` (`@Transactional`: lock, compare, `applyToAccount`, `applyToCustomer`) |
| `9700-CHECK-CHANGE-IN-REC` | 4109 | baseline comparison inside `AccountUpdateService.confirm` |
| `ABEND-ROUTINE` | 4203 | `StorageAccessException` → HTTP 500 |

## 4. CBACT01C — paragraph to Java

| Paragraph | Java |
|---|---|
| `0000-ACCTFILE-OPEN` / `9000-*-CLOSE` | reader/writer lifecycle managed by Spring Batch |
| `1000-ACCTFILE-GET-NEXT` | `JpaPagingItemReader` ordered by `acctId` |
| `1100-DISPLAY-ACCT-RECORD` | step logging |
| `1300-POPUL-ACCT-RECORD` / `1350-WRITE-ACCT-RECORD` | `AcctCompRecordAggregator` |
| `1400-POPUL-ARRAY-RECORD` / `1450-WRITE-ARRY-RECORD` | `AcctArrayRecordAggregator` |
| `1500-POPUL-VBRC-RECORD` / `1550`/`1575-WRITE-VB*-RECORD` | `AcctVbRecordAggregator` |
| `9910-DISPLAY-IO-STATUS` / `9999-ABEND-PROGRAM` | exception propagation → step `FAILED` |

## 5. COBOL construct to Java idiom

| Construct | Java approach |
|---|---|
| `PIC X(n)` fixed-width move | `CobolText.padRight`/`truncate`, `CHAR(n)` columns |
| `PIC 9(n)` display numeric | `CobolRecordBuilder.pic9`, `Long` in the domain |
| `PIC S9(10)V99` display with overpunch | `ZonedDecimalCodec`, `BigDecimal` |
| `COMP-3` | `CobolRecordBuilder.packed`, `BigDecimal` |
| 88-level condition names | enums and named predicates (`FieldFlag`, `AccountUpdateStatus`) |
| `REDEFINES` text/numeric pair | keyed `String` in the DTO, converted after the edit passes |
| `OCCURS` | positional writes in the batch aggregator |
| `SEARCH ALL` | sorted arrays with `Arrays.binarySearch` in `LookupTables` |
| `PERFORM … THRU … EXIT` with `GO TO … EXIT` | early `return` from a small method |
| `COPY … REPLACING` (`CSSETATY` x40) | one `FieldFlag` per field, one `ScreenField` enum constant |
| `INITIALIZE` / `MOVE LOW-VALUES` | explicit object construction; a deliberately retained "record area keeps its previous content" case exists in `AcctCompRecordAggregator` |
| `EXEC CICS READ`/`READ UPDATE`/`REWRITE`/`SYNCPOINT ROLLBACK` | repository finder / `FOR UPDATE` lock / `save` / transaction rollback |
| `EXEC CICS ABEND` | unchecked exception → HTTP 500 |
| `CALL 'CSUTLDTC'` | `DateValidationService.validateYyyyMmDd` |
| `CALL 'COBDATFT'` | `CobdatftDateFormatter.convert` (behaviour reconstructed from the caller — UCR-04) |

Traceability comments in the Java sources name the source program and paragraph at each significant
method, so the mapping above can be verified from either direction.

## 6. Authorization programs — program to module

| Source | Lines | Target | Note |
|---|---|---|---|
| `COPAUA0C` | 1,026 | `AuthorizationRequestListener`, `AuthorizationRequestProcessor`, `AuthorizationDecisionEngine`, `AuthorizationReplyPublisher`, `CardholderLookupClient` | the MQ-triggered authorizer; the CICS transaction shell becomes a Kafka listener, the decision becomes a pure domain component |
| `COPAUS0C` | 1,032 | `AuthorizationInquiryService.summary`, `AuthorizationController.summary`, `AuthorizationSummaryScreen.tsx` | CPVS |
| `COPAUS1C` | 604 | `AuthorizationInquiryService.detail`, `AuthorizationController.detail`, `AuthorizationDetailScreen.tsx` | CPVD |
| `COPAUS2C` | 244 | `FraudMarkingService`, `AuthorizationController.toggleFraud` | the DB2 fraud writer, called from CPVD by `LINK` |
| `CBPAUP0C` + `CBPAUP0J` | 386 | `PurgeAuthJobConfig`, `AuthSummaryKeysetReader`, `ExpiredAuthorizationProcessor`, `AuthPurgeWriter`, `AuthPurgeProperties` | see `07-batch.md` §7 |
| `PAUDBUNL` + `DBUNLDGS` | 683 | `tools/ims_authorization_unload.py` | the unload path becomes the migration extractor; the target has no unload utility of its own |
| `PAUDBLOD` | 369 | Flyway `V2__load_authorization_sample_data.sql` generated by the decoder | the load becomes a migration, with the reconciliation `PAUDBLOD` never had (UCR-28) |

### 6.1 Copybook classification and target representation

| Copybook | Role | Target |
|---|---|---|
| `CCPAURQY` | MQ request message layout | `AuthorizationRequestMessage` (record, parsed from the Kafka payload) |
| `CCPAURLY` | MQ reply message layout | `AuthorizationReplyMessage` (record, serialised to the reply topic) |
| `CCPAUERY` | decline-reason code table | `DeclineReason` enum + `DeclineReasonFlag` |
| `CIPAUSMY` | IMS `PAUTSUM0` segment layout | `AuthorizationSummaryEntity` + `pending_auth_summary` |
| `CIPAUDTY` | IMS `PAUTDTL1` segment layout | `AuthorizationDetailEntity` + `pending_auth_detail` |
| `IMSFUNCS` | DL/I function-code constants | not carried forward — the functions become repository calls; the constants are documented in `07-batch.md` §7.1 |
| `PADFLPCB`, `PASFLPCB`, `PAUTBPCB` | PCB masks (working-storage plumbing) | dropped; the status-code handling they carried is in the reader/processor |
| `AUTHFRDS` DCLGEN | DB2 host structure | `FraudReportEntity` + `authorization_fraud_report` |
| `COPAU00`/`COPAU01` BMS copybooks | screen structures | React component props and the response DTOs; no symbolic map equivalent |

Per playbook §8.3, none of the message or screen copybooks became a persistence entity and no shared
legacy-model library was created: `authorization-domain` is used only by the authorization service and
its batch job, and the cross-context cardholder view is a DTO of the account service's contract.

### 6.2 COPAUA0C — paragraph to Java method

| Paragraph | Java |
|---|---|
| `0000-MAIN` / the MQ GET-wait loop | `AuthorizationRequestListener.onMessage` (the broker delivers; the loop disappears) |
| `1000-PROCESS-REQUEST` | `AuthorizationRequestProcessor.process` |
| `2000-GET-CARD-XREF`, `3000-GET-ACCT-DATA`, `4000-GET-CUST-DATA` | `CardholderLookupClient.byCardNumber` — one cross-context call replaces three VSAM reads (the account service still performs them in that order) |
| `5000-CHECK-AUTH-SUMMARY` | `AuthorizationSummaryRepository.findByIdForUpdate` |
| `6000-AUTHORIZE` / `6100-CHECK-CREDIT-LIMIT` | `AuthorizationDecisionEngine.decide` |
| `7000-BUILD-REPLY-MQMD` / `7100-PUT-REPLY` | `AuthorizationReplyPublisher` + the outbox row (CMD-11) |
| `8000-UPDATE-DB` / `8400-UPDATE-SUMMARY` | `AuthorizationRequestProcessor.updateSummary` (UCR-30 remediation) |
| `8500-INSERT-AUTH` | `AuthorizationDetailRepository.save` with the complemented key |
| `9000-ABEND` / the `SYNCPOINT ROLLBACK` paths | exception out of the `@Transactional` method → rollback → retry/dead-letter (CMD-10, CMD-12) |

### 6.3 Authorization-specific construct to Java idiom

| Construct | Java approach |
|---|---|
| `GU`/`GN`/`GNP`/`ISRT`/`REPL`/`DLET` via `CBLTDLI` | Spring Data repository methods; the parent/child walk is a foreign key and an ordered query |
| PCB status-code checks (`' '`, `GE`, `GB`, `GA`, `GK`) | `Optional` for not-found, exception for anything the source abends on |
| `CHKP` | chunk commit plus the reader position in the step execution context |
| complemented keys `99999 - YYDDD`, `999999999 - HHMMSSmmm` | `AuthorizationKey` — kept as stored integers so the natural index order is still newest-first |
| `EXEC SQL INSERT`/`DELETE` on `AUTHFRDS` | `FraudReportRepository` inside the same local transaction (CMD-13) |
| `EXEC CICS LINK` to `COPAUS2C` | a service call inside one bounded context, not a remote hop |
| `MQGET`/`MQPUT1` with `MQMD-CORRELID`, `MQMD-REPLYTOQ` | `@KafkaListener` and `KafkaTemplate`, with the correlation id and reply-to carried as headers and the card number as the partition key (`23-messaging-modernization-mapping.md`) |
| MQ `MQTM` trigger monitor | consumer-group subscription; there is no trigger to configure |
| `EXEC CICS SYNCPOINT` around IMS + DB2 + MQ | one PostgreSQL transaction plus a transactional outbox (CMD-10/CMD-11) |
| BMS `SEND MAP`/`RECEIVE MAP`, `DFHAID` PF keys | JSON responses and React buttons (`06-screen-mapping.md` §8) |
| `XCTL` from CPVS to CPVD carrying the key in the COMMAREA | client-side navigation with the key in the route (retired per §8.4.5) |

Traceability comments in each authorization class name the source program and paragraph, as in the
account scope.
