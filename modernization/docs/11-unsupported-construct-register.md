# Unsupported Construct Register

Every unsupported, ambiguous, incomplete, missing, conflicting or unresolvable construct found in the
in-scope closure. Nothing here was silently ignored or silently "fixed".

---

### UCR-01 — Two supplied copies of the account file disagree on one record

- **Source artifact:** `app/data/ASCII/acctdata.txt`, `app/data/EBCDIC/AWS.M2.CARDDEMO.ACCTDATA.PS`
- **Source location:** record 49 (account `00000000049`), fields `ACCT-ADDR-ZIP` and `ACCT-GROUP-ID`
- **Construct:** the ASCII copy holds `A000000000` followed by zeroes; the EBCDIC copy holds
  `ZEROAPR`. The other 49 records and all customer and cross-reference records agree.
- **Business impact:** low. Neither field is read or written by any in-scope program; `ACCT-GROUP-ID`
  is displayed and editable on the update screen, so a demonstration seeded from the EBCDIC copy
  would show a different group id for that one account.
- **Technical impact:** the seed load must choose a copy. The ASCII copy was chosen, because the
  supplied JCL REPRO loads the PS data set that the ASCII files mirror and because it is internally
  consistent with the rest of the file.
- **Modernisation risk:** low, but it means the two supplied data sets are not interchangeable.
- **Local handling:** asserted as a known difference in `tools/reconcile_sample_data.py`
  (`KNOWN_DATA_DIFFERENCES`) so reconciliation stays green while the discrepancy remains visible in
  the evidence log; not normalised.
- **Manual review:** required — the data owner must say which copy is authoritative.
- **Confidence:** High (the difference is directly observable). **Status:** Open, REVIEW REQUIRED.

---

### UCR-02 — Commented-out validation of Address Line 2

- **Source artifact:** `app/cbl/COACTUPC.cbl`
- **Source location:** `1200-EDIT-MAP-INPUTS`, immediately before the `City` edit
- **Construct:** `* MOVE 'Address Line 2' TO WS-EDIT-VARIABLE-NAME` and the associated edit call are
  commented out, with the comment "Address Line 2 is optional". The field is therefore accepted with
  no validation at all — not even the optional-alphanumeric edit.
- **Business impact:** any characters, including control characters, can be stored in
  `CUST-ADDR-LINE-2`.
- **Technical impact:** none in the target beyond reproducing it.
- **Modernisation risk:** medium — a reviewer may mistake the omission for a conversion defect.
- **Local handling:** preserved AS-IS: `addressLine2` is accepted unvalidated (BRD-COACTUPC BR-34).
  The dead code is recorded, not deleted.
- **Manual review:** required — decide whether the field should be validated. Recommended action:
  apply the optional-alphanumeric edit *if* the business confirms it.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-03 — Platform delivery constructs with no local equivalent

- **Source artifact:** `COACTVWC`, `COACTUPC`, `app/bms/*.bms`, `app/cpy/CSSTRPFY.cpy`,
  `COCOM01Y.cpy`, IBM `DFHAID`, `DFHBMSCA`
- **Source location:** all `EXEC CICS SEND/RECEIVE MAP`, `XCTL`, `RETURN TRANSID`, AID tests,
  attribute-byte moves
- **Construct:** 3270 terminal transport, pseudo-conversational COMMAREA state, PF-key AIDs,
  attribute bytes, mapset/map names, cursor positioning.
- **Business impact:** none. Playbook §6.2 places these outside the parity bar; the *business* intent
  of each key and of each highlight is preserved (`06-screen-mapping.md` §5, §6).
- **Technical impact:** state moves from COMMAREA into the request/response envelope; highlighting
  moves from attribute bytes into `fieldFlags`.
- **Modernisation risk:** low.
- **Local handling:** modernised away deliberately. `DFHAID`/`DFHBMSCA` are not reimplemented.
- **Manual review:** not required.
- **Confidence:** High. **Status:** Closed — accepted.

---

### UCR-04 — `COBDATFT` is called but not present in the repository

- **Source artifact:** `app/cbl/CBACT01C.cbl` (caller), `app/cpy/CODATECN.cpy` (interface)
- **Source location:** `1300-POPUL-ACCT-RECORD`, `CALL 'COBDATFT' USING CODATECN-*`
- **Construct:** the date-formatting module itself is missing; only its parameter copybook and the
  caller's usage (`CODATECN-TYPE '2'`, `CODATECN-OUTTYPE '2'`, 20-byte in/out areas, a status field)
  are available.
- **Business impact:** the reissue date written to the 107-byte extract could differ from the real
  module's output for inputs outside the observed pattern.
- **Technical impact:** `CobdatftDateFormatter` reconstructs the type-2 to type-2 conversion from the
  copybook and the caller's field usage; other type combinations return a status the caller ignores.
- **Modernisation risk:** medium for the extract only; no online behaviour depends on it.
- **Local handling:** implemented from the interface evidence, with the reconstruction and its limits
  documented in the class Javadoc; the caller's truncation of the 8-digit result into a 10-byte field
  is preserved.
- **Manual review:** required — supply the real module or its specification and re-verify the extract.
- **Confidence:** Medium. **Status:** Open, REVIEW REQUIRED.

---

### UCR-05 — Unchecked return codes, unreachable paragraphs and one-branch fields

- **Source artifact:** `CBACT01C`, `COACTUPC`
- **Source locations and constructs:**
  1. `CBACT01C 1300-POPUL-ACCT-RECORD` — the `COBDATFT` status field is never tested after the call.
  2. `COACTUPC 1230-EDIT-ALPHANUM-REQD` and `1240-EDIT-ALPHANUM-OPT` — no `PERFORM` reaches either
     paragraph; every field that would plausibly use them is routed to the alphabetic edits instead.
  3. `CBACT01C 1300` — the current-cycle-debit output field is populated on one branch only (stored
     value zero), so on the other branch the record area keeps the previous record's content.
  4. `CBACT01C 1400` — array occurrences 4 and 5 are declared in the output layout and never
     populated.
- **Business impact:** (1) a formatting failure would go unnoticed; (3) the first extract record can
  carry uninitialised content in that field and later records can repeat a previous account's value.
- **Technical impact:** all four are reproduced rather than corrected, which makes the extract
  byte-comparable to the source output.
- **Modernisation risk:** medium for (3) — a consumer of the extract may be relying on the repeated
  value.
- **Local handling:** (1) the formatter returns a status the aggregator deliberately does not act on;
  (2) the two paragraphs are implemented as `FieldEditor` methods and covered by unit tests, but no
  production path calls them, matching the source; (3) `AcctCompRecordAggregator` keeps the field
  between items; (4) zeroes from `INITIALIZE`.
- **Manual review:** required for (1) and (3).
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-06 — `CEEDAYS` (Language Environment date service) has no local equivalent

- **Source artifact:** `app/cbl/CSUTLDTC.cbl`
- **Source location:** `CALL "CEEDAYS" USING WS-DATE-TO-TEST, WS-DATE-FORMAT, OUTPUT-LILLIAN,
  FEEDBACK-CODE`
- **Construct:** an IBM LE callable service returning a Lillian day number and a feedback code whose
  severity and message number `CSUTLDTC` maps to result strings.
