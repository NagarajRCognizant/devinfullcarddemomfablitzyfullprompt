# Test Strategy and Functional-Parity Method

§1-§5 cover the account and identity scope as originally delivered. §6-§8 cover the authorization
scope and the two deliberate defect remediations. The coverage table in §2 is the whole reactor.

## 1. Strategy

Parity is demonstrated from source-derived expectations, not from inspecting the converted code. Every
test's expected value comes from one of three places: the literal text or numeric edit in the COBOL
paragraph, the record layout of a copybook or JCL DD statement, or the supplied sample data. Where the
source behaviour is odd (UCR-05, UCR-07, UCR-10), the test asserts the odd behaviour, so a future
"clean-up" fails the suite instead of silently changing business behaviour.

| Level | Tests | What it proves |
|---|---|---|
| COBOL semantics (unit) | `CobolTextTest`, `CobolNumericTest`, `CobolRecordBuilderTest`, `ZonedDecimalCodecTest`, `CobdatftDateFormatterTest` | fixed-width moves, `PIC` edits, zoned/packed decimal with sign, truncation, the reconstructed `COBDATFT` conversion |
| Field edits (unit) | `FieldEditorTest`, `DateEditorTest`, `DateValidationServiceTest` | every edit paragraph `1215`-`1280` and the `CSUTLDPY`/`CSUTLDTC` date rules, including boundaries and the reserved SSN ranges |
| Services (unit, Mockito) | `AccountReadServiceTest`, `AccountViewServiceTest`, `AccountUpdateValidatorTest`, `AccountChangeDetectorTest`, `AccountUpdateServiceTest`, `AccountScreenMapperTest`, `MenuAccessServiceTest` | read sequencing and short-circuits, message selection per program, first-message-wins with all flags, change detection, the update lifecycle, lock ordering, rollback, screen mapping, the access rule |
| Repository / data (integration, PostgreSQL) | `AccountStoreIntegrationTest` | the seed load matches the supplied ASCII records, fixed-width fields survive the round trip, the alternate-index path works, key-sequence reads, both records lock for update |
| REST API (integration, MockMvc + PostgreSQL) | `AccountApiIntegrationTest` | every operation end to end: prompt, view, validation error, not found, fetch→validate→confirm, no-change, field flags, stale-snapshot conflict, malformed body, role endpoint |
| Batch (unit + integration) | `AcctRecordAggregatorTest`, `ReadAcctJobIntegrationTest` | byte-exact 107/110/84 layouts including the hard-coded literals and the unpopulated occurrences; one record per account per extract, key order, rerun replaces the extract |
| Frontend (Vitest + Testing Library) | `AccountViewScreen.test.tsx`, `AccountUpdateScreen.test.tsx` | field rendering with the map's edited amounts, the validation message line, read-only versus editable fields, the confirmation flow |

Integration tests run against the local PostgreSQL instance through `PostgresIntegrationTest`.
Testcontainers is the playbook default but the Docker API on this machine is too old for the
Testcontainers client (`client version 1.32 … minimum supported API version is 1.40`), so the base
class points at the documented local database instead — same PostgreSQL 16, same migrations, same
assertions. This is recorded as a limitation (L-02), not a reduction in coverage.

## 2. Coverage

JaCoCo, enforced by the Maven verify goal on the migrated business logic — `com.carddemo.service`,
`com.carddemo.domain`, `com.carddemo.cobol`, `com.carddemo.batch` — with DTOs, entities, the
application class and framework configuration excluded per playbook §6.

Executed run (`evidence/authorization-mvn-verify.txt`): **371 tests, 0 failures, 0 errors, 0
skipped** across the whole reactor, `All coverage checks have been met` in every gated module,
`BUILD SUCCESS`.

| Module | Line | Branch |
|---|---|---|
| `common` | 87.0% (428/492) | 89.0% (274/308) |
| `account-service` | 93.8% (484/516) | 95.9% (142/148) |
| `identity-service` | 91.6% (196/214) | 89.3% (67/75) |
| `batch-account-extract` | 99.1% (113/114) | 83.3% (5/6) |
| `authorization-domain` | 95.7% (154/161) | 84.7% (61/72) |
| `authorization-service` | 98.8% (424/429) | 90.9% (100/110) |
| `batch-auth-purge` | 99.4% (153/154) | 92.6% (50/54) |

