# Consistency-Model Delta Register

Every atomicity, ordering, isolation or durability guarantee that differs between CICS/VSAM and the
local Spring/PostgreSQL target. No guarantee is silently dropped.

---

### CMD-01 — Unit of work around the paired rewrite

- **Source boundary:** `COACTUPC 9600-WRITE-PROCESSING` — one CICS unit of work covering
  `READ UPDATE ACCTDAT`, `READ UPDATE CUSTDAT`, `REWRITE ACCTDAT`, `REWRITE CUSTDAT`, committed by the
  implicit syncpoint at `RETURN`.
- **Target component:** `AccountUpdateService.confirm`, annotated `@Transactional`.
- **Source guarantee:** both records commit together or neither does; VSAM recoverable-file backout
  on abend or explicit `SYNCPOINT ROLLBACK`.
- **Target guarantee:** both rows commit together or neither does, under one PostgreSQL transaction at
  `READ COMMITTED`.
- **Changed:** the isolation level. VSAM record locks under CICS effectively serialise the two
  records for the duration of the unit of work; PostgreSQL `READ COMMITTED` additionally allows other
  transactions to read the *old* committed row while this one is in flight. Nothing an in-scope
  program does depends on blocking those readers.
- **Business impact:** none observed — a concurrent enquiry may see the pre-update values a moment
  longer than on CICS.
- **Confidence:** High. **Resolution:** Accepted with rationale.
- **Validation test:** `AccountUpdateServiceTest.aFailingCustomerRewriteRollsTheUnitOfWorkBack`,
  `AccountApiIntegrationTest.theUpdateTransactionFetchesValidatesAndCommits`.

---

### CMD-02 — Explicit rollback replaced by transaction rollback

- **Source boundary:** `COACTUPC 9600` — `SYNCPOINT ROLLBACK` after a failed customer rewrite; the
  failed *account* rewrite path returns without one (UCR-10.4).
- **Target component:** the same `@Transactional` method.
- **Source guarantee:** asymmetric — customer failure explicitly backs out the account rewrite;
  account failure relies on the abend/return path.
- **Target guarantee:** symmetric — any exception rolls the whole transaction back.
- **Changed:** the account-failure path now rolls back *explicitly* rather than implicitly. The
  observable outcome is the same (no partial update), so this is a mechanism change, not a behaviour
  change.
- **Business impact:** none; if anything it removes an ambiguity.
- **Confidence:** High. **Resolution:** Accepted with rationale (recorded because the mechanism
  differs).
- **Validation test:** `AccountUpdateServiceTest.aFailingCustomerRewriteRollsTheUnitOfWorkBack` and
  `confirmRerunsTheEditsSoAnInvalidSubmissionIsNeverWritten` assert that no row changed.

---

### CMD-03 — `READ UPDATE` record lock versus `SELECT … FOR UPDATE`

- **Source boundary:** `COACTUPC 9300`/`9400` with the `UPDATE` option.
- **Target component:** `AccountRepository.lockByAcctId`, `CustomerRepository.lockByCustId`
  (`@Lock(PESSIMISTIC_WRITE)`).
- **Source guarantee:** an exclusive record lock held to the syncpoint; a second transaction's
  `READ UPDATE` fails immediately or waits depending on the CICS/VSAM configuration.
- **Target guarantee:** an exclusive row lock held to commit; a second transaction *waits* by default.
- **Changed:** contention behaviour — waiting instead of an immediate non-normal response. The source
  had a `LOCK_ERROR`-style path for the non-normal response; the target keeps that status for lock
  acquisition failures (for example a statement timeout) but will normally block briefly instead.
- **Business impact:** low. Under contention the operator waits rather than receiving an error; no
  update is lost either way.