- **Business impact:** the *observable* result of `CSUTLDTC` is one of ten result strings and a
  severity; those are preserved. The Lillian number itself is never used by any in-scope caller.
- **Technical impact:** `DateValidationService` performs strict `java.time` parsing of the
  `YYYY-MM-DD` mask the callers use and maps failures onto the same result strings
  (`Date is invalid`, `Invalid month`, `Nonnumeric data`, …). Feedback conditions that only an LE
  implementation can raise (`Invalid Era`, `Unsupp. Range`, `Bad Pic String`, `Insufficient`) cannot
  be produced locally for the single mask in use.
- **Modernisation risk:** low for the in-scope callers, medium if other masks are introduced later.
- **Local handling:** implemented as above and unit-tested against the mapping table in
  `BRD-CSUTLDTC.md`.
- **Manual review:** required only if `CSUTLDTC` must serve other date masks.
- **Confidence:** Medium-High. **Status:** Open, REVIEW REQUIRED (limited).

---

### UCR-07 — Hard-coded literals requiring business sign-off

- **Source artifact:** `CBACT01C`, `COACTUPC`, `COACTVWC`, `COUSR00C`, `COADM01C`
- **Source locations and values:**
  | Value | Location | Meaning |
  |---|---|---|
  | `2525.00` | `CBACT01C 1300` | default current-cycle debit when the stored value is zero |
  | `1005.00`, `1525.00`, `-1025.00`, `-2500.00` | `CBACT01C 1400` | array occurrence amounts |
  | `300` / `850` | `COACTUPC 1275-EDIT-FICO-SCORE` | FICO acceptance range |
  | `1582` / century bounds | `COACTUPC` date edits via `CSUTLDPY` | earliest acceptable year |
  | `12` / `39` | `CBACT01C 1550`/`1575` | variable record lengths |
  | `'0001'`, `'9999'` | `COACTVWC`/`COACTUPC` `ABEND-ROUTINE` | abend codes |
  | `10` | `COUSR00C` `PROCESS-PAGE-FORWARD`/`-BACKWARD` loop bounds, the `OCCURS 10` array and the map field names `USRID01`-`USRID10` | rows on one page of the user list |
  | option count | `COADM02Y` `CDEMO-ADMIN-OPT-COUNT` | number of administration options |
- **Business impact:** the amounts are demonstration values that will appear in any extract produced
  locally; the FICO bounds and the year bound are genuine acceptance rules but are not externalised.
- **Technical impact:** each literal is a named constant in the target
  (`AcctCompRecordAggregator.ZERO_DEBIT_DEFAULT`, `AcctArrayRecordAggregator.OCCURRENCE_*`,
  `FieldEditor` FICO bounds, `UserListService.PAGE_SIZE`, the `AdminMenuService` option list) so it
  can be changed in one place once signed off.
- **Modernisation risk:** medium — the amounts are almost certainly not real business defaults.
- **Local handling:** preserved exactly, flagged in `BRD-CBACT01C.md`, `BRD-COACTUPC.md`,
  `BRD-COUSR00C.md` and `BRD-COADM01C.md`.
- **Manual review:** required for every row. The page size and the option count are structural rather
  than monetary, so they need confirmation only if the screens change.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-08 — `EXEC CICS ABEND` / `CEE3ABD` transaction abends

- **Source artifact:** `COACTVWC` (`ABEND-ROUTINE`, code `9999`, plus `'0001'` on an unknown state),
  `COACTUPC` (`ABEND-ROUTINE`, code `9999`), `CBACT01C` (`CALL 'CEE3ABD'`, code 999)
- **Construct:** an abnormal transaction/step termination that also backs out the unit of work.
- **Business impact:** the operator sees a terminal abend rather than a message; no data is
  committed. The observable business outcome — nothing changed, the work failed — is preserved.
- **Technical impact:** the target throws `StorageAccessException`, which `ApiExceptionHandler` maps
  to HTTP 500 carrying the source's `WS-FILE-ERROR-MESSAGE` text, and the transaction rolls back.
  Batch failures fail the step instead of abending the job.
- **Modernisation risk:** low. There is no dump, no abend code and no ASRA equivalent locally.
- **Local handling:** as above; the abend codes are retained as constants in the message text so the
  path is recognisable in logs.
- **Manual review:** not required for local functional equivalence; operational alerting is out of
  scope.
- **Confidence:** High. **Status:** Closed — accepted.

---

### UCR-09 — Docker image build and Compose startup could not be verified

- **Source artifact:** not a source construct — `account-service/Dockerfile`,
  `identity-service/Dockerfile`, `gateway/Dockerfile`, `batch-account-extract/Dockerfile`,
  `frontend/Dockerfile`, `modernization/docker-compose.yml`
- **Construct:** the playbook requires evidence for `docker compose build`/`up`.
- **Business impact:** none on the converted behaviour.
- **Technical impact:** the base images could not be pulled from this machine: Docker Hub returned
  `429 Too Many Requests` and the Public ECR mirror returned a data-limit error
  (`evidence/docker-build.log`).
- **Modernisation risk:** low — the assets are conventional, but they are **unverified**.
- **Local handling:** the assets ship as-is; the local execution guide documents the direct
  `mvn`/`java`/`npm` path, which *was* verified end to end, as the primary route.
- **Manual review:** required — run `docker compose build && docker compose up` on a machine with
  registry access.
- **Confidence:** High (the failure is a registry limit, not a defect in the assets).
  **Status:** Open, REVIEW REQUIRED.

---

### UCR-10 — Duplicate and contradictory conditions

- **Source artifact:** `COACTVWC`, `COACTUPC`
- **Source locations and constructs:**
  1. `COACTVWC 1300-SETUP-SCREEN-ATTRS` — two `WHEN` branches with identical conditions and
     identical cursor placement; the second is unreachable.
  2. `COACTVWC 9000-READ-ACCT` — when the cross-reference read succeeds and the account read fails,
     the previously loaded account fields are not cleared, so a stale account can remain on the
     screen next to a fresh customer.
  3. `COACTUPC 9700-CHECK-CHANGE-IN-REC` — the date-of-birth comparison uses different field offsets
     from the ones the same program uses when building the record, so a date-of-birth-only external
     change may not be detected.
  4. `COACTUPC 9600-WRITE-PROCESSING` — the account rewrite failure path returns without an explicit
     `SYNCPOINT ROLLBACK`, while the customer rewrite failure path issues one.
- **Business impact:** (2) misleading display; (3) a lost-update window for one field; (4) relies on
  the implicit backout of the abend path rather than an explicit rollback.
- **Technical impact:** (1) and (4) collapse naturally in the target (one branch; one transaction that
  rolls back on any failure). (2) and (3) are business-visible, so they are **preserved AS-IS** —
  intended behaviour is not corroborated anywhere.
- **Modernisation risk:** (3) medium.
- **Local handling:** (2) the read chain leaves the earlier values in place exactly as the source
  does; (3) the concurrency comparison reproduces the source's field selection. Both are flagged in
  `BRD-COACTVWC.md` / `BRD-COACTUPC.md` and covered by tests asserting the AS-IS outcome.
- **Manual review:** required for (2) and (3).
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-11 — Missing `ELSE` / `WHEN OTHER` on business decisions