Every module passes both 80% floors. The lowest branch figures come from null-guard combinations in
the change detector, the screen mappers and the decision engine's flag permutations, which multiply
branches faster than distinct business outcomes; every business outcome is asserted.

Frontend (`evidence/frontend-test.log`): `eslint` clean, `tsc --noEmit` clean, 11 test files / 42
tests passed, Vite production build succeeded.

## 3. Functional-parity method

For each business rule in the catalogue:

1. **Derive** the expected observable outcome from the source (message text, accepted/rejected,
   stored value, record bytes, read order).
2. **Encode** it as an assertion whose expected value is the source-derived value, quoted verbatim
   where it is text.
3. **Execute** it in the suite above.
4. **Trace** it in `14-rtm.md`, rule by rule, to the test that proves it.

Three additional parity mechanisms beyond unit assertions:

- **Message parity.** Every message lives once, in `ScreenMessages`, copied verbatim from the source
  including the double space in `Account Filter must  be a non-zero 11 digit number` and the trailing
  period in `Account:%s not found in Cross ref file.`. Tests assert the exact strings.
- **Record parity.** The batch aggregator tests assert offsets, lengths, sign overpunch and packed
  nibbles against the layouts derived from `CBACT01C` and the READACCT DD statements, so an extract
  produced locally is byte-comparable to a mainframe extract for the same input.
- **Data parity.** `tools/reconcile_sample_data.py` compares the ASCII source, the EBCDIC source and
  the loaded database field by field (`evidence/sample-data-reconciliation.log`): 50/50/50 records,
  550 account field comparisons matched, one known source-data discrepancy (UCR-01) asserted as such.

## 4. Functional-parity evidence summary

| Behaviour required by the engagement | Evidence |
|---|---|
| Account-ID and card-number validation, numeric/length edits, not-found paths | `AccountReadServiceTest` (4 cases), `AccountApiIntegrationTest.aNonNumericAccountIdIsAValidationError`, `anUnknownAccountIsNotFound`, `evidence/api-smoke.log` |
| XREF → ACCOUNT → CUSTOMER order and per-read status handling | `AccountReadServiceTest.theReadSequenceIsCrossReferenceThenAccountThenCustomer`, `aMissingCrossReferenceStopsBeforeTheAccountRead`, `aMissingAccountStopsBeforeTheCustomerRead`, `aMissingCustomerIsReportedWithTheCustomerIdFromTheCrossReference` |
| Every field-level edit with exact messages and highlight semantics | `FieldEditorTest` (18 cases), `DateEditorTest` (7), `AccountUpdateValidatorTest.everyFailingFieldIsFlaggedButOnlyTheFirstMessageIsReturned` |
| Update lifecycle: edit → confirm → both rewrites | `AccountUpdateServiceTest.validatedChangesAskForConfirmation`, `confirmRewritesTheAccountBeforeTheCustomer`, `AccountApiIntegrationTest.theUpdateTransactionFetchesValidatesAndCommits` |
| "No changes made" detection | `AccountChangeDetectorTest` (5 cases), `AccountUpdateServiceTest.anUnchangedSubmissionNeverReachesTheEdits`, `AccountApiIntegrationTest.anUnchangedSubmissionIsReportedRatherThanRewritten` |
| Optimistic record-changed check | `AccountUpdateServiceTest.aRecordThatMovedSinceItWasFetchedIsNotOverwritten`, `AccountApiIntegrationTest.aRecordChangedByAnotherUpdaterIsNotOverwritten` |
| Rollback when the second rewrite fails | `AccountUpdateServiceTest.aFailingCustomerRewriteRollsTheUnitOfWorkBack` |
| Signed/packed decimal precision, dates, blank/zero/sentinel semantics | `CobolNumericTest`, `ZonedDecimalCodecTest`, `CobolTextTest`, `CobolRecordBuilderTest`, `AccountScreenMapperTest.aLeadingZeroSsnKeepsAllNineDigits` |
| Batch sequential read and the three layouts | `AcctRecordAggregatorTest` (6 cases), `ReadAcctJobIntegrationTest` (3), `evidence/batch-readacct.log` |
| Business-level access rules | `MenuAccessServiceTest` (5 cases), `AccountApiIntegrationTest.theRoleEndpointDescribesTheLocallySelectedUserType` |
| Clean database initialisation and seed load | `evidence/db-clean.log`, `evidence/migration.log`, `AccountStoreIntegrationTest.everySampleRecordIsLoadedFromTheSuppliedAsciiFiles` |
| OpenAPI validity | `evidence/openapi-lint.log` |
| Startup and live API behaviour | `evidence/backend-startup.log`, `evidence/api-smoke.log` |