- **Confidence:** Medium-High (the source's lock-wait configuration is not in the repository).
- **Resolution:** Accepted with rationale; the `LOCK_ERROR` status is retained so the source's error
  path remains representable.
- **Validation test:** `AccountStoreIntegrationTest.bothRecordsCanBeLockedForUpdate`;
  `AccountUpdateServiceTest.anUnavailableAccountLockStopsBeforeTheCustomerLock` and
  `anUnavailableCustomerLockStopsBeforeAnyRewrite`.

---

### CMD-04 — Optimistic "changed by someone else" check

- **Source boundary:** `COACTUPC 9700-CHECK-CHANGE-IN-REC`, comparing the `ACUP-OLD-*` snapshot from
  the earlier pseudo-conversational turn against the record just read for update.
- **Target component:** the baseline comparison inside `AccountUpdateService.confirm`, using the
  `original` form the client returns.
- **Source guarantee:** a lost update across turns is detected and refused with
  `Record changed by some one else. Please review`.
- **Target guarantee:** identical, with the snapshot travelling in the request body instead of the
  COMMAREA.
- **Changed:** the snapshot is now client-supplied, so a client could tamper with it. In the source it
  was server-held terminal state.
- **Business impact:** none for a local functional-equivalence build; for production this would need a
  server-side snapshot or a version column.
- **Confidence:** High. **Resolution:** Accepted with rationale + REVIEW REQUIRED for production.
- **Validation test:** `AccountApiIntegrationTest.aRecordChangedByAnotherUpdaterIsNotOverwritten`;
  `AccountUpdateServiceTest.aRecordThatMovedSinceItWasFetchedIsNotOverwritten`.

---

### CMD-05 — Read-sequence isolation on the enquiry path

- **Source boundary:** `COACTVWC 9000-READ-ACCT` — three separate `READ`s with no unit-of-work
  intent; each read sees whatever is committed at that moment.
- **Target component:** `AccountReadService.readAccount`, `@Transactional(readOnly = true)`.
- **Source guarantee:** none across the three reads; the screen can show a mix of old and new data.
- **Target guarantee:** stronger — all three reads occur in one `READ COMMITTED` transaction, so they
  see one consistent snapshot per statement and cannot interleave with a commit *within* a statement.
- **Changed:** the target is slightly *more* consistent than the source.
- **Business impact:** none; a more consistent screen cannot break a rule the source enforced.
- **Confidence:** High. **Resolution:** Accepted with rationale.
- **Validation test:** `AccountApiIntegrationTest.aKnownAccountIsReturnedWithItsCustomerAndCard`;
  `AccountReadServiceTest.theReadSequenceIsCrossReferenceThenAccountThenCustomer`.

---

### CMD-06 — Ordering of the two writes

- **Source boundary:** `COACTUPC 9600` — account rewrite strictly before customer rewrite.
- **Target component:** `applyToAccount` then `applyToCustomer`, followed by one flush at commit.
- **Source guarantee:** the account record reaches the file first; a failure between the two leaves
  the account updated until the rollback backs it out.
- **Target guarantee:** the same statement order, but both writes flush inside one transaction, so no
  intermediate state is ever visible to another transaction.
- **Changed:** the intermediate state disappears. Hibernate could also reorder flushes, so the order
  is pinned by explicit `saveAndFlush`-style sequencing in the service and asserted by test.
- **Business impact:** none — the intermediate state was never intended to be observed.
- **Confidence:** High. **Resolution:** Compensated: the write order is explicit and asserted.
- **Validation test:** `AccountUpdateServiceTest.confirmRewritesTheAccountBeforeTheCustomer` verifies
  the order with an inOrder verification; `aFailingCustomerRewriteRollsTheUnitOfWorkBack` asserts that
  the account row is not left updated.

---

### CMD-07 — Batch commit boundary and restart

- **Source boundary:** `CBACT01C` under READACCT — one sequential pass, no intermediate commit; the
  outputs are sequential data sets deleted and recreated by the PREDEL step. A failure means the step
  is rerun from the beginning after the extracts are deleted again.
- **Target component:** `readAcctStep`, chunk size 50, Spring Batch job repository in PostgreSQL.
- **Source guarantee:** all-or-nothing at the data-set level, enforced operationally by PREDEL.
- **Target guarantee:** chunk-level commit with *restart from the last committed chunk* when the same
  job parameters are relaunched, or a full rewrite when `run.id` changes.
- **Changed:** partial output can exist after a failure, and a restart appends rather than starting
  over. This is a genuine difference in durability semantics.
- **Business impact:** medium if an operator relaunches with the same parameters expecting a clean
  file. Mitigated: each writer sets `shouldDeleteIfExists(true)`, so a *new* run reproduces PREDEL
  exactly, and the guide instructs incrementing `run.id` for a fresh extract.
- **Confidence:** High. **Resolution:** Compensated with implementation and documentation; the
  restart-from-chunk case remains a deliberate, documented difference.
- **Validation test:** `ReadAcctJobIntegrationTest.theJobWritesOneRecordPerAccountIntoEachExtract` and
  `aRerunReplacesTheExtractRatherThanAppendingToIt`.

---

### CMD-08 — Durability of the local database

- **Source boundary:** recoverable VSAM files under CICS with logging and forward recovery.
- **Target component:** a local PostgreSQL 16 instance (Docker or native), default `fsync` on, no
  archiving, no replication.
- **Source guarantee:** committed changes survive with forward recovery to any point in time.
- **Target guarantee:** committed changes survive process and machine restart; no point-in-time
  recovery, no backup.
- **Changed:** recoverability beyond crash consistency.
- **Business impact:** none for a local functional-equivalence build; the database is reseeded by a
  single documented command.
- **Confidence:** High. **Resolution:** Accepted with rationale — production durability, HA and DR are
  explicitly out of scope.
- **Validation test:** clean-migration and reseed evidence (`evidence/migration.log`,
  `evidence/db-clean.log`).

---

### CMD-09 — Transaction boundary of a "turn"

- **Source boundary:** each pseudo-conversational turn of CAUP is its own CICS task and unit of work;
  state survives between turns in the COMMAREA, not in a transaction.
- **Target component:** each REST call is its own transaction; state travels in the request body.
- **Source guarantee:** no lock or transaction is held between turns — the operator can sit on the
  screen indefinitely without holding a record.
- **Target guarantee:** identical — nothing is held between calls.
- **Changed:** nothing material; recorded because the mechanism (COMMAREA → request body) changed and
  because it is what makes CMD-04 necessary.
- **Business impact:** none.
- **Confidence:** High. **Resolution:** Accepted with rationale.
- **Validation test:** `AccountApiIntegrationTest.theUpdateTransactionFetchesValidatesAndCommits`,
  which issues each turn as a separate request.

---

### CMD-10 — Two-phase commit across IMS DB, DB2 and MQ

- **Source boundary:** `COPAUA0C` — one CICS unit of work per message, ended by `EXEC CICS SYNCPOINT`
  in `5000-PROCESS-AUTH`, coordinating the IMS DL/I updates (`PAUTSUM0` replace or insert,
  `PAUTDTL1` insert) and, for the fraud path of `COPAUS1C`/`COPAUS2C`, DB2 `AUTHFRDS` — RRS
  two-phase commit across two resource managers.
- **Target component:** `AuthorizationRequestProcessor.process`, `@Transactional` over a single
  PostgreSQL database that owns `pending_auth_summary`, `pending_auth_detail`, `auth_fraud`,
  `auth_request_log` and `auth_reply_outbox`.
- **Source guarantee:** the summary update and the authorization insert commit together across two
  resource managers, or neither does.
- **Target guarantee:** identical atomicity, but from a single resource manager rather than a
  distributed commit — the bounded context owns all five tables, so there is no distributed
  transaction to coordinate.
- **Changed:** the mechanism, not the guarantee. Two-phase commit was eliminated rather than
  emulated, by placing the IMS hierarchy and the DB2 fraud table in the same service's database
  (playbook §8.2.13: each service owns its data; here that *strengthens* the boundary).
- **Business impact:** none.
- **Confidence:** High. **Resolution:** Accepted with rationale — the guarantee is preserved by a
  local transaction.
- **Validation test:** `AuthorizationRequestProcessorIntegrationTest.anApprovedAuthorizationCreatesTheSummaryAndTheDetail`
  and `.theReplyIsWrittenToTheOutboxInTheSameTransaction` (summary, detail, idempotency record and
  outbox row all present after one call), `.anUnknownCardIsDeclinedAndWritesNoAuthorization` (nothing
  is written when the decision writes nothing),
  `AuthorizationApiIntegrationTest.markingAndUnmarkingFraudTogglesTheFlagAndKeepsOneFraudRow`.

---

### CMD-11 — The reply was sent before the authorization was stored

- **Source boundary:** `COPAUA0C 5000-PROCESS-AUTH` — `7100-SEND-RESPONSE` (`MQPUT` with
  `MQPMO-NO-SYNCPOINT`) runs *before* `8400-UPDATE-SUMMARY` and `8500-INSERT-AUTH`, and outside the
  unit of work that commits them.
- **Target component:** `AuthorizationRequestProcessor.process` writes the reply into
  `auth_reply_outbox` inside the same transaction as the summary and the detail;
  `AuthorizationReplyPublisher` publishes it after the commit.
- **Source guarantee:** none. The acquirer could be told "approved" while the IMS write then failed,
  so the credit was never consumed — money authorised against a balance that was not updated.
- **Target guarantee:** a reply is only ever published if the decision that produced it is committed
  (transactional outbox); a committed decision is always eventually replied to.
- **Changed:** strengthened. An approval is no longer answered before it is durable.
- **Business impact:** positive and deliberate. The observable reply content for a successful
  request is byte-identical; only the failure path differs, and in the source that path produced an
  inconsistency. Recorded as a §2.4(3) remediation in the parity evidence.
- **Confidence:** High. **Resolution:** Compensated with a transactional outbox and test.
- **Validation test:** `AuthorizationRequestProcessorIntegrationTest.theReplyIsWrittenToTheOutboxInTheSameTransaction`,
  `AuthorizationReplyPublisherTest.aFailedPublishLeavesTheReplyUnpublishedAndCountsTheAttempt`,
  `.theNamedReplyDestinationAndCorrelationIdOfTheRequestAreCarriedOnTheReply`.

---

### CMD-12 — MQ at-most-once get becomes Kafka at-least-once delivery

- **Source boundary:** `COPAUA0C 3100-READ-REQUEST-MQ` — `MQGET` with `MQGMO-NO-SYNCPOINT`: the
  message leaves the queue immediately and is not returned if the task then fails. The reply is
  `MQPER-NOT-PERSISTENT` with `MQMD-EXPIRY 50`.
- **Target component:** `AuthorizationRequestListener` on `carddemo.authorization.request` with
  manual-immediate acknowledgement, `AuthorizationRequestLogEntity` as the idempotency record, the
  error handler in `KafkaConfig`, and `carddemo.authorization.request.DLT`.
- **Source guarantee:** at most once. A request lost in a failed task is never retried and never
  answered; the acquirer times out.
- **Target guarantee:** at least once with idempotent processing. A failed offset is redelivered a
  bounded number of times with backoff, then dead-lettered; a redelivered request that was already
  decided is not decided twice — the stored reply is republished.
- **Changed:** the delivery semantic (lost → retried), and duplicate replies become possible where
  the source could not produce one.
- **Business impact:** fewer lost authorizations; a requester must tolerate a duplicate reply
  carrying the same correlation id. MQ message expiry has no Kafka equivalent and is replaced by
  topic retention — a reply is no longer discarded after five seconds.
- **Confidence:** High. **Resolution:** Compensated — idempotency key
  (`card number + original authorization date + time + transaction id`), duplicate detection with
  reply replay, bounded retry and dead-letter handling.
- **Validation test:** `AuthorizationRequestProcessorIntegrationTest.aDuplicateDeliveryReplaysTheStoredReplyWithoutAuthorizingTwice`,
  `AuthorizationRequestListenerIntegrationTest.anAuthorizationRequestIsAnsweredOnTheReplyTopic`,
  `.aRedeliveredRequestIsDecidedOnceAndReplaysTheStoredReply`,
  `.aMalformedRequestIsDeadLetteredWithoutRetrying`,
  `.anAccountServiceFailureIsRetriedAndThenDeadLettered`.

---

### CMD-13 — The fraud unit of work spanned two programs and two resource managers

- **Source boundary:** `COPAUS1C PROCESS-PF5-KEY` — `EXEC CICS LINK` to `COPAUS2C`, which inserts or
  updates DB2 `AUTHFRDS` without committing, then `UPDATE-AUTH-DETAILS` replaces the IMS `PAUTDTL1`
  segment, and only the caller's `SYNCPOINT` commits both. A failed segment replace issues
  `SYNCPOINT ROLLBACK`, backing out the DB2 row as well.
- **Target component:** `FraudMarkingService.toggleFraud`, `@Transactional`, upserting `auth_fraud`
  and updating `pending_auth_detail` in one local transaction.
- **Source guarantee:** the fraud report row and the authorization's fraud flag commit together
  across DB2 and IMS, or neither does.
- **Target guarantee:** identical, within one database.
- **Changed:** the mechanism only — a distributed commit across a `LINK` boundary becomes one local
  transaction, because the fraud store moved into the authorization service's own database rather
  than staying a shared DB2 table.
- **Business impact:** none.
- **Confidence:** High. **Resolution:** Accepted with rationale.
- **Validation test:** `AuthorizationApiIntegrationTest.markingAndUnmarkingFraudTogglesTheFlagAndKeepsOneFraudRow`,
  `FraudMarkingServiceTest.aSegmentWhoseComplementedTimeIsAbsentIsRejectedRatherThanTimestampedWrongly`
  (no fraud row is written when the authorization cannot be timestamped),
  `.anAuthorizationThatIsNoLongerThereReturnsTheSourceMessage`.

---

### CMD-14 — Per-card ordering of authorization requests

- **Source boundary:** the request queue `AWS.M2.CARDDEMO.PAUTH.REQUEST` is FIFO and `COPAUA0C`
  drains it one message at a time in a single task, so all requests are totally ordered and no two
  are decisioned concurrently.
- **Target component:** topic `carddemo.authorization.request`, 3 partitions, partition key =
  card number; listener concurrency equal to the partition count; `findByIdForUpdate` taking a
  pessimistic row lock on the account's summary.
- **Source guarantee:** total ordering across *all* cards, and serialised decisioning, because there
  is one consumer.
- **Target guarantee:** ordering per card (same key → same partition → one consumer thread), and
  serialised decisioning per *account* through the row lock on the summary.
- **Changed:** total ordering is lost. Requests for different cards are decisioned concurrently, so
  two requests for two different cards of the same account can interleave where the source would
  have run them in arrival order.
- **Business impact:** the decision depends on available credit at the account level, so the
  interleaving is observable: two concurrent requests on one account no longer see the queue's
  arrival order. Which one is approved when both fit is unchanged; which one is declined when only
  one fits can differ from the mainframe. Total ordering across unrelated cards has no business
  meaning and is not preserved.
- **Confidence:** High. **Resolution:** Compensated — the pessimistic lock makes concurrent
  decisioning on one account serialisable, so available credit can never be over-committed; the
  *order* in which two simultaneous requests are serialised is accepted as non-deterministic and
  recorded as such.
- **Validation test:** `AuthorizationConcurrencyIntegrationTest.twoConcurrentRequestsOnOneAccountCannotBothConsumeTheSameCredit`
  (the compensating lock),
  `AuthorizationRequestListenerIntegrationTest.theReplyGoesToTheDestinationTheRequesterNamed`
  (the card number is the message key of both the request and the reply).

---

### CMD-15 — Purge unit of work: one IMS checkpoint versus one Spring Batch chunk

- **Source boundary:** `CBPAUP0C` — a BMP that deletes expired authorizations and issues
  `EXEC DLI CHKP ID('RMAD…')` after every *n* summaries (the parameter card supplies 1), which
  commits the deletes so far and establishes the restart point.
- **Target component:** `PurgeAuthJobConfig` — a chunk-oriented step whose commit interval is the
  same checkpoint frequency; `AuthSummaryKeysetReader` resumes from the last committed account id in
  the Spring Batch execution context.
- **Source guarantee:** deletes are committed in checkpoint-sized groups; a restarted job resumes
  from the last checkpoint and neither loses nor repeats work.
- **Target guarantee:** deletes are committed in chunk-sized groups; a restarted execution resumes
  from the last committed chunk. Equivalent, with one difference: IMS restarts the *job* from the
  checkpoint's saved position in the database scan, whereas Spring Batch restarts from the saved
  account id, and the purge is naturally idempotent (an already-deleted authorization is simply not
  found again), so a repeated chunk is harmless.
- **Changed:** nothing material; the restart position is recorded in the batch metadata database
  rather than in the IMS log.
- **Business impact:** none.
- **Confidence:** High. **Resolution:** Compensated with implementation and test.
- **Validation test:** `AuthSummaryKeysetReaderTest.aRestartResumesAtTheRootAfterTheLastCheckpointedOne`,
  `PurgeAuthJobIntegrationTest.rootsAreCommittedAtTheConfiguredCheckpointFrequency`,
  `.reRunningTheJobPurgesNothingFurther`.

---

### CMD-16 — Bill payment: one unit of recovery over TRANSACT and ACCTDAT

- **Source boundary:** `COBIL00C` writes the payment to `TRANSACT` and rewrites `ACCTDAT` in the
  same CICS task, so the transaction and the reduced balance commit together or not at all
  (`BRD-COBIL00C.md` BR-14).
- **Target component:** `BillPaymentService` in the transaction context writes `bill_payment` and
  `transaction` in one local database transaction, then calls
  `POST /api/accounts/{id}/balance-adjustments` on the account service, which owns the balance.
- **Source guarantee:** atomicity across the transaction record and the account balance.
- **Target guarantee:** atomicity within the transaction context, plus an idempotent, compensated
  adjustment of the balance. The client's idempotency key is the primary key of `bill_payment`, so a
  replay of the same payment neither writes a second transaction nor adjusts the balance twice; a
  failed adjustment marks the payment `COMPENSATED` and removes the transaction.
- **Changed:** the two writes are no longer one commit. Between them an observer of the account
  service can see the transaction recorded and the balance not yet reduced.
- **Business impact:** the window is a single synchronous call. Its outcomes are: both applied (the
  source's success), neither applied (the source's failure), or the payment compensated, which is
  observable as a `COMPENSATED` row the source has no counterpart for. No path leaves the balance
  reduced without a transaction, which is the outcome the business cares about.
- **Confidence:** High. **Resolution:** Compensated with implementation and test.
- **Validation test:** `BillPaymentServiceTest` (the replay and the compensation paths),
  `TransactionApiIntegrationTest` (the end-to-end payment).

---

### CMD-17 — Report request: internal-reader submission becomes a persisted request

- **Source boundary:** `CORPT00C` writes the `TRANREPT` job stream to the `JOBS` transient-data
  queue, which JES reads; the screen's "submitted for printing" message is sent in the same task
  (`BRD-CORPT00C.md` BR-11, BR-12).
- **Target component:** `ReportRequestService` inserts a `report_request` row in the same local
  transaction that serves the request; `transactionReportJob` consumes pending rows on its schedule.
- **Source guarantee:** the job is queued before the operator is told it was submitted, and the
  queue write is part of the task.
- **Target guarantee:** the request row is committed before the operator is told it was recorded.
  The report itself is produced later, by the batch job.
- **Changed:** submission and execution are decoupled in time by the job's schedule, where the
  internal reader would have started the job as soon as JES picked it up. The source's 1000-line
  cap on the job stream has no equivalent.
- **Business impact:** the operator's wait for a printed report is now bounded by the job schedule
  rather than by JES. The message text is unchanged and remains accurate. A duplicate request is
  visible as two rows and produces two reports, exactly as two submissions would have produced two
  jobs.
- **Confidence:** High. **Resolution:** Accepted with rationale — this is the intended target
  design, not a lost guarantee.
- **Validation test:** `ReportRequestServiceTest` (the request is persisted with the resolved
  range), `TransactionBatchIntegrationTest` (the job consumes it and marks it complete).

---

### CMD-18 — Transaction posting: one unit of recovery over three files

- **Source boundary:** `CBTRN02C` updates `TCATBALF`, rewrites `ACCTDAT` and writes `TRANSACT` for
  each daily transaction in one batch step; a failure abends the step
  (`BRD-CBTRN01C-02C-03C.md` BR-09 … BR-14).
- **Target component:** `postTransactionsJob` — a chunk-oriented step writing
  `transaction_category_balance` and `transaction` in the chunk's database transaction, with the
  account balance adjusted through the account service.
- **Source guarantee:** the three updates for one transaction are in one unit of recovery, and the
  account master is the same file the validation read.
- **Target guarantee:** the category balance and the transaction row are in one chunk transaction;
  the balance adjustment is idempotent on a key derived from the transaction id, so a retried chunk
  cannot add an amount twice.
- **Changed:** the account balance is updated by a remote call rather than a rewrite of the record
  read during validation, so the credit-limit test of BR-04 and the balance update of BR-11 no
  longer read and write the same locked record.
- **Business impact:** two jobs or a job and an online payment posting against one account can
  interleave where the source serialised them through the file. The account service applies each
  adjustment under a row lock, so no amount is lost; the *order* of two concurrent adjustments is
  non-deterministic, which is only observable in the credit-limit decision at the boundary.
- **Confidence:** High. **Resolution:** Compensated with implementation and test.
- **Validation test:** `TransactionBatchIntegrationTest` (posting, rejection and re-run),
  `PostedTransactionWriterTest` (the idempotency key and the reject trailer).

---

## Summary

| Resolution | IDs |
|---|---|
| Accepted with rationale | CMD-01, CMD-02, CMD-03, CMD-04 (with production REVIEW REQUIRED), CMD-05, CMD-08, CMD-09, CMD-10, CMD-13, CMD-17 |
| Compensated with implementation and test | CMD-06, CMD-07, CMD-11, CMD-12, CMD-14, CMD-15, CMD-16, CMD-18 |
| REVIEW REQUIRED (unresolved) | none |

CMD-10 … CMD-15 were added for the authorization closure, where the source coordinated IMS DB, DB2
and MQ under one CICS unit of work. Two of the six are accepted because the guarantee is preserved
rather than lost: the authorization bounded context owns the converted IMS hierarchy *and* the
converted DB2 fraud table, so the source's two-phase commit becomes one local transaction. The four
compensated entries are the genuinely lost guarantees — reply-before-write (CMD-11), at-most-once
delivery (CMD-12), total request ordering (CMD-14) and the IMS checkpoint restart position (CMD-15)
— each with a named proving test. The business decisions they raise (duplicate replies,
non-expiring replies) are carried in `11-unsupported-construct-register.md` UCR-21.

CMD-16 … CMD-18 were added for the transaction closure. All three arise from the same split: the
transaction record now lives in the transaction context and the account balance in the account
context, so what the source did in one unit of recovery is a local transaction plus an idempotent,
compensated call. The lost guarantee in each case is the joint commit, and the compensating control
in each case is an idempotency key derived from the transaction identity plus a row lock inside the
account service.