- **Source artifact:** `COACTVWC`, `COACTUPC`, `COSGN00C`, `COUSR01C`
- **Source location:** the `EVALUATE TRUE` blocks in `0000-MAIN` of both account programs and the
  `EVALUATE` over `DFHRESP` values in the `92xx`-`94xx` read paragraphs; `COSGN00C`
  `READ-USER-SEC-FILE` line 230 (`IF CDEMO-USRTYP-ADMIN`); `COUSR01C` `PROCESS-ENTER-KEY` line 158
  (`MOVE USRTYPEI OF COUSR1AI TO SEC-USR-TYPE`)
- **Construct:** several `EVALUATE`s have no `WHEN OTHER`; the read paragraphs do have one (mapped to
  the fatal path), but the state machines fall through silently for combinations the flags allow.
- **Business impact:** an unanticipated state produces no message and no action; the screen is simply
  re-sent (COACTVWC additionally abends with `'0001'` for one such case). In the identity programs the
  same omission has a security consequence: sign-on tests only for user type "A", so any other stored
  value — including a value that is neither "A" nor "U" — silently becomes a regular operator, and
  `COUSR01C` accepts any single character as the user type of a new user, so such records can be
  created through the supplied screens.
- **Technical impact:** the target's status enum makes the state space explicit and total, so a
  fall-through becomes an unreachable default rather than silent inaction.
- **Modernisation risk:** low for the account state machines, medium for the user type, because the
  omission decides which menu an operator reaches.
- **Local handling:** the reachable behaviour is preserved; the unreachable default throws, which is
  a deviation only for states the source could not reach. The identity behaviour is preserved as well:
  `SignedOnUser` grants administrator authority only for "A" and treats every other value as a regular
  operator, and the user type of a new user is accepted unvalidated.
- **Manual review:** required for the user-type omissions only — whether an unknown user type should
  be rejected at sign-on and whether the keyed user type should be constrained to "A"/"U" are business
  decisions, and both change observable behaviour.
- **Confidence:** Medium-High. **Status:** Open, REVIEW REQUIRED (user type); the account state
  machines are closed — accepted.

---

### UCR-12 — Absent operational, scheduler and specification evidence

- **Source artifact:** repository-wide
- **Construct:** no Control-M/CA-7/OPC definitions, no runbooks, no design or functional
  specifications, no test data expectations, no production job schedule.
- **Business impact:** the batch schedule, downstream consumers of the three extracts, and the
  operational recovery procedure are unknown.
- **Technical impact:** the batch job is documented for manual local launch only; no scheduler is
  introduced (playbook §4.8).
- **Modernisation risk:** medium for a production migration, none for this local build.
- **Local handling:** the gap is registered; no behaviour was inferred from artifacts that do not
  exist.
- **Manual review:** required before any production planning.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-13 — Commented-out long-message send

- **Source artifact:** `COACTVWC`, `COACTUPC`, `COADM01C`, `COUSR01C`, `COUSR00C`
- **Source location:** the plain-text/long-message send near `SEND-PLAIN-TEXT`; `COADM01C` lines
  152-153; `COUSR01C` line 268; `COUSR00C` line 592 (the commented `GTEQ` of `STARTBR-USER-SEC-FILE`)
- **Construct:** a `SEND TEXT` of a longer diagnostic message is commented out, leaving only the
  short `ERRMSG` line for some failures. Three further pieces of commented-out logic exist in the
  identity programs: the option name inside the "not installed" message, the `DISPLAY` of the failing
  response codes of the user add, and the `GTEQ` option of the user-list browse — the last of which
  means the browse positions on an exact key match rather than the next higher key.
- **Business impact:** the operator sees less detail than the code once intended, and the commented
  `GTEQ` decides where a keyed user list starts.
- **Technical impact:** none; the target returns the short message only, omits the option name, and
  reproduces the browse positioning the active code actually performs.
- **Modernisation risk:** low.
- **Local handling:** dead code recorded, not deleted; short-message behaviour preserved. The browse
  positioning is documented as `COUSR00C` BR-03 so the commented alternative is not mistaken for the
  intended rule.
- **Manual review:** optional. **Confidence:** High. **Status:** Closed — accepted.

---

### UCR-14 — Clear-text credentials and user-existence disclosure in sign-on

- **Source artifact:** `app/cbl/COSGN00C.cbl`, `app/cbl/COUSR01C.cbl`, `app/cbl/COUSR02C.cbl`,
  `app/cpy/CSUSR01Y.cpy`, `app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS`
- **Source location:** `READ-USER-SEC-FILE` line 223 (`IF SEC-USR-PWD = WS-USER-PWD`) and lines
  242-256; `COUSR02C` `PROCESS-ENTER-KEY` line 169; `SEC-USR-PWD PIC X(08)`
- **Construct:** the password is stored in the record in clear text, compared literally, redisplayed
  on the update screen, and limited to eight characters. There is no hashing, salting, expiry,
  history, complexity rule or failed-attempt lock-out anywhere in the closure. The two distinct
  messages "User not found. Try again ..." and "Wrong Password. Try again ..." also disclose whether
  a user id exists.
- **Business impact:** the behaviour *is* the business rule (COSGN00C BR-06, BR-07) and cannot be
  changed without changing observable behaviour and the seed data.
- **Technical impact:** the converted service compares the stored value as the source does, so the
  parity tests pass; the credential store is a converted VSAM record, not a production identity
  store. Only the transport changed: the browser receives a signed JWT instead of a COMMAREA, so the
  password is sent once per sign-on rather than held in session state.
- **Modernisation risk:** high for production, none for this local build.
- **Local handling:** preserved AS-IS and isolated in `SignOnService`, so replacing it means
  replacing one class. The JWT signing key is supplied by configuration and is a throwaway value
  locally (`15-local-execution-guide.md`).
- **Manual review:** **required before any production use** — password hashing, lock-out and a single
  generic failure message must be decided by the business, because all three change what the operator
  sees.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-15 — Unchecked outcomes in the user-maintenance programs

- **Source artifact:** `app/cbl/COUSR03C.cbl`, `app/cbl/COUSR00C.cbl`, `app/cbl/COUSR01C.cbl`
- **Source location:** `COUSR03C` `DELETE-USER-INFO` lines 188-191; `COUSR00C` lines 608, 642, 676;
  `COUSR01C` line 268
- **Construct:** `DELETE-USER-INFO` performs the read for update and then the delete without testing
  the read's outcome, so a "User ID NOT found..." from the read is reported *and* the delete is still
  attempted. In `COUSR00C` the browse response codes reach only a `DISPLAY`, and in `COUSR01C` that
  `DISPLAY` is commented out, so the failing response code is lost entirely.
- **Business impact:** the operator can be shown a not-found message followed by the outcome of an
  operation that should never have been attempted; diagnostics for genuine file failures do not exist.
- **Technical impact:** the converted service reads and deletes inside one transaction, so a missing
  record raises before the delete runs — the sequence cannot be reproduced. The lost response codes
  become logged exceptions, which is strictly more information than the source kept.
- **Modernisation risk:** low; the difference is only observable when the record disappears between
  the two operations.
- **Local handling:** the deviation is recorded here and in
  `12-consistency-model-delta-register.md`; the commented-out `DISPLAY` is recorded, not restored.
- **Manual review:** optional. **Confidence:** High. **Status:** Closed — accepted.