## 5. What the tests deliberately do not prove

- No comparison against a live mainframe run exists — there is no z/OS environment here. Parity is
  against source-derived expectations and the supplied sample data.
- The `COBDATFT` reconstruction (UCR-04) and the `CEEDAYS` replacement (UCR-06) are verified against
  the callers' usage, not against the real modules.
- Docker image and Compose startup were not verified (UCR-09).
- No automated accessibility audit was run (L-04).
- Coverage is a floor: 100% of the extracted rules are traced in the RTM, but a rule the extraction
  missed would be invisible to both.

## 6. Authorization scope — parity evidence

### 6.1 Decision and message parity

| Behaviour required by the source | Evidence |
|---|---|
| Approval is the default; a decline needs a reason | `AuthorizationDecisionEngineTest.anAuthorizationWithinTheAvailableCreditIsApproved` and the decline cases |
| Available credit is `credit limit − credit balance`; the summary is used when it exists, the account balance when it does not | `AuthorizationDecisionEngineTest.availableCreditComesFromTheSummaryWhenOneExists`, `…FallsBackToTheAccountBalance` |
| Missing card, account or customer declines `3100`; over-limit declines `4100` | `AuthorizationDecisionEngineTest`, `DeclineReasonTest` |
| Response code `00`/`05`, approved amount = transaction amount on approval and zero on decline, match status `P`/`D` | `AuthorizationDecisionEngineTest`, `AuthorizationRequestProcessorIntegrationTest` |
| The decline-reason code table verbatim, including its unreachable entries and the `9999-ERROR` fallback | `DeclineReasonTest` (12 cases) |
| Date and time keys stored as `99999 − YYDDD` and `999999999 − HHMMSSmmm`, newest first | `AuthorizationKeyTest` (8 cases) |
| The request and reply record layouts of `CCPAURQY`/`CCPAURLY`, field by field | `AuthorizationMessageLayoutTest` (9 cases) |
| Summary counters and balances updated on approval (including the cash balance reset, UCR-22) and on decline | `AuthorizationRequestProcessorIntegrationTest`, `AuthorizationApiIntegrationTest` |
| CPVS: numeric account edit, five rows per page, both boundary messages, `S`/`s` selection edit | `AuthorizationApiIntegrationTest`, `AuthorizationInquiryServiceTest`, `AuthorizationSummaryScreen.test.tsx` (6 cases) |
| CPVS with no summary displays zero metrics | `AuthorizationInquiryServiceTest`, `AuthorizationApiIntegrationTest` |
| CPVD: `MM/DD/YY`, `HH:MM:SS`, `MM/YY` editing, fraud rendering `F-`/`R-`/`-`, chain navigation and the end-of-chain message | `AuthorizationInquiryServiceTest`, `FraudStatusTest`, `AuthorizationApiIntegrationTest`, `AuthorizationDetailScreen.test.tsx` (4 cases) |
| Fraud toggle: confirmed fraud is removed, anything else is marked, with the `AUTHFRDS` row written | `FraudMarkingServiceTest`, `AuthorizationApiIntegrationTest` |
| Purge parameter defaulting, expiry arithmetic, counter reversal, checkpoint frequency, report totals | `AuthPurgePropertiesTest`, `ExpiredAuthorizationProcessorTest`, `AuthPurgeWriterTest`, `AuthSummaryKeysetReaderTest`, `PurgeAuthJobIntegrationTest`, `evidence/authorization-purge-batch-run.txt` |
| Cross-context cardholder read with the source's three read flags, and a transport failure that must not decline | `CardholderLookupClientTest` |
| Clean-database migration and the decoded IMS unload | `evidence/authorization-data-reconciliation.txt` |
| Full reactor build, tests and coverage gates | `evidence/authorization-mvn-verify.txt` |

### 6.2 Messaging parity

The MQ interaction is proven against an embedded Kafka broker, not asserted from code:
`AuthorizationRequestListenerIntegrationTest` publishes real records to the request topic and
asserts the reply record, the preserved correlation id and reply-to header, the card number as the
partition key, the bounded retry and the dead-letter record for a malformed request, and that a
replayed request produces the stored reply instead of a second authorization.
`AuthorizationConcurrencyIntegrationTest` proves that two concurrent requests for the same account
serialise on the summary lock and produce consistent totals. `AuthorizationReplyPublisherTest` and
`AuthorizationRequestListenerTest` cover the outbox and the listener contract in isolation.
What this does **not** prove is behaviour under broker loss, rebalance or replication on a
multi-broker cluster (L-17).

### 6.3 Data parity

`tools/ims_authorization_unload.py` decodes the supplied EBCDIC unload,
`tools/generate_authorization_seed_sql.py` turns it into the seed migration, and
`tools/reconcile_authorization_data.py` compares it with the loaded database field by field
(`evidence/authorization-data-reconciliation.txt`): 21 `PAUTSUM0` roots and 202 `PAUTDTL1` children,
231 summary field comparisons and 3,030 detail field comparisons with zero failures, the parent/child
relationship reconciled per account, and one supplied record rejected and reported rather than
silently skipped (UCR-31).

## 7. Deliberate behaviour deltas — AS-IS defects remediated

Two defects in the authorization closure are corrected rather than carried forward, both under
playbook §6.3 (corroborated by a second artifact) and both recorded here with before/after behaviour.
No other AS-IS defect in any scope was corrected.

| Ref | Source behaviour (before) | Target behaviour (after) | Corroboration | Test |
|---|---|---|---|---|
| UCR-30 | `COPAUA0C` `8400-UPDATE-SUMMARY` adds `PA-TRANSACTION-AMT` — a field the *next* paragraph populates — to the declined total, so the total is the sum of the *previous* declined requests' amounts, zero for the first message of a run | the declined total accrues the current request's transaction amount | `CBPAUP0C` BR-14 subtracts exactly that field when purging a declined authorization, so the total can only be consistent if it is the sum of each declined authorization's own amount | `AuthorizationRequestProcessorIntegrationTest` pins the corrected total |
| UCR-27 | `CBPAUP0C` computes the four counter reversals when it purges a child but never issues `REPL SEGMENT(PAUTSUM0)`, so unless the root is deleted the adjustment is discarded and available credit is never released | the reversals are written back (`persist-summary-adjustments`, default `true`) | the module `README.md` states the job's function as "Adjustment of available credit when unmatched authorizations are deleted", and the program computes the reversals with no other use for them | `AuthPurgeWriterTest.aRootThatKeepsChildrenHasItsCountersRewrittenInTheRemediatedMode`; `…theAsIsModeDiscardsTheCounterReversalsTheSourceNeverRewrote` pins the AS-IS mode, still available by configuration |

Both remain REVIEW REQUIRED: the remediations change subsequent authorization decisions, and
historical summary totals migrated from IMS carry the old defect.

## 8. What the authorization tests deliberately do not prove

- No comparison against a live mainframe, IMS, DB2 or MQ run — the same limitation as the account
  scope (L-03).
- No multi-broker Kafka behaviour: no broker loss, rebalance, replication or lag testing (L-17).
- No performance or load testing: the repository contains no message volumes, MQ statistics or batch
  schedule, so partition count, listener concurrency and chunk size are documented defaults to be
  tuned before go-live (`19-test-strategy-and-quality-gates.md` §6).
- No IMS status-code path can be exercised, because there is no IMS: the abend paths on unexpected
  root and child statuses are mapped to step failure and are unexercised (RTM Part B records the
  three affected rows).
- No browser-driven UI testing or accessibility audit (L-04).

## 9. Transactions, cards, statements and branch migration — parity evidence