---

### UCR-16 — Message defects preserved verbatim

- **Source artifact:** `app/cbl/COUSR03C.cbl`, `app/cbl/COUSR00C.cbl`, `app/cbl/COADM01C.cbl`
- **Source location:** `COUSR03C` `DELETE-USER-SEC-FILE` line 332; `COUSR00C` lines 212, 251, 603;
  `COADM01C` lines 152-153 (the commented `CDEMO-ADMIN-OPT-NAME`)
- **Construct:** four separate message defects. A failed *delete* reports "Unable to Update User...".
  The invalid-selection message of the user list is the only message in the closure with no trailing
  ellipsis. Two different causes produce nearly identical texts ("You are already at the top of the
  page..." for refusing to page back, "You are at the top of the page..." for a browse that could not
  be positioned). The "This option is not installed ..." message of the admin menu had the option name
  commented out, so it never says which option. In addition, `COUSR00C` `PROCESS-ENTER-KEY` acts on
  the first selected row only and discards any other selection without telling the operator.
- **Business impact:** operators receive wrong or ambiguous wording; support cannot distinguish the
  two paging conditions from the message alone.
- **Technical impact:** none — every literal is reproduced exactly in `UserMessages`, including the
  wrong one.
- **Modernisation risk:** low, but a reviewer will read them as conversion defects, which is why they
  are registered.
- **Local handling:** preserved verbatim; parity tests assert the wrong wording so that no future
  change silently "corrects" it.
- **Manual review:** required — the business should decide whether to correct the delete message and
  the ambiguous paging wording; both are observable changes.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-17 — The CICS MQ trigger mechanism has no target equivalent

- **Source artifact:** `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl`,
  `app/app-authorization-ims-db2-mq/csd/CRDDEMO2.csd`, module `README.md`
- **Source location:** `1000-INITIALIZE` `EXEC CICS RETRIEVE INTO(MQTM)`; CSD transaction `CP00`;
  the trigger definition on queue `AWS.M2.CARDDEMO.PAUTH.REQUEST`
- **Construct:** the program is not a server loop. It is started by the MQ trigger monitor, is told
  which queue to read through the `MQTM` trigger message, drains up to 500 messages and ends. The
  trigger attributes of the queue (trigger type, depth, the process definition naming `CP00`) are
  not in the supplied CSD, so the exact trigger condition cannot be reconstructed.
- **Business impact:** none directly; it is a delivery mechanism, not a rule. It does determine how
  many concurrent readers exist and therefore how much per-card interleaving the source allowed.
- **Technical impact:** the target is a continuously running Kafka listener container with a fixed
  concurrency instead of a triggered, self-terminating task. The 500-message run limit
  (`COPAUA0C` BR-05) and the five-second queue wait (BR-02) have no purpose in that model and are
  not reproduced; they are recorded as behaviour that was deliberately dropped because they are
  properties of the trigger mechanism.
- **Modernisation risk:** medium — a reviewer expecting a literal conversion will look for both
  literals. Throughput characteristics differ: a continuously polling consumer with 3 partitions is
  not the same load profile as a depth-triggered task.
- **Local handling:** `AuthorizationRequestListener` with `concurrency` bound to the partition
  count; the run limit and wait interval are documented in
  `23-messaging-modernization-mapping.md` as retired trigger mechanics.
- **Manual review:** required — capacity planning must be redone against the real trigger
  attributes and production message rates.
- **Confidence:** Medium (the trigger attributes themselves are missing). **Status:** Open, REVIEW REQUIRED.

---

### UCR-18 — Unreachable decline reasons and unchecked outcomes in COPAUA0C

- **Source artifact:** `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl`
- **Source location:** `6000-MAKE-DECISION` `EVALUATE TRUE` (the `CARD-NOT-ACTIVE`,
  `ACCOUNT-CLOSED`, `CARD-FRAUD`, `MERCHANT-FRAUD` branches); `1000-INITIALIZE` `RETRIEVE … NOHANDLE`;
  `1200-SCHEDULE-PSB`; `5600-READ-PROFILE-DATA`
- **Construct:** four decline reasons (`4200`, `4300`, `5100`, `5200`) are coded but the conditions
  that select them are never set anywhere in the closure, so those branches cannot be reached. The
  trigger retrieve is issued `NOHANDLE` and its outcome is never tested. A failed PSB schedule is
  logged and processing continues into DLI calls that must then fail.
- **Business impact:** the reason codes exist in the contract (and `COPAUS1C` displays them) but no
  authorization can ever carry them. Card status, account status and fraud lists are therefore *not*
  enforced at authorization time in the supplied source.
- **Technical impact:** `DeclineReason` carries all the codes, including `4400` and `5300` from the
  display table, so the contract is complete; `AuthorizationDecisionEngine` can only produce
  `0000`, `3100` and `4100`, exactly as the source can.
- **Modernisation risk:** high if misread — implementing card-status or fraud-list checks would be
  inventing a business rule the source does not have.
- **Local handling:** the unreachable branches are implemented as reachable code in
  `DeclineReason`/`AuthorizationReadState` but nothing sets them; `AuthorizationDecisionEngineTest`
  asserts the three reachable outcomes and documents the rest.
- **Manual review:** required — the business must say whether card status, account status and fraud
  screening are *meant* to decline an authorization. This is the single largest functional gap in the
  closure.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-19 — The authorization request message is parsed without any validation

- **Source artifact:** `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl`, copybook `CCPAURQY`
- **Source location:** `2100-EXTRACT-REQUEST-MSG` (`UNSTRING … DELIMITED BY ','`, then
  `FUNCTION NUMVAL`)
- **Construct:** the comma-delimited request is split into 18 fields with no check on field count,
  length, numeric content or the card number. A short message leaves the remaining fields blank and
  the authorization is decisioned anyway; `NUMVAL` of non-numeric amount text is
  implementation-defined.
- **Business impact:** a malformed request can be decisioned and stored, and a non-numeric amount
  can be treated as zero (and therefore approved).
- **Technical impact:** the target cannot reproduce "implementation-defined". `AuthorizationRequestMessage.parse`
  requires the 18 fields and a parseable amount, and raises `MalformedAuthorizationRequestException`
  otherwise, which goes straight to the dead-letter topic without retry.
- **Modernisation risk:** medium — this is a deliberate behaviour delta on invalid input only.
- **Local handling:** implemented as above; `AuthorizationMessageLayoutTest` pins both the valid
  layout and the rejection.
- **Manual review:** required — confirm that rejecting a malformed request to a dead-letter topic is
  preferred to decisioning it with blank fields.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-20 — Declared-but-empty profile lookup

- **Source artifact:** `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl`
- **Source location:** `5600-READ-PROFILE-DATA` — the paragraph body is `CONTINUE`
- **Construct:** the decision flow performs a profile read that does nothing. There is no profile
  data store in the supplied scope and no copybook for one.
- **Business impact:** none today; it marks an intended extension point (per-cardholder or
  per-product authorization profiles) that was never implemented.
- **Technical impact:** none — nothing to convert.
- **Modernisation risk:** low.
- **Local handling:** deliberately not represented in the target; recorded here so its absence is not
  read as an omission.
- **Manual review:** optional.
- **Confidence:** High. **Status:** Closed — accepted.

---