| Flow | Parity assertion | Test |
|---|---|---|
| Transaction list (COTRN00C) | ten rows a page in transaction-id order, forward and backward paging, the first-page and last-page wording, single-row selection | `TransactionListServiceTest`, `TransactionApiIntegrationTest`, `TransactionListScreen.test.tsx` |
| Transaction view (COTRN01C) | the 16-character id requirement and the not-found wording | `TransactionViewServiceTest`, `TransactionViewScreen.test.tsx` |
| Transaction add (COTRN02C) | field edits in source order with the source messages; the id is the highest existing id plus one | `TransactionAddServiceTest`, `TransactionApiIntegrationTest`, `TransactionAddScreen.test.tsx` |
| Bill payment (COBIL00C) | a non-positive balance is refused with the source wording; the payment is the whole balance with the source's fixed classification and placeholder merchant; the balance is reduced once | `BillPaymentServiceTest`, `TransactionApiIntegrationTest`, `BillPaymentScreen.test.tsx` |
| Report request (CORPT00C) | the monthly range including the December rollover, the yearly range, and the custom range validated part by part | `ReportRequestServiceTest`, `ReportRequestScreen.test.tsx` |
| Card list, view, update (COCRDLIC/SLC/UPC) | seven rows a page, the filter edits and the asterisk convention, the security code never sent to the screen, the no-change and concurrent-change messages | `CardListServiceTest`, `CardDetailServiceTest`, `CardUpdateServiceTest`, the three screen tests |
| Daily verification and posting (CBTRN01C/02C) | reason codes 100 … 103 with their source descriptions, including 103 overwriting 102; the category balance, transaction and account movements of an accepted transaction | `DailyTransactionValidatorTest`, `PostedTransactionWriterTest`, `TransactionBatchIntegrationTest` |
| Transaction report (CBTRN03C) | 133-character lines, twenty to a page, the edited amount fields, the page, account and grand totals | `TransactionReportWriterTest`, `ReportLinesTest` |
| Interest calculation (CBACT04C) | the disclosure-group rate with the `DEFAULT` fallback, balance × rate ÷ 1200 truncated, one transaction per accrual with the source's fixed classification, one settlement per account | `InterestAccrualWriterTest`, `TransactionBatchIntegrationTest` |
| Statements (CBSTM03A) | both the 80-column and the HTML statement byte for byte, including the `STRING`-delimited name and address truncation and the statement total | `StatementRendererTest`, `StatementLinesTest`, `TransactionBatchIntegrationTest` |
| Master file prints (CBACT02C/03C, CBCUS01C) | every record printed once in key order as the whole record image | `MasterFileRecordsTest` |
| Branch migration (CBEXPORT/CBIMPORT) | the 500-byte record with its 40-byte key area, one-byte type, timestamp, big-endian `COMP` sequence, branch and region; round trip export → import; an unknown record type rejected without failing the run | `MigrationExportRecordTest`, `FixedLengthRecordReaderTest`, `ImportMasterRecordsTest`, `MigrationJobsIntegrationTest` |

### 9.1 Deliberate behaviour deltas — AS-IS defects remediated in this closure

| Ref | Source behaviour (before) | Target behaviour (after) | Corroboration | Test |
|---|---|---|---|---|
| UCR-33 | `CBTRN03C`'s end-of-file branch adds the last transaction's amount to the page, account and grand totals a second time, so a report's totals exceed the sum of its own printed detail lines | totals are accumulated only for a record that was read, so they equal the printed detail lines | the report prints the detail lines it totals; a total that does not equal them cannot be the intended output | `TransactionReportWriterTest` pins the corrected totals |
| UCR-34 | `CBSTM03A` stores a card's transaction count only on a card-number change, so the last card of the file is given an empty statement; the working-storage table also caps at 51 cards × 10 transactions with no overflow check | each statement lists all of the card's transactions, read from the transaction store | the program's purpose is one complete statement per cardholder, and the cap and the dropped group are unbounded-subscript defects rather than rules | `StatementRendererTest`, `TransactionBatchIntegrationTest` |
| UCR-39 (partial) | `CBTRN02C` can leave a transaction's category balance updated when the account rewrite fails (reason 109) | the chunk transaction plus the compensated account adjustment make a posting all-or-nothing | the reject reason exists to report the failure, not to record a half-posted transaction | `PostedTransactionWriterTest`, `TransactionBatchIntegrationTest`; the consistency change is CMD-18 |

All three remain REVIEW REQUIRED: a converted report or statement will not tie line for line to an
archived mainframe one.

### 9.2 What this closure's tests deliberately do not prove

- No comparison against a live CICS, VSAM or JES run (L-03): the expected results are derived from
  the source text, not from a mainframe execution.
- The interest truncation and the source's fixed classifications are asserted AS-IS, so the tests
  prove fidelity to the source, not that the values are correct (UCR-07, UCR-38, UCR-39).
- The branch migration round trip is proved file to file; no receiving branch's load programs were
  available, so the export was not read by a third party.
- File-status abend paths are covered by framework failure handling rather than by injected I/O
  errors, as in the earlier closures.