### UCR-21 — MQ delivery and reply guarantees cannot be reproduced exactly

- **Source artifact:** `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl`, module `README.md`
- **Source location:** `3100-READ-REQUEST-MQ` (`MQGMO-NO-SYNCPOINT`), `7100-SEND-RESPONSE`
  (`MQPER-NOT-PERSISTENT`, `MQMD-EXPIRY 50`), `5000-PROCESS-AUTH` (reply before the IMS write)
- **Construct:** the request is read outside the unit of work, so a failure after the get loses the
  request (at-most-once). The reply is non-persistent and expires after five seconds. The reply is
  sent *before* the authorization is stored.
- **Business impact:** the source can answer "approved" and then fail to store the authorization,
  leaving the credit not consumed; and it can lose a request outright.
- **Technical impact:** Kafka consumption is at-least-once, which is the opposite trade. The target
  therefore writes the decision and the reply row in one transaction and publishes from the outbox,
  and de-duplicates on the request identity.
- **Modernisation risk:** medium — the target is *stricter* than the source in both directions.
- **Local handling:** compensated; see `12-consistency-model-delta-register.md` CMD-10, CMD-11,
  CMD-12 with `AuthorizationRequestProcessorIntegrationTest` and
  `AuthorizationRequestListenerIntegrationTest` as the proving tests. Message expiry is not
  represented: Kafka retention is a topic property, recorded in
  `23-messaging-modernization-mapping.md`.
- **Manual review:** required — confirm that a reply is allowed to be delivered more than once and
  that a non-expiring reply is acceptable.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED (behaviour compensated, business decision outstanding).

---

### UCR-22 — Cash balance is reset to zero on every approval

- **Source artifact:** `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl`
- **Source location:** `8400-UPDATE-SUMMARY` — `MOVE 0 TO PA-CASH-BALANCE` in the approved branch
- **Construct:** every approved authorization clears the account's pending cash balance, whether or
  not the authorization is a cash advance. The processing code that would identify a cash
  transaction is stored but never examined.
- **Business impact:** potentially material — pending cash advances stop constraining the cash limit
  as soon as any authorization is approved. The cash limit is displayed on CPVS, so the figure an
  operator sees is affected.
- **Technical impact:** none; one assignment.
- **Modernisation risk:** high if "corrected" silently — cash accounting would then differ from the
  mainframe.
- **Local handling:** carried forward unchanged in `AuthorizationRequestProcessor.updateSummary`
  with the reasoning in the code comment; asserted by `AuthorizationRequestProcessorIntegrationTest`.
- **Manual review:** required — the business must say whether this is intended.
- **Confidence:** High (the statement is unambiguous; its intent is not). **Status:** Open, REVIEW REQUIRED.

---

### UCR-23 — Two-digit years throughout the authorization closure

- **Source artifact:** `cbl/COPAUA0C.cbl`, `cbl/COPAUS0C.cbl`, `cbl/COPAUS1C.cbl`,
  `cbl/COPAUS2C.cbl`, copybooks `CCPAURQY`, `CIPAUDTY`
- **Source location:** `PA-AUTH-ORIG-DATE PIC X(6)` (YYMMDD), the `MM/DD/YY` screen edits, the
  `99999 - YYDDD` key, `EXEC CICS FORMATTIME MMDDYY`
- **Construct:** authorization dates are carried, keyed, displayed and reported with a two-digit
  year, and the authorization key is built from a five-digit Julian date.
- **Business impact:** dates are ambiguous beyond a century boundary, and the key sort order breaks
  at the century.
- **Technical impact:** the target stores the original six-character date as supplied
  (`auth_orig_date char(6)`) so the stored value is byte-identical, and derives the century as 20xx
  when it must build a timestamp for the fraud row.
- **Modernisation risk:** medium — the century assumption is a target-side decision.
- **Local handling:** preserved; the century assumption is a single constant in `AuthorizationKey`
  and is asserted by `AuthorizationKeyTest`.
- **Manual review:** required — confirm the 20xx window.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-24 — Detail screen sets an error with no message

- **Source artifact:** `app/app-authorization-ims-db2-mq/cbl/COPAUS1C.cbl`
- **Source location:** `PROCESS-ENTER-KEY` — the validation of the account id and the selected
  authorization key sets `WS-ERR-FLG` to `'Y'` without moving any text to `WS-MESSAGE`
- **Construct:** entering the detail screen without a numeric account id or with a blank
  authorization key produces an empty screen with an empty message line.
- **Business impact:** the operator gets no explanation. In practice the condition is unreachable
  from CPVS, which always supplies both.
- **Technical impact:** the target must answer *something* to an HTTP request.
- **Modernisation risk:** low.
- **Local handling:** the target answers the same validation messages CPVS uses
  (`Please enter Acct Id...`, `Acct Id must be Numeric ...`) rather than an empty body; recorded here
  as a deliberate, minimal delta on an unreachable path.
- **Manual review:** optional.
- **Confidence:** High. **Status:** Closed — accepted.

---

### UCR-25 — Julian-day age arithmetic is wrong across a year boundary

- **Source artifact:** `app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl`
- **Source location:** `4000-CHECK-IF-EXPIRED` —
  `COMPUTE WS-DAY-DIFF = CURRENT-YYDDD - WS-AUTH-DATE`
- **Construct:** both operands are five-digit `YYDDD` values, so the subtraction is only a day count
  inside one year. On 1 January (`26001`) an authorization from 31 December (`25365`) yields −636,
  which is below any retention period, so it is never purged.
- **Business impact:** authorizations written late in a year are never purged, and their credit is
  never released. The summary counters keep them forever.
- **Technical impact:** one subtraction. Correcting it would purge a backlog of records the source
  has been keeping, which changes the summary counters of every affected account.
- **Modernisation risk:** high in both directions — correcting it silently purges historical data;
  keeping it carries the defect forward.
- **Local handling:** carried forward **unchanged** (defect DEF-AUTH-04):
  `ExpiredAuthorizationProcessor.isExpired` recovers `99999 - auth_date_9c` and subtracts it from
  today's Julian `YYDDD` exactly as paragraph 4000 does, so the same records expire and the same
  records do not. The correct behaviour is obvious but the *consequence* of correcting it (a one-off
  bulk purge) is a business decision, so the playbook §2.4(3) test is not met.
  `ExpiredAuthorizationProcessorTest` pins the AS-IS behaviour.
- **Manual review:** required — the business must approve the correction and the one-off purge of the
  backlog it exposes before the arithmetic is changed.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-26 — Duplicated condition guarding the summary delete

- **Source artifact:** `app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl`
- **Source location:** `MAIN-PARA` —
  `IF PA-APPROVED-AUTH-CNT <= 0 AND PA-APPROVED-AUTH-CNT <= 0`
- **Construct:** the approved counter is tested twice. The declined counter was evidently intended,
  which is how every other paired statement in the program is written.
- **Business impact:** an account whose approved authorizations have all been purged loses its
  pending authorization summary even when declined authorizations are still recorded beneath it, so
  the declined history and its totals disappear.
- **Technical impact:** one boolean expression.
- **Modernisation risk:** high if "corrected" silently — the corrected condition deletes strictly
  fewer summaries, which changes what CPVS shows.
- **Local handling:** carried forward **unchanged** in `ExpiredAuthorizationProcessor`
  (`boolean deleteSummary = approvedCnt <= 0 && approvedCnt <= 0;`, with the source citation in the
  comment) because the intended condition cannot be corroborated from the source. Asserted by
  `ExpiredAuthorizationProcessorTest` so no future change silently corrects it.
- **Manual review:** required — the business must choose the intended condition.
- **Confidence:** High (the defect), Medium (the intent). **Status:** Open, REVIEW REQUIRED.

---

### UCR-27 — Purge computes the available-credit adjustment and discards it

- **Source artifact:** `app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl`
- **Source location:** `4000-CHECK-IF-EXPIRED` (the four `SUBTRACT`s) and the absence of any
  `REPL SEGMENT(PAUTSUM0)` anywhere in the program
- **Construct:** purging an authorization reduces the account's approved/declined counters and
  totals in the working copy of the summary segment, but the segment is never rewritten. Only the
  deletes survive. The job's stated purpose — releasing available credit — is therefore not
  achieved, except in the case where the summary itself is deleted.
- **Business impact:** material. Approved authorization totals and the credit balance keep rising
  and available credit (`COPAUA0C` BR-15) keeps falling as authorizations age out, until the summary
  happens to be deleted.
- **Technical impact:** the target computes the same adjustment; whether it writes it back is a
  configuration switch.
- **Modernisation risk:** high in both directions — writing it back changes subsequent authorization
  decisions; not writing it back carries a material defect forward.
- **Local handling:** the adjustment is computed by `ExpiredAuthorizationProcessor` and written back
  by `AuthPurgeWriter` when `carddemo.batch.authpurge.persist-summary-adjustments` is true, which is
  the **default**. This is a deliberate §8.9.5 remediation, corroborated by a second artifact: the
  module `README.md` states the job's batch function as "Adjustment of available credit when
  unmatched authorizations are deleted", which is exactly the write the program omits, and
  `CBPAUP0C` computes the four reversals with no other use for them. Setting the property to false
  reproduces the AS-IS behaviour for comparison runs.
  `AuthPurgeWriterTest.theAsIsModeDiscardsTheCounterReversalsTheSourceNeverRewrote` and
  `.aRootThatKeepsChildrenHasItsCountersRewrittenInTheRemediatedMode` pin both modes, and the
  before/after behaviour is in `10-testing-and-parity.md`.
- **Manual review:** required — this is the decision with the largest business impact in the
  authorization closure.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-28 — Load utility skips and swallows bad records silently

- **Source artifact:** `app/app-authorization-ims-db2-mq/cbl/PAUDBLOD.CBL`
- **Source location:** `3000-READ-CHILD-SEG-FILE` — `IF ROOT-SEG-KEY IS NUMERIC` with no `ELSE`;
  the `ELSE` of the file-status check, which displays a message and loops on
- **Construct:** an authorization record whose account id is not numeric is not loaded and nothing is
  reported. A read failure other than end-of-file is displayed once and the loop continues, which on
  a permanent error is an endless loop and on a transient one silently drops records.
- **Business impact:** silent data loss during a load, with no reconciliation in the program.
- **Technical impact:** the target migration pipeline cannot be allowed to do this.
- **Modernisation risk:** medium.
- **Local handling:** the migration pipeline fails the step on any unreadable record and reconciles
  loaded row counts against the extract, per `05-data-mapping.md`; skipped records are reported, not
  swallowed. A deliberate delta on the failure path only.
- **Manual review:** required if a historical load is to be re-run and reconciled.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-29 — The HIDAM index database has no target counterpart

- **Source artifact:** `app/app-authorization-ims-db2-mq/ims/DBPAUTX0.dbd`, segment `PAUTINDX`
- **Source location:** `DBD NAME=DBPAUTX0,ACCESS=(INDEX,VSAM,PROT)`, `FIELD NAME=(INDXSEQ,SEQ,U)`,
  `LCHILD NAME=(PAUTSUM0,DBPAUTP0),INDEX=ACCNTID`, and the matching `LCHILD NAME=(PAUTINDX,DBPAUTX0)`
  in `DBPAUTP0.dbd`
- **Construct:** a separate physical index database is what makes `DBPAUTP0` a HIDAM database — it is
  how DL/I resolves `GU SEGMENT(PAUTSUM0) WHERE (ACCNTID = …)` and how it establishes the root
  sequence that `CBPAUP0C` walks with `GN`. It is infrastructure, not a business access path: no
  program names it, and it indexes the same account id that is already the root key.
- **Business impact:** none. Both behaviours it supports — keyed root retrieval and ascending root
  order — are preserved.
- **Technical impact:** PostgreSQL maintains the primary-key b-tree of `pending_auth_summary`
  automatically, so there is no artifact to generate. One source file therefore has no target file,
  which would otherwise look like a coverage gap.
- **Modernisation risk:** low.
- **Local handling:** recorded as *Replaced* in `13-artifact-disposition.md`; the keyed read is
  `AuthorizationSummaryRepository.findByIdForUpdate` and the ordered walk is `findNextRoots`.
- **Manual review:** not required.
- **Confidence:** High. **Status:** Closed — accepted.

Note: an earlier revision of this register described `DBPAUTX0` as a *secondary index over the card
number of `PAUTDTL1`*. That was a misreading of the DBD; the correction above is what the source
says. The target keeps `ix_pending_auth_detail_card` on `pending_auth_detail (card_num, auth_key)`
all the same, because the fraud table is keyed by card number and timestamp and the reconciliation
queries of `05-data-mapping.md` join on it — it is a target-side index with no source counterpart,
not a converted one.

---

### UCR-30 — Declined authorization total accrues the previous message's amount

- **Source artifact:** `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl`
- **Source location:** `8400-UPDATE-SUMMARY` —
  `ADD PA-TRANSACTION-AMT TO PA-DECLINED-AUTH-AMT`, where `PA-TRANSACTION-AMT` belongs to the
  `PENDING-AUTH-DETAILS` segment area that `8500-INSERT-AUTH` loads *afterwards*
- **Construct:** the declined total is increased by the detail-area amount, which at that point still
  holds whatever the previously processed message left there (zero on the first message of a run).
  The approved branch three lines above correctly uses the working amount `WS-APPROVED-AMT`.
- **Business impact:** the declined authorization total on CPVS is wrong — it is the sum of the
  *preceding* declined requests' amounts, shifted by one.
- **Technical impact:** in the target the two amounts are distinct values, so the defect cannot be
  reproduced by accident; it would have to be implemented deliberately.
- **Modernisation risk:** medium.
- **Local handling:** **remediated** with the current request's amount, corroborated by
  `CBPAUP0C` BR-14, which subtracts exactly that field when purging a declined authorization — the
  total can only be consistent if it is the sum of the declined authorizations' own amounts. The
  reasoning is in the code comment in `AuthorizationRequestProcessor.updateSummary`, the
  before/after behaviour is in `10-testing-and-parity.md`, and
  `AuthorizationRequestProcessorIntegrationTest` pins the corrected total.
- **Manual review:** required — confirm the remediation before cutover, since historical summary
  totals migrated from IMS carry the old defect.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED (remediated in the target, sign-off outstanding).

---

### UCR-31 — A root segment in the supplied unload has a blank account key

- **Source artifact:** `app/app-authorization-ims-db2-mq/data/EBCDIC/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat`
- **Source location:** the last `PAUTSUM0` record of the unload, at byte offset 51508; its
  `PA-ACCT-ID` field holds `X'404040404040'` (six EBCDIC blanks)
- **Construct:** `PA-ACCT-ID` is `PIC S9(11) COMP-3`, so its last nibble must be a sign and the rest
  must be digits. Blanks are neither, so the record carries no decodable account id and cannot be
  attached to the hierarchy. It has no children.
- **Business impact:** one authorization summary of the supplied data set is not migrated. No
  authorization detail is lost, and no account known to `carddemo` loses its summary.
- **Technical impact:** a migration that normalised the key would invent an account id and, because
  the root key is the primary key, would either collide with a real account or create an orphan
  summary that no screen can reach.
- **Modernisation risk:** low for the supplied data set, medium if the same defect exists in
  production volumes.
- **Local handling:** `tools/ims_authorization_unload.py` rejects the record, reports it as an
  anomaly, and `tools/generate_authorization_seed_sql.py` records it as a comment in
  `V2__load_authorization_sample_data.sql` instead of seeding it. The reconciliation report counts
  21 roots, not 22, so the rejection is visible rather than silent — the opposite of the source
  loader's behaviour (UCR-28).
- **Manual review:** required — the data owner must decide whether the record is scratch data or a
  real summary whose key was lost, before a production load.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-32 — Summary fields declared but never populated, and LOW-VALUES in character fields

- **Source artifacts:** `app/app-authorization-ims-db2-mq/cpy/CIPAUSMY.cpy`, the same module's
  `cbl/COPAUA0C.cbl`, `cbl/COPAUS0C.cbl`, `cbl/CBPAUP0C.cbl`, and the supplied unload image
- **Source location:** `PA-AUTH-STATUS PIC X(1)` and `PA-ACCOUNT-STATUS PIC X(2) OCCURS 5 TIMES`;
  no in-scope program moves a value into either field, and no program tests them
- **Construct:** two summary fields are part of the segment layout but are dead in every supplied
  program. In the unload image they hold LOW-VALUES (`X'00'`) for most accounts and blanks or `'00'`
  fragments for others, which is the signature of a field written only by initialisation.
- **Business impact:** unknown. The field names suggest an authorization hold status and a
  five-occurrence account status history that some program outside the supplied scope may maintain.
  Nothing in scope depends on them.
- **Technical impact:** `X'00'` is not a storable code point in a PostgreSQL character column, so the
  values cannot be carried across byte for byte.
- **Modernisation risk:** low in scope; medium if an out-of-scope producer exists.
- **Local handling:** both columns are kept in `pending_auth_summary` and on
  `AuthorizationSummaryEntity`, so the layout is not narrowed, and the decoder converts LOW-VALUES to
  blanks — a documented encoding rule in `05-data-mapping.md` §9, applied in exactly one place
  (`unpack_text`) and therefore applied identically by the seed generator and the reconciliation
  report. No target code reads either column, matching the source.
- **Manual review:** required — confirm no out-of-scope program populates these fields before the
  blank conversion is applied to production data.
- **Confidence:** High (in-scope usage), Low (out-of-scope producers). **Status:** Open, REVIEW REQUIRED.

---

### UCR-33 — `CBTRN03C`'s closing totals count the last transaction twice

- **Source artifact:** `app/cbl/CBTRN03C.cbl`
- **Source location:** the end-of-file branch of the main read loop, which performs the page-total,
  account-total and grand-total `ADD TRAN-AMT` a second time although no record was read
- **Construct:** the totals are accumulated from the record area, and the record area still holds
  the last record when end of file is reached, so the last amount is added twice.
- **Business impact:** the closing page total, the last account total and the grand total of every
  printed report are overstated by the last transaction's amount, so a report's totals do not equal
  the sum of its own printed detail lines.
- **Technical impact:** reproducing the defect would mean printing arithmetic the converted system
  can be shown to disagree with.
- **Modernisation risk:** medium — archived reports carry the defect.
- **Local handling:** **remediated.** `TransactionReportWriter` accumulates only on a record that
  was read, so its totals equal the printed detail lines. `TransactionReportWriterTest` pins the
  corrected totals, and the before/after behaviour is in `10-testing-and-parity.md`.
- **Manual review:** required — a converted report will not tie to an archived mainframe report.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED (remediated in the target).

---

### UCR-34 — `CBSTM03A` drops the last card's transactions and caps its table at 51 × 10

- **Source artifact:** `app/cbl/CBSTM03A.CBL`
- **Source location:** `8500-READTRNX-READ` stores `TR-CNT` into `WS-TRCT (CR-CNT)` only when the
  card number changes; `WS-TRNX-TABLE` is `OCCURS 51` by `OCCURS 10` with no overflow check
- **Construct:** the transaction count of the final card group is never stored, and neither the
  card nor the transaction subscript is bounded.
- **Business impact:** the last cardholder on the transaction file receives a statement with no
  transactions and a zero total; a cardholder with more than ten transactions loses the rest, and a
  52nd card writes outside the table.
- **Technical impact:** the target has no table: a card's transactions are a query.
- **Modernisation risk:** medium.
- **Local handling:** **remediated.** `StatementRenderer` reads the card's transactions from the
  transaction store, so every cardholder is complete and no cap applies. `StatementRendererTest`
  and `TransactionBatchIntegrationTest` cover it.
- **Manual review:** required — converted statements are longer than mainframe ones for cardholders
  with more than ten transactions.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED (remediated in the target).

---

### UCR-35 — `CBIMPORT` declares validation complete without validating anything

- **Source artifact:** `app/cbl/CBIMPORT.cbl`
- **Source location:** `3000-VALIDATE-IMPORT`, whose body is two `DISPLAY` statements
- **Construct:** a named validation step that examines no data and always reports success.
- **Business impact:** an import is reported as validated whatever it contains.
- **Technical impact:** none; there is nothing to convert.
- **Modernisation risk:** low technically, medium for data quality.
- **Local handling:** preserved AS-IS — `MigrationImportJobConfig` logs the same two messages and
  validates nothing, because inventing validation rules would reject records the mainframe accepted.
- **Manual review:** required — supply the intended validation rules.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-36 — Personal data and card security codes written in clear

- **Source artifacts:** `app/cbl/CBEXPORT.cbl`, `app/cpy/CVEXPORT.cpy`, `app/cbl/CBACT02C.cbl`,
  `app/cbl/CBCUS01C.cbl`
- **Source location:** the five `…-CREATE-…-EXP-REC` paragraphs of `CBEXPORT`, and the
  `DISPLAY CARD-RECORD` / `DISPLAY CUSTOMER-RECORD` statements of the print programs
- **Construct:** the export record carries social security number, government-issued id, date of
  birth and the card security code as plain fields, and the print programs write whole card and
  customer record images to the job log.
- **Business impact:** personal data and card security codes leave the system in files and logs.
- **Technical impact:** encrypting the export file or masking the log would break a receiving
  branch's existing programmes and the print programs' stated purpose.
- **Modernisation risk:** high for compliance.
- **Local handling:** preserved AS-IS so that an export remains readable by the receiving branch;
  the print jobs log the same record images. The exposure is stated in
  `BRD-CBEXPORT-CBIMPORT-COBSWAIT.md` and `BRD-CBACT02C-CBACT03C-CBCUS01C.md`.
- **Manual review:** required — decide encryption or masking, and whether the print jobs may run
  outside a controlled environment, before production use.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-37 — `CBEXPORT` hard-codes the exporting branch and region

- **Source artifact:** `app/cbl/CBEXPORT.cbl`
- **Source location:** `MOVE '0001' TO EXPORT-BRANCH-ID` and `MOVE 'NORTH' TO EXPORT-REGION-CODE`
  in each of the five create paragraphs
- **Construct:** the identity of the exporting branch is a literal, not a parameter.
- **Business impact:** a second branch cannot use the programme unchanged; every export claims to
  come from branch `0001` in region `NORTH`.
- **Technical impact:** none; both are named constants in `MigrationExportHeader`.
- **Modernisation risk:** medium.
- **Local handling:** preserved AS-IS as named constants, ready to become configuration once the
  business confirms the intended source of the values.
- **Manual review:** required.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-38 — `CBACT04C`'s fee calculation is an empty paragraph

- **Source artifact:** `app/cbl/CBACT04C.cbl`
- **Source location:** `1400-COMPUTE-FEES`, which contains no statement that changes any balance
- **Construct:** the interest run is documented as calculating interest *and* fees, but the fee step
  is empty.
- **Business impact:** no fee is ever charged by the run.
- **Technical impact:** none; there is nothing to convert.
- **Modernisation risk:** medium — a fee schedule may be expected.
- **Local handling:** preserved AS-IS: `InterestAccrualWriter` calculates no fee.
- **Manual review:** required — supply the fee rules if fees are expected.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-39 — Truncated interest, and a posting that can leave the category balance ahead of the account

- **Source artifacts:** `app/cbl/CBACT04C.cbl`, `app/cbl/CBTRN02C.cbl`
- **Source location:** `COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200` with no
  `ROUNDED` phrase; and the account-rewrite failure path of `2000-POST-TRANSACTION`, which sets
  reason 109 after the category balance has already been updated
- **Construct:** an unrounded `COMPUTE` truncates towards zero, and the posting has no unit of
  recovery spanning the category balance and the account.
- **Business impact:** fractions of a cent are lost on every accrual; a failed account rewrite
  leaves the category balance increased and the account unchanged.
- **Technical impact:** the target divides with `RoundingMode.DOWN` to keep the truncation, and its
  chunk transaction plus the compensated account adjustment make the posting all-or-nothing.
- **Modernisation risk:** low for the truncation, medium for the partial posting.
- **Local handling:** truncation preserved AS-IS (`InterestAccrualWriter`); the partial posting is
  **not reproducible** in the target and the difference is recorded as CMD-18.
- **Manual review:** required — confirm the truncation is intended.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

### UCR-40 — MVS control-block navigation and `ALTER … TO PROCEED TO`

- **Source artifacts:** `app/cbl/CBSTM03A.CBL`, `app/cbl/CBSTM03B.CBL`
- **Source location:** the `SET ADDRESS OF PSA-BLOCK` / `TCB-BLOCK` / `TIOT-BLOCK` prologue and its
  loop over the allocated DD names; `ALTER 8100-FILE-OPEN TO PROCEED TO …` and the `GO TO` dispatch
  of `CBSTM03B`'s `0000-START`
- **Construct:** the statement job reads MVS control blocks to report the job, step and allocated
  data-set names, and parameterises one open routine over four files by altering a `GO TO`.
- **Business impact:** none — operator diagnostics and an internal dispatch mechanism.
- **Technical impact:** neither construct exists off the mainframe.
- **Modernisation risk:** low.
- **Local handling:** the job and step are identified from the Spring Batch execution context in the
  log, and the altered `GO TO` disappears because each file is its own repository or client call —
  the consolidation recorded for `CBSTM03B` in `13-artifact-disposition.md`.
- **Manual review:** not required.
- **Confidence:** High. **Status:** Closed.

---

### UCR-41 — Sample customer data the account edits refuse

- **Source artifact:** `app/data/EBCDIC/custdata.txt` (loaded by `V2__load_sample_data.sql`)
- **Source location:** customer 11 and others: `FICO` `209`, telephone area codes `002` and `553`,
  ZIP `24984` against state `WA`
- **Construct:** the shipped data does not satisfy the edits `COACTUPC` applies to the same fields —
  a FICO score between 300 and 850, an area code whose first digit is not `0` or `1` and whose third
  digit is not `1`, and a ZIP that belongs to the state.
- **Business impact:** such an account cannot be rewritten from the screen until fields unrelated to
  the change are corrected, which is what a browser run of the update hits first.
- **Technical impact:** none — the edits and the data are both carried over unchanged.
- **Modernisation risk:** low; it is a fixture problem, not a behaviour one.
- **Local handling:** both preserved AS-IS, so the converted screen refuses exactly the rows the
  mainframe screen refused; the browser evidence corrects the offending fields on screen first
  (`evidence/ui-runtime-test-report.md`).
- **Manual review:** required — decide whether the sample file or the edits are authoritative.
- **Confidence:** High. **Status:** Open, REVIEW REQUIRED.

---

## Summary

| Status | IDs |
|---|---|
| Open, REVIEW REQUIRED | UCR-01, UCR-02, UCR-04, UCR-05, UCR-06, UCR-07, UCR-09, UCR-10, UCR-11 (the user-type omissions only), UCR-12, UCR-14, UCR-16, UCR-17, UCR-18, UCR-19, UCR-21, UCR-22, UCR-23, UCR-25, UCR-26, UCR-27, UCR-28, UCR-30, UCR-31, UCR-32, UCR-41 |
| Closed — accepted | UCR-03, UCR-08, UCR-11 (the account state machines), UCR-13, UCR-15, UCR-20, UCR-24, UCR-29 |

The three entries added for the identity closure are UCR-14 (clear-text credentials and user-existence
disclosure), UCR-15 (unchecked outcomes) and UCR-16 (message defects); UCR-07, UCR-11 and UCR-13 were
extended with their sign-on and user-administration occurrences.

The sixteen entries added for the authorization closure are UCR-17 … UCR-32.
UCR-31 and UCR-32 are defects in the supplied authorization *data* rather than in a program.
 The three with the
largest business impact are UCR-18 (card status, account status and fraud screening never decline an
authorization in the source), UCR-27 (the purge computes the available-credit adjustment and
discards it) and UCR-22 (every approval clears the pending cash balance). UCR-07 (hard-coded
literals) is extended by the authorization literals listed in `BRE-Report-CARDDEMO-AUTHORIZATION.md`
§4.

The eight entries added for the transactions, cards, statements and branch-migration closure are
UCR-33 … UCR-40. The three with the largest business impact are UCR-33 and UCR-34 (report totals and
statements that are wrong in the source and corrected in the target) and UCR-36 (personal data and
card security codes written in clear). UCR-40 is a closed platform-construct entry, and UCR-07
(hard-coded literals) is extended by the bill-payment and interest classifications listed in
`BRE-Report-CARDDEMO.md` §5.

UCR-41 was added from the browser run of the converted screens: like UCR-31 and UCR-32 it is a
defect in the supplied *data* rather than in a program.
