# Implementation Traceability Matrix

One row per extracted business rule: ticket, rule, source location, target implementation, React
component, API operation, PostgreSQL object, batch job/step, validation test, expected and actual
result, status.

Conventions:
- **Expected result** is derived from the source, never from the target code.
- **Actual** is `as expected` only where the cited test executed and passed in
  `evidence/backend-build-test.log` (634 tests, 0 failures) or `evidence/frontend-test.log`
  (21 files, 103 tests, 0 failures).
- **R** in the status column marks REVIEW REQUIRED, with the register reference.
- Package prefix `com.carddemo` is omitted. `AVS` = `service.AccountViewService`,
  `ARS` = `service.AccountReadService`, `AUS` = `service.AccountUpdateService`,
  `AUV` = `service.AccountUpdateValidator`, `ACD` = `service.AccountChangeDetector`,
  `ASM` = `service.AccountScreenMapper`, `FE` = `domain.validation.FieldEditor`,
  `DE` = `domain.validation.DateEditor`, `DVS` = `domain.validation.DateValidationService`,
  `MAS` = `service.MenuAccessService`.
- API operations: `viewAccount` = `GET /api/accounts/{id}`, `promptAccountView` =
  `GET /api/accounts/view`, `fetch` = `GET /api/accounts/{id}/update`, `validate` =
  `POST …/update/validate`, `confirm` = `POST …/update/confirm`, `menu` = `GET /api/menu`.

---

# Part A — Account Management and User Administration

## 1. COACTVWC — Account View (transaction CAVW)

React component: `AccountViewScreen.tsx`. PostgreSQL objects: `card_xref`, `ix_card_xref_acct_id`,
`account`, `customer` (read-only). Batch: n/a.

| Ticket | Rule | Source location | Target | API | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-14 | BR-01 | `0000-MAIN` (`EIBCALEN = 0`) | `AVS.prompt` | `promptAccountView` | `AccountViewServiceTest.theFirstTurnOnlyPrompts` | first turn holds no account and only prompts | as expected | Done |
| CDM-18 | BR-02 | `0000-MAIN` `EVALUATE EIBAID` | the operation set (view + navigation only); no other action exists | `viewAccount` | `AccountApiIntegrationTest.theViewTransactionStartsWithThePrompt` | only display and exit are honoured | as expected | Done |
| CDM-20 | BR-03 | `COMMON-RETURN` | `AccountViewScreen` back action returning to `MenuScreen` | n/a | `AccountViewScreen.test.tsx` | exit returns to the caller, else the menu | as expected | Done |
| CDM-17 | BR-04 | `COMMON-RETURN` (`CDEMO-USRTYP-USER`) | `MAS` default of regular on exit | `menu` | `MenuAccessServiceTest.anUnknownUserTypeDefaultsToRegular` | exit leaves the operator regular | as expected | Done |
| CDM-09 | BR-05 | `0000-MAIN` (unknown state → abend `0001`) | unreachable in the target: state is a total enum, not flag combinations | n/a | — | an unrecognised state fails the transaction | not reachable | Done, UCR-11 |
| CDM-15 | BR-06 | `2210-EDIT-ACCOUNT` | `cobol.CobolText.normaliseAsterisk`, `isBlank` | `viewAccount` | `CobolTextTest.asteriskMeansNothingSupplied`, `blankCoversLowValuesAndSpaces` | `*` and blanks mean nothing entered | as expected | Done |
| CDM-14 | BR-07 | `2210-EDIT-ACCOUNT` | `ARS.editAccountId` blank branch | `viewAccount` | `AccountReadServiceTest.anAbsentAccountIdIsItsOwnError`, `AccountViewServiceTest.aBlankAccountIdIsRejectedBeforeAnyRead` | prompt is issued and the remembered account cleared | as expected | Done |
| CDM-14 | BR-08 | `2200-EDIT-MAP-INPUTS` | `ScreenValidationException` code `ACCOUNT_ID_BLANK` | `viewAccount` | `AccountReadServiceTest.anAbsentAccountIdIsItsOwnError` | blank is "no search criteria", not invalid | as expected | Done |
| CDM-14 | BR-09 | `2210-EDIT-ACCOUNT` | `ARS.editAccountId` length/numeric/zero checks | `viewAccount` | `AccountReadServiceTest.aNonNumericShortOrZeroAccountIdIsRejected`, `eachProgramUsesItsOwnMessage`; `AccountApiIntegrationTest.aNonNumericAccountIdIsAValidationError` | exactly 11 digits, non-zero, else `Account Filter must  be a non-zero 11 digit number` | as expected | Done |
| CDM-14 | BR-10 | `9200`→`9300`→`9400` | `ARS.readAccount` | `viewAccount` | `AccountReadServiceTest.theReadSequenceIsCrossReferenceThenAccountThenCustomer` | cross-reference, then account, then customer | as expected | Done |
| CDM-14 | BR-11 | `9000-READ-ACCT` | early `orElseThrow` at each step | `viewAccount` | `AccountReadServiceTest.aMissingCrossReferenceStopsBeforeTheAccountRead`, `aMissingAccountStopsBeforeTheCustomerRead` | later reads are skipped after a failure | as expected | Done |
| CDM-14 | BR-12 | `9200` `DFHRESP(NOTFND)` | `ScreenMessages.accountNotInCrossReference` | `viewAccount` | `AccountApiIntegrationTest.anUnknownAccountIsNotFound` | `Account:<id> not found in Cross ref file.` | as expected | Done |
| CDM-14 | BR-13 | `9300` `DFHRESP(NOTFND)` | `ScreenMessages.accountNotInMaster` | `viewAccount` | `AccountReadServiceTest.aMissingAccountStopsBeforeTheCustomerRead` | account-master not-found wording | as expected | Done |
| CDM-14 | BR-14 | `9400` `DFHRESP(NOTFND)` | `ScreenMessages.customerNotInMaster` | `viewAccount` | `AccountReadServiceTest.aMissingCustomerIsReportedWithTheCustomerIdFromTheCrossReference` | `CustId:<id> not found in …`, id taken from the cross-reference | as expected | Done |
| CDM-14 | BR-15 | `9200`/`9300`/`9400` `WHEN OTHER` | `StorageAccessException` → HTTP 500 with the file-error text | all | `AccountApiIntegrationTest` error mapping | any other status is fatal, naming the file | as expected | Done |
| CDM-20 | BR-16 | `1200-SETUP-SCREEN-VARS` | `ASM.toDetails`; the read chain leaves earlier values in place | `viewAccount` | `AccountScreenMapperTest.detailsKeepTheFixedWidthKeysAndTheEditedAmounts` | account fields shown once found; stale values retained on a later failure | as expected (AS-IS) | Done, **R** UCR-10.2 |
| CDM-20 | BR-17 | `1200-SETUP-SCREEN-VARS` | `ASM.toDetails` customer block | `viewAccount` | `AccountScreenMapperTest.missingStoredValuesProduceBlankMapFields` | customer fields only when found | as expected | Done |
| CDM-11 | BR-18 | `1200-SETUP-SCREEN-VARS` `STRING` | `ASM` SSN grouping | `viewAccount` | `AccountScreenMapperTest.aLeadingZeroSsnKeepsAllNineDigits` | `nnn-nn-nnnn`, leading zeros kept | as expected | Done |
| CDM-14 | BR-19 | `1200-SETUP-SCREEN-VARS` | `ScreenMessages.PROMPT_FOR_ACCT` | `promptAccountView` | `AccountViewServiceTest.aKnownAccountIsDisplayedWithTheOutputMessage` | `Enter or update id of account to display` | as expected | Done |
| CDM-21 | BR-20 | `1300-SETUP-SCREEN-ATTRS` | `fieldFlags` `NOT_OK`/`BLANK`; blank redisplays as `*` | `viewAccount` | `AccountViewScreen.test.tsx`, `AccountReadServiceTest` flags | rejected id highlighted; blank shown as asterisk | as expected | Done |
| CDM-20 | BR-21 | `1300-SETUP-SCREEN-ATTRS` (duplicate `WHEN`) | autofocus on the account-id input | n/a | `AccountViewScreen.test.tsx` | the cursor is on the account number | as expected; the duplicate branch collapses | Done, UCR-10.1 |
| CDM-14 | BR-22 | no `WRITE`/`REWRITE`/`DELETE` in the program | `@Transactional(readOnly = true)`; the view path has no write call at all | `viewAccount` | `AccountViewServiceTest.aKnownAccountIsDisplayedWithTheOutputMessage` (mocked repositories, no save interaction) | the enquiry never writes | as expected | Done |
| CDM-18 | BR-23 | `ABEND-ROUTINE` (`9999`) | `ApiExceptionHandler` → HTTP 500 | all | `AccountApiIntegrationTest.aMalformedBodyIsARequestError` (envelope), `evidence/api-smoke.log` | an unhandled failure ends the interaction, nothing committed | as expected (no abend code) | Done, UCR-08 |

## 2. COACTUPC — Account Update (transaction CAUP)

React component: `AccountUpdateScreen.tsx`. PostgreSQL objects: `card_xref`, `account`, `customer`
(read/write). Batch: n/a.

| Ticket | Rule | Source location | Target | API | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-16 | BR-01 | `0000-MAIN`, `ACUP-DETAILS-NOT-FETCHED` | `AUS.fetch` | `fetch` | `AccountUpdateServiceTest.fetchReturnsTheStoredValuesInBothBlocks` | a new update starts with nothing fetched | as expected | Done |
| CDM-16 | BR-02 | `2000-DECIDE-ACTION` | the three operations plus `AccountUpdateStatus` | `fetch`/`validate`/`confirm` | `AccountApiIntegrationTest.theUpdateTransactionFetchesValidatesAndCommits` | save only after validation; display/exit always | as expected | Done |
| CDM-21 | BR-03 | `COMMON-RETURN` | `AccountUpdateScreen` back action | n/a | `AccountUpdateScreen.test.tsx` | exit returns to caller, else menu | as expected | Done |
| CDM-16 | BR-04 | `0000-MAIN` (`ACUP-CHANGES-OKAYED-AND-DONE`) | `confirm` re-reads and re-validates; a committed state cannot be replayed | `confirm` | `AccountUpdateServiceTest.confirmReportsAnUnchangedSubmission` | a completed update is not repeatable | as expected | Done |
| CDM-09 | BR-05 | `0000-MAIN` `WHEN OTHER` | unreachable: total status enum | n/a | — | unrecognised state fails | not reachable | Done, UCR-11 |
| CDM-15 | BR-06 | `1200-EDIT-MAP-INPUTS` first branch | `AUS.fetch` validates only the key | `fetch` | `AccountUpdateServiceTest.fetchReturnsTheStoredValuesInBothBlocks` | before fetch only the key is checked | as expected | Done |
| CDM-15 | BR-07 | `1200-EDIT-MAP-INPUTS` (`FLG-ACCTFILTER-BLANK`) | `ScreenValidationException` `ACCOUNT_ID_BLANK` | `fetch` | `AccountReadServiceTest.anAbsentAccountIdIsItsOwnError` | blank key = no search criteria | as expected | Done |
| CDM-16 | BR-08 | `2000-DECIDE-ACTION` (F12) | `fetch` re-reads the stored values | `fetch` | `AccountApiIntegrationTest.theUpdateTransactionFetchesValidatesAndCommits` (re-fetch) | cancel discards keyed changes | as expected | Done |
| CDM-16 | BR-09 | `2000-DECIDE-ACTION` → `9600` | `confirm` re-runs the edits and the change check before writing | `confirm` | `AccountUpdateServiceTest.confirmRerunsTheEditsSoAnInvalidSubmissionIsNeverWritten` | writes only from the validated state | as expected | Done |
| CDM-16 | BR-10 | `1200`/`3250-SETUP-INFOMSG` | `CHANGES_VALIDATED` + `ScreenMessages.CHANGES_VALIDATED` | `validate` | `AccountUpdateServiceTest.validatedChangesAskForConfirmation` | `Changes validated.Press F5 to save`, nothing written | as expected | Done |
| CDM-15 | BR-11 | `1210-EDIT-ACCOUNT` | `ARS.editAccountId` blank branch | `fetch` | `AccountReadServiceTest.anAbsentAccountIdIsItsOwnError` | prompt, remembered account cleared | as expected | Done |
| CDM-15 | BR-12 | `1210-EDIT-ACCOUNT` | `ARS.editAccountId(keyed, true)` | `fetch` | `AccountReadServiceTest.eachProgramUsesItsOwnMessage` | update-program wording, 11 digits, non-zero | as expected | Done |
| CDM-16 | BR-13 | `1205-COMPARE-OLD-NEW` | `ACD.hasChanges` → `NO_CHANGES` | `validate` | `AccountChangeDetectorTest.anIdenticalSubmissionHasNoChanges`, `AccountApiIntegrationTest.anUnchangedSubmissionIsReportedRatherThanRewritten` | `No change detected with respect to values fetched.` | as expected | Done |
| CDM-16 | BR-14 | `1205-COMPARE-OLD-NEW` | `ACD` case/pad-insensitive comparison for status and group | `validate` | `AccountChangeDetectorTest.caseOnlyDifferencesInTheUpperCasedFieldsAreNotChanges` | status/group compared ignoring case and padding | as expected | Done |
| CDM-16 | BR-15 | `1205-COMPARE-OLD-NEW` | same, for the customer text fields | `validate` | `AccountChangeDetectorTest.aChangedCustomerFieldIsDetected` | names/address/state/country/gov-id ignore case | as expected | Done |
| CDM-16 | BR-16 | `1205-COMPARE-OLD-NEW` | exact keyed-text comparison for numerics | `validate` | `AccountChangeDetectorTest.amountsAreComparedAsKeyedText`, `aChangedAccountFieldIsDetected` | amounts, dates, phones, ids compared exactly | as expected | Done |
| CDM-16 | BR-17 | `1200-EDIT-MAP-INPUTS` guard | `validate` short-circuits on an already-validated identical submission | `validate` | `AccountUpdateServiceTest.anUnchangedSubmissionNeverReachesTheEdits` | validated submissions are not re-edited | as expected | Done |
| CDM-15 | BR-18 | `1100-RECEIVE-MAP` | `CobolText.normaliseAsterisk` applied per field | `validate` | `AccountUpdateValidatorTest.aSingleAsteriskBlanksTheFieldOut` | an all-asterisk field is treated as empty | as expected | Done |
| CDM-15 | BR-19 | `1215-EDIT-MANDATORY` | `FE.editMandatory` | `validate` | `FieldEditorTest.mandatoryFieldRejectsBlank`, `mandatoryFieldAcceptsAValue` | `<field> must be supplied.` | as expected | Done |
| CDM-15 | BR-20 | `1220-EDIT-YESNO` | `FE.editYesNo` | `validate` | `FieldEditorTest.yesNoAcceptsOnlyYAndN`, `yesNoTreatsZeroesAsNotSupplied` | `<field> must be Y or N.` | as expected | Done |
| CDM-15 | BR-21 | `1225-EDIT-ALPHA-REQD` | `FE.editAlphaRequired` | `validate` | `FieldEditorTest.alphaRequiredRejectsBlankAndDigits` | letters and spaces only, required | as expected | Done |
| CDM-15 | BR-22 | `1235-EDIT-ALPHA-OPT` | `FE.editAlphaOptional` | `validate` | `FieldEditorTest.alphaOptionalAcceptsBlankButNotDigits` | optional, letters and spaces only | as expected | Done |
| CDM-15 | BR-23 | `1230-EDIT-ALPHANUM-REQD`, `1240-EDIT-ALPHANUM-OPT` | `FE.editAlphanumRequired`, `editAlphanumOptional` — implemented, unreached, as in the source | n/a | `FieldEditorTest.alphanumericEdits` | letters, digits, spaces only | as expected; no path reaches it | Done, **R** UCR-05.2 |
| CDM-15 | BR-24 | `1245-EDIT-NUM-REQD` | `FE.editNumericRequired` | `validate` | `FieldEditorTest.numericRequiredRejectsBlankNonNumericAndZero` | supplied, all digits, non-zero | as expected | Done |
| CDM-15 | BR-25 | `1250-EDIT-SIGNED-9V2` | `FE.editSignedAmount`, `cobol.CobolNumeric` | `validate` | `FieldEditorTest.signedAmountReturnsTheConvertedValue`, `signedAmountRejectsBlankAndInvalidText`, `CobolNumericTest` (7 cases) | sign kept, exactly two decimals, 10 integer digits max | as expected | Done |
| CDM-15 | BR-26 | `1260-EDIT-US-PHONE-NUM` | `FE.editUsPhoneNumber` | `validate` | `FieldEditorTest.phoneNumberIsOptionalWhenAreaCodeAndPrefixAreBlank`, `phoneNumberValidatesEachPart` | omit entirely, or supply all three parts | as expected | Done |
| CDM-15 | BR-27 | `EDIT-US-PHONE-AREA-CODE` + `CSLKPCDY` | `FE` area-code check against `domain.reference.LookupTables` (area codes from `CSLKPCDY`) | `validate` | `FieldEditorTest.phoneNumberPartsMustBeNumericAndNonZero` | area code must be a known NANP code | as expected | Done |
| CDM-15 | BR-28 | `EDIT-US-PHONE-PREFIX` | `FE` prefix check | `validate` | `FieldEditorTest.phoneNumberValidatesEachPart` | 3 digits, non-zero | as expected | Done |
| CDM-15 | BR-29 | `EDIT-US-PHONE-LINENUM` | `FE` line check | `validate` | `FieldEditorTest.phoneNumberValidatesEachPart` | 4 digits, non-zero | as expected | Done |
| CDM-15 | BR-30 | `1265-EDIT-US-SSN` | `FE.editUsSsn` | `validate` | `FieldEditorTest.ssnFirstPartExcludesReservedRanges`, `ssnPartsAreMandatory` | parts numeric, non-zero; first part not 000/666/900-999 | as expected | Done |
| CDM-15 | BR-31 | `1270-EDIT-US-STATE-CD` + `CSLKPCDY` | `FE.editUsStateCode`, `domain.reference.LookupTables` | `validate` | `FieldEditorTest.stateCodeMustBeKnown` | `<field>: is not a valid state code` | as expected | Done |
| CDM-15 | BR-32 | `1275-EDIT-FICO-SCORE` | `FE.editFicoScore` (300/850 constants) | `validate` | `FieldEditorTest.ficoScoreRange`, `AccountUpdateValidatorTest.aFicoScoreOutsideTheRangeIsRejectedAfterTheNumericEdit` | `<field>: should be between 300 and 850` | as expected | Done, **R** UCR-07 |
| CDM-15 | BR-33 | `1280-EDIT-US-STATE-ZIP-CD` | `FE.editUsStateZipCombo` | `validate` | `FieldEditorTest.stateAndZipMustBeAKnownCombination`, `AccountUpdateValidatorTest.aStateZipMismatchIsOnlyCheckedWhenBothFieldsAreValid` | first two zip digits must be registered for the state | as expected | Done |
| CDM-15 | BR-34 | `1200-EDIT-MAP-INPUTS` (commented-out edit) | `AUV` validates city, accepts `addressLine2` unvalidated | `validate` | `AccountUpdateValidatorTest.theFixtureFormPassesEveryEdit` | city required alphabetic; address line 2 unchecked | as expected (AS-IS) | Done, **R** UCR-02 |
| CDM-15 | BR-35 | `EDIT-YEAR` (`CSUTLDPY`) | `DE` year rules | `validate` | `DateEditorTest.yearMustBeFourDigitsInThe19thOr20thCentury` | 4 digits, 20th/21st century | as expected | Done, **R** UCR-07 (bound) |
| CDM-15 | BR-36 | `EDIT-MONTH` | `DE` month rules | `validate` | `DateEditorTest.monthAndDayRanges` | 1-12 | as expected | Done |
| CDM-15 | BR-37 | `EDIT-DAY` | `DE` day rules | `validate` | `DateEditorTest.monthAndDayRanges` | 1-31 | as expected | Done |
| CDM-15 | BR-38 | `EDIT-DAY-MONTH-YEAR` | `DE` day/month/leap-year rules | `validate` | `DateEditorTest.dayMonthCombinations`, `centuryYearsUseTheFourHundredYearRule` | 30-day months, February, the 400-year rule | as expected | Done |
| CDM-15 | BR-39 | `EDIT-DATE-CCYYMMDD` → `CSUTLDTC` | `DVS.validateYyyyMmDd` called after the part edits | `validate` | `DateValidationServiceTest` (4 cases), `DateEditorTest.aValidDateIsConverted` | a non-zero severity is reported even after the edits pass | as expected | Done, **R** UCR-06 |
| CDM-15 | BR-40 | `EDIT-DATE-OF-BIRTH` | `DE` future check | `validate` | `DateEditorTest.dateOfBirthCannotBeTodayOrLater` | `<field>:cannot be in the future` | as expected | Done |
| CDM-16 | BR-41 | `9200-GETCARDXREF-BYACCT` | `CardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumAsc` | `fetch` | `AccountStoreIntegrationTest.theCrossReferenceIsReachableByAccount` | resolved through the cross-reference by account | as expected | Done |
| CDM-16 | BR-42 | `9200`/`9300`/`9400` not-found | `ScreenMessages` (shared with the enquiry) | `fetch` | `AccountApiIntegrationTest.anUnknownAccountIsNotFound` | identical wording to the enquiry | as expected | Done |
| CDM-16 | BR-43 | `9200`/`9300`/`9400` `WHEN OTHER` | `StorageAccessException` | all | `AccountApiIntegrationTest` error mapping | any other status is fatal, naming the file | as expected | Done |
| CDM-16 | BR-44 | `9500-STORE-FETCHED-DATA` | the `original` form returned by `fetch` and echoed back | `fetch`/`validate`/`confirm` | `AccountUpdateServiceTest.fetchReturnsTheStoredValuesInBothBlocks` | fetched values become the baseline | as expected | Done, **R** CMD-04 |
| CDM-16 | BR-45 | `9500-STORE-FETCHED-DATA` | `ASM.toForm` splits dates | `fetch` | `AccountScreenMapperTest.theFormSplitsDatesPhonesAndTheSsnIntoMapFields` | dates held as year/month/day | as expected | Done |
| CDM-16 | BR-46 | `9600-WRITE-PROCESSING` `READ UPDATE` ×2 | `lockByAcctId` then `lockByCustId` | `confirm` | `AccountUpdateServiceTest.anUnavailableAccountLockStopsBeforeTheCustomerLock`, `anUnavailableCustomerLockStopsBeforeAnyRewrite`, `AccountStoreIntegrationTest.bothRecordsCanBeLockedForUpdate` | both records locked before any comparison or write | as expected | Done, CMD-03 |
| CDM-16 | BR-47 | `9700-CHECK-CHANGE-IN-REC` | baseline comparison → `RECORD_CHANGED` | `confirm` | `AccountUpdateServiceTest.aRecordThatMovedSinceItWasFetchedIsNotOverwritten`, `AccountApiIntegrationTest.aRecordChangedByAnotherUpdaterIsNotOverwritten` | nothing written; `Record changed by some one else. Please review` | as expected | Done |
| CDM-16 | BR-48 | `9700-CHECK-CHANGE-IN-REC` | the same comparison, reproducing the source's field selection | `confirm` | `AccountUpdateServiceTest.aRecordThatMovedSinceItWasFetchedIsNotOverwritten` | text compared ignoring case; DOB offsets as in the source | as expected (AS-IS) | Done, **R** UCR-10.3 |
| CDM-16 | BR-49 | `9600-WRITE-PROCESSING` | account save before customer save in one transaction | `confirm` | `AccountUpdateServiceTest.confirmRewritesTheAccountBeforeTheCustomer` | account first, customer only if it succeeds | as expected | Done, CMD-06 |
| CDM-16 | BR-50 | `9600` `SYNCPOINT ROLLBACK` | `@Transactional` rollback → `UPDATE_FAILED` | `confirm` | `AccountUpdateServiceTest.aFailingCustomerRewriteRollsTheUnitOfWorkBack` | the whole save is backed out and reported failed | as expected | Done, CMD-01/02 |
| CDM-16 | BR-51 | `9600` phone assembly | `AUS` stores `(AAA)PPP-LLLL` | `confirm` | `AccountUpdateServiceTest.confirmRewritesTheAccountBeforeTheCustomer` (stored value asserted) | phones stored formatted | as expected | Done |
| CDM-16 | BR-52 | `2000-DECIDE-ACTION` | `CHANGES_COMMITTED` + `ScreenMessages.CHANGES_COMMITTED` | `confirm` | `AccountApiIntegrationTest.theUpdateTransactionFetchesValidatesAndCommits` | `Changes committed to database` and the stored values redisplayed | as expected | Done |
| CDM-16 | BR-53 | `9600` (reissue date moved twice) | only the keyed year/month/day is applied — the target does not reproduce the redundant first move | `confirm` | `AccountUpdateServiceTest.confirmRewritesTheAccountBeforeTheCustomer` (the stored record carries the keyed values) | the keyed value is what is stored | as expected; the discarded first move is recorded, not reproduced | Done, UCR-13 |
| CDM-21 | BR-54 | `3310-PROTECT-ALL-ATTRS` | account and customer id rendered read-only | `fetch` | `AccountUpdateScreen.test.tsx` | keys displayed, not editable | as expected | Done |
| CDM-21 | BR-55 | `3300-SETUP-SCREEN-ATTRS` | `fieldFlags` for every failing field | `validate` | `AccountUpdateValidatorTest.everyFailingFieldIsFlaggedButOnlyTheFirstMessageIsReturned`, `AccountApiIntegrationTest.aFailingFieldEditIsReturnedWithItsFlag` | all failing fields highlighted; one message shown | as expected | Done |
| CDM-18 | BR-56 | `ABEND-ROUTINE` (`9999`) | `ApiExceptionHandler` → HTTP 500, transaction rolled back | all | `AccountApiIntegrationTest.aMalformedBodyIsARequestError` (envelope) | unexpected failure ends the interaction | as expected (no abend code) | Done, UCR-08 |

## 3. CBACT01C under READACCT — batch extract

Batch job/step: `readAcctJob` / `readAcctStep`. PostgreSQL object: `account` (read-only) plus the
Spring Batch metadata tables. React: n/a. API: n/a.

| Ticket | Rule | Source location | Target | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|
| CDM-19 | BR-01 | `READACCT.jcl` `PREDEL` | each writer `setShouldDeleteIfExists(true)` | `ReadAcctJobIntegrationTest.aRerunReplacesTheExtractRatherThanAppendingToIt` | a run starts from empty extracts | as expected for a new run | Done, **R** CMD-07 (restart) |
| CDM-19 | BR-02 | `0000-ACCTFILE-OPEN` | step start opens the reader and all three writers before the first item | `ReadAcctJobIntegrationTest.theJobWritesOneRecordPerAccountIntoEachExtract` | all files available before processing | as expected for the success path; an open failure is not injected | Done |
| CDM-19 | BR-03 | `1000-ACCTFILE-GET-NEXT` | `JpaPagingItemReader` ordered by `acct_id` | `ReadAcctJobIntegrationTest.theFirstAndLastCompRecordsCarryTheLowestAndHighestKey` | every account once, in key order | as expected | Done |
| CDM-19 | BR-04 | `1000-` status `'10'` | reader returns `null`; step completes | `ReadAcctJobIntegrationTest.theJobWritesOneRecordPerAccountIntoEachExtract` | end of file ends the run normally | as expected | Done |
| CDM-19 | BR-05 | `1000-` `WHEN OTHER` → `9910`/`CEE3ABD` | the exception propagates and fails the step | none — the failure path is not injected | any other read outcome ends the run abnormally | **not exercised**: framework default step failure, no test | Done, UCR-08, **R** (untested path) |
| CDM-19 | BR-06 | `1300`/`1400`/`1500`/`1575` | composite writer, three outputs, four records | `ReadAcctJobIntegrationTest.theJobWritesOneRecordPerAccountIntoEachExtract`, `AcctRecordAggregatorTest.everyAccountProducesATwelveByteAndAThirtyNineByteVariableRecord` | one summary, one array, two variable records per account | as expected | Done |
| CDM-19 | BR-07 | `1300-POPUL-ACCT-RECORD` | `AcctCompRecordAggregator` | `AcctRecordAggregatorTest.theCompRecordIsOneHundredAndSevenBytesInLayoutOrder` | 107 bytes, fields in layout order | as expected | Done |
| CDM-19 | BR-08 | `1300` `CALL 'COBDATFT'` | `cobol.CobdatftDateFormatter` (reconstructed) | `CobdatftDateFormatterTest` (4 cases), `AcctRecordAggregatorTest.theReissueDateIsWrittenAsTheEightDigitFormatTheAssemblerReturns` | reissue date converted type 2 → type 2, truncated by the caller | as expected for the observed pattern | Done, **R** UCR-04, UCR-05.1 |
| CDM-19 | BR-09 | `1300` (`2525.00`) | `AcctCompRecordAggregator.ZERO_DEBIT_DEFAULT` | `AcctRecordAggregatorTest.aZeroCurrentCycleDebitIsReplacedByTheHardCodedDefault`, `aNonZeroCurrentCycleDebitLeavesWhateverTheRecordAreaHeld` | zero debit → `2525.00`; otherwise the record area is left as it was | as expected (AS-IS) | Done, **R** UCR-07, UCR-05.3 |
| CDM-19 | BR-10 | `1350-WRITE-ACCT-RECORD` | `FlatFileItemWriter` (107-byte) | `ReadAcctJobIntegrationTest.theJobWritesOneRecordPerAccountIntoEachExtract` | appended to the 107-byte extract; failure is fatal | as expected | Done |
| CDM-19 | BR-11 | `1400-POPUL-ACCT-ARRAY` | `AcctArrayRecordAggregator` | `AcctRecordAggregatorTest.theArrayRecordIsOneHundredAndTenBytesWithOnlyThreeOccurrencesPopulated` | 110 bytes; occurrences 1-3 carry the literals, 4-5 stay zero | as expected (AS-IS) | Done, **R** UCR-07, UCR-05.4 |
| CDM-19 | BR-12 | `1450-WRITE-ACCT-ARRAY` | `FlatFileItemWriter` (110-byte) | `ReadAcctJobIntegrationTest.theJobWritesOneRecordPerAccountIntoEachExtract` | appended to the 110-byte extract | as expected | Done |
| CDM-19 | BR-13 | `1500`/`1575` | `AcctVbRecordAggregator` | `AcctRecordAggregatorTest.everyAccountProducesATwelveByteAndAThirtyNineByteVariableRecord` | short record = key + status; long record = key + amounts | as expected | Done |
| CDM-19 | BR-14 | `1550`/`1575` (lengths 12/39) | 4-byte RDW + payload, 84-byte maximum | `AcctRecordAggregatorTest.everyAccountProducesATwelveByteAndAThirtyNineByteVariableRecord` | 12 and 39 bytes | as expected | Done, **R** UCR-07 |
| CDM-19 | BR-15 | `1100-DISPLAY-ACCT-RECORD` | `INFO` logging per item | `evidence/batch-readacct.log` | every account logged field by field | as expected | Done |
| CDM-19 | BR-16 | `9000-ACCTFILE-CLOSE` | a writer close failure propagates and fails the step | none — the failure path is not injected | a close failure ends the run abnormally | **not exercised**: framework default step failure, no test | Done, UCR-08, **R** (untested path) |

## 4. CSUTLDTC — date validation service

Target: `domain.validation.DateValidationService`, called from `DateEditor`. No React component, no
API operation of its own, no PostgreSQL object, no batch step.

| Ticket | Rule | Source location | Target | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|
| CDM-06 | BR-01 | `A000-MAIN` initialise | a fresh result object per call | `DateValidationServiceTest.aValidDatePassesWithSeverityZero` | no outcome leaks between checks | as expected | Done |
| CDM-06 | BR-02 | `CALL "CEEDAYS"` | strict `java.time` parsing of the caller's mask | `DateValidationServiceTest.anImpossibleDateIsADateValueError` | the date is checked against the mask | as expected for the mask in use | Done, **R** UCR-06 |
| CDM-06 | BR-03 | `RETURN-CODE` from severity | the returned severity; callers treat `0` as valid | `DateValidationServiceTest.aValidDatePassesWithSeverityZero` | severity decides validity | as expected | Done |
| CDM-06 | BR-04 | `EVALUATE FEEDBACK-CODE` | the result-string mapping | `DateValidationServiceTest.aShortValueIsInsufficientData`, `nonNumericDataIsReportedSeparately` | one of the ten result strings | reachable outcomes as expected; four LE-only outcomes unreachable | Done, **R** UCR-06 |
| CDM-06 | BR-05 | `WHEN OTHER` | default `Date is invalid` | `DateValidationServiceTest.anImpossibleDateIsADateValueError` | anything unrecognised is invalid | as expected | Done |
| CDM-06 | BR-06 | result structure | the result record carries severity, code, text, date and mask | `DateValidationServiceTest` (all 4) | the result echoes the checked date and mask | as expected | Done |

## 5. COMEN01C — menu access rules (business-level access)

Target: `service.MenuAccessService`. React: `MenuScreen.tsx`. API: `menu`. No PostgreSQL object,
no batch step.

| Ticket | Rule | Source location | Target | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|
| CDM-17 | BR-01 | `PROCESS-ENTER-KEY` | `MAS` option validation | `MenuAccessServiceTest.anUnknownProgramIsARequestError` | a numeric, existing, non-zero option is required | as expected | Done |
| CDM-17 | BR-02 | `PROCESS-ENTER-KEY` `INSPECT` | blanks read as zeros before evaluation | `MenuAccessServiceTest.anUnknownProgramIsARequestError` | blanks become zeros | as expected | Done |
| CDM-17 | BR-03 | `PROCESS-ENTER-KEY` (`CDEMO-USRTYP-USER`) | `AdminOnlyOptionException` → HTTP 403 | `MenuAccessServiceTest.accessIsGrantedForBothProgramsAndBothUserTypes` | admin-only options refused to regular operators | as expected | Done |
| CDM-17 | BR-04 | `COMEN02Y` option table | both account options open to regular operators | `MenuAccessServiceTest.bothAccountOptionsAreOpenToARegularUser`, `anAdministratorSeesTheSameOptions` | options 1 and 2 available to a regular operator | as expected | Done |
| CDM-17 | BR-05 | `PROCESS-ENTER-KEY` (`'DUMMY'`) | placeholder/unavailable options reported, not entered | `MenuAccessServiceTest.anUnknownProgramIsARequestError` | reported rather than entered | as expected | Done |
| CDM-17 | BR-06 | `PROCESS-ENTER-KEY` COMMAREA moves | the caller is implicit in the API; the UI returns to `MenuScreen` | `AccountViewScreen.test.tsx`, `AccountUpdateScreen.test.tsx` | the menu is remembered as the caller | as expected (no COMMAREA) | Done, UCR-03 |
| CDM-17 | BR-07 | `WHEN DFHPF3` → `COSGN00C` | out of scope: sign-on is not converted | — | exit returns to sign-on | not implemented — sign-on is outside the capability | Done, **R** `13-artifact-disposition.md` (COSGN00C retired from this scope) |

## 6. Non-rule tickets

| Ticket | Deliverable | Evidence | Status |
|---|---|---|---|
| CDM-1, CDM-2 | Source inventory and coverage | `02-source-inventory-and-coverage.md` | Done |
| CDM-3 … CDM-8 | Rule catalogues and consolidation | `BRD-*.md`, `BRE-Report-CARDDEMO-ACCOUNT.md` | Done |
| CDM-9 | Architecture and decomposition | `04-architecture.md` | Done |
| CDM-10, CDM-11 | COBOL semantics and domain model | `cobol/*`, `domain/*`; `CobolTextTest`, `CobolNumericTest`, `CobolRecordBuilderTest`, `ZonedDecimalCodecTest` | Done |
| CDM-12 | Flyway schema and seed | `V1__create_account_management_schema.sql`, `V2__load_sample_data.sql`; `evidence/migration.log`, `evidence/db-clean.log` | Done |
| CDM-13 | Persistence | `persistence/*`; `AccountStoreIntegrationTest` | Done |
| CDM-18 | OpenAPI contract | `openapi/openapi.yaml`; `evidence/openapi-lint.log` | Done |
| CDM-20, CDM-21 | React screens | `frontend/src/*`; `evidence/frontend-test.log` | Done |
| CDM-22 | Tests and coverage | `evidence/backend-build-test.log` — 172 tests, 96.0% line / 85.7% branch | Done |
| CDM-23 | Build/startup/smoke/batch evidence | `evidence/backend-startup.log`, `api-smoke.log`, `batch-readacct.log` | Done |
| CDM-24 | Docker assets | `evidence/docker-build.log` — registry limit | **Blocked**, UCR-09 |
| CDM-25 | Data reconciliation | `evidence/sample-data-reconciliation.log` — `RECONCILIATION PASSED` | Done |
| CDM-26 | Consistency-model delta register | `12-consistency-model-delta-register.md` | Done |
| CDM-27 | Unsupported construct register | `11-unsupported-construct-register.md` | Done |
| CDM-28 | Artifact disposition | `13-artifact-disposition.md` — 159/159 | Done |
| CDM-29 | This matrix | `14-rtm.md` | Done |
| CDM-30 | Documentation and README | `docs/*`, `README.md` | Done |
| CDM-31 | Auto-fix log | `16-auto-fix-log.md` | Done |
| CDM-32 | Delivery | branch, commit, pull request | Done |
| CDM-33 | Manual-review backlog | `17-limitations-and-review-required.md` | Done |

## 7. Coverage of Part A

108 of 108 extracted account and user rules are traced.

- 104 rows cite at least one executed test; 4 cite a captured evidence log only (`CBACT01C` BR-15 and
  the three logging/close/open-failure paths).
- 3 rows are explicitly **not exercised** by a test: `COACTVWC` BR-05 and `COACTUPC` BR-05 (unreachable
  in the target's total status enum) and the injected-I/O-failure paths of `CBACT01C` BR-05 and BR-16.
  Each says so in its own row rather than claiming coverage it does not have.
- Every REVIEW REQUIRED marker resolves to an entry in `11-unsupported-construct-register.md` or
  `12-consistency-model-delta-register.md` and is listed in `17-limitations-and-review-required.md` §3.

---

# Part B — Credit Card Authorizations

Added for the authorization closure (`app/app-authorization-ims-db2-mq/`). Same conventions as Part
A. **Actual** is `as expected` only where the cited test executed and passed in
`evidence/authorization-mvn-verify.txt` (371 backend tests, 0 failures),
`evidence/frontend-test.log` (42 frontend tests), `evidence/authorization-purge-batch-run.txt` or
`evidence/authorization-data-reconciliation.txt`.

Package prefix `com.carddemo` is omitted, and `a` abbreviates `authorization`:
- `ADE` = `a.domain.AuthorizationDecisionEngine`, `AK` = `a.domain.AuthorizationKey`,
  `AM` = `a.domain.AuthorizationMessages`, `DR` = `a.domain.DeclineReason`,
  `FS` = `a.domain.FraudStatus`, `MS` = `a.domain.MatchStatus`,
  `ARS` = `a.domain.AuthorizationReadState`
- `RQM` = `a.messaging.AuthorizationRequestMessage`, `RLM` = `a.messaging.AuthorizationReplyMessage`,
  `ARL` = `a.messaging.AuthorizationRequestListener`,
  `ARP` = `a.messaging.AuthorizationReplyPublisher`
- `ARPR` = `a.service.AuthorizationRequestProcessor`, `AIS` = `a.service.AuthorizationInquiryService`,
  `FMS` = `a.service.FraudMarkingService`
- `CLC` = `a.client.CardholderLookupClient`, `STI` = `a.client.ServiceTokenIssuer`,
  `KC` = `a.config.KafkaConfig`, `ATP` = `a.config.AuthorizationTopicsProperties`,
  `AC` = `a.api.AuthorizationController`
- `APP` = `batch.auth.AuthPurgeProperties`, `ASKR` = `batch.auth.AuthSummaryKeysetReader`,
  `EAP` = `batch.auth.ExpiredAuthorizationProcessor`, `APW` = `batch.auth.AuthPurgeWriter`,
  `PAJC` = `batch.auth.PurgeAuthJobConfig`
- API operations: `listAuthorizations` = `GET /api/authorizations`, `getAuthorization` =
  `GET /api/authorizations/{accountId}/{authKey}`, `toggleFraud` =
  `POST /api/authorizations/{accountId}/{authKey}/fraud`, `lookupCardholder` =
  `GET /api/cardholders/{cardNumber}` (account service).
- Kafka destinations: `request` = `carddemo.authorization.request.v1`, `reply` =
  `carddemo.authorization.reply.v1`, `dlt` = `carddemo.authorization.request.DLT`.

---

## 8. COPAUA0C — authorization request processor (transaction CP00, MQ-triggered)

PostgreSQL objects: `pending_auth_summary`, `pending_auth_detail`, `authorization_request_log`,
`authorization_reply_outbox`. React component: n/a (no screen — the source program is queue-driven).
Kafka: `request` in, `reply`/`dlt` out.

| Ticket | Rule | Source location | Target | API / destination | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-38 | BR-01 | `1000-INITIALIZE` `RETRIEVE INTO(MQTM)` | `ARL` `@KafkaListener(topics = "${…request-topic}")`; the trigger-supplied queue name becomes the configured topic of `ATP` | `request` | `AuthorizationRequestListenerIntegrationTest.anAuthorizationRequestIsAnsweredOnTheReplyTopic` | work is taken from the destination the platform names, not one the program chooses | as expected | Done, UCR-17 |
| CDM-38 | BR-02 | `MOVE 5000 TO WS-WAIT-INTERVAL` | retired: a Kafka listener is pushed records, so no wait interval exists; `max.poll.interval` replaces it | `request` | — | the consumer does not exit while work may still arrive | platform mechanism, not reproduced | Done, UCR-17 |
| CDM-38 | BR-03 | `1100-OPEN-REQUEST-QUEUE` `MQOO-INPUT-SHARED` | retired: a Kafka consumer group gives the same shared consumption; `KC` sets `group-id` | `request` | `AuthorizationRequestListenerIntegrationTest` (a single group consumes each record once) | several instances may consume the same source of work without duplication | as expected | Done, CMD-14 |
| CDM-38 | BR-04 | `2000-MAIN-PROCESS` `PERFORM UNTIL NO-MORE-MSG-AVAILABLE` | `ARL.onRequest` — one record per invocation, the listener loop replaces the program loop | `request` | `AuthorizationRequestListenerIntegrationTest.anAuthorizationRequestIsAnsweredOnTheReplyTopic` | requests are processed one at a time until none remain | as expected | Done |
| CDM-38 | BR-05 | `WS-REQSTS-PROCESS-LIMIT VALUE 500` | retired: the 500-request cap existed to release a CICS task; a long-running listener has no task to release | `request` | — | a single unit of work does not monopolise the platform | platform mechanism, not reproduced | Done, UCR-17 |
| CDM-38 | BR-06 | `EXEC CICS SYNCPOINT` inside the loop | `ARPR.process` is `@Transactional` per record, so each request commits alone | `request` | `AuthorizationRequestProcessorIntegrationTest.theReplyIsWrittenToTheOutboxInTheSameTransaction` | one request's outcome never depends on the next | as expected | Done, CMD-10 |
| CDM-38 | BR-07 | `2100-EXTRACT-REQUEST-MSG` `UNSTRING … ','` | `RQM.parse` — the nine comma-delimited fields of `CCPAURQY` in source order | `request` | `AuthorizationMessageLayoutTest.theRequestIsParsedFieldByField` | field for field, the source request layout | as expected | Done |
| CDM-38 | BR-08 | `FUNCTION NUMVAL(WS-TRANSACTION-AMT-AN)` | `RQM.parse` amount conversion, rejecting what `NUMVAL` could not read | `request` | `AuthorizationMessageLayoutTest.anAmountThatNumvalCouldNotReadIsMalformed` | the amount is read as a number | as expected | Done, UCR-19 |
| CDM-38 | BR-09 | `3100-READ-REQUEST-MQ` `MQMD-CORRELID`/`MQMD-REPLYTOQ` | `ARL` reads the `kafka_correlationId` and reply-destination headers; `ARPR` stores both on the outbox row | `reply` | `AuthorizationRequestListenerTest.thePresentedCorrelationIdAndReplyDestinationAreUsedWhenTheRequesterSuppliesThem`, `…IntegrationTest.theReplyGoesToTheDestinationTheRequesterNamed` | the reply goes where the requester asked, under the correlator it gave | as expected | Done |
| CDM-39 | BR-10 | `SET APPROVE-AUTH TO TRUE` | `ADE.decide` starts from approved | `request` | `AuthorizationDecisionEngineTest.anAmountWithinTheSummaryAvailableCreditIsApproved` | an authorization is approved unless a rule declines it | as expected | Done |
| CDM-38 | BR-11 | `5100`/`5200`/`5300-READ-…-RECORD` | `CLC.findByCardNumber` over the account service's `lookupCardholder`, which performs the same xref → account → customer closure | `lookupCardholder` | `CardholderLookupClientTest.aFoundCardholderIsReadWithTheBearerTokenOfTheCaller`, `CardholderLookupServiceTest` (account service) | cardholder data is resolved from the card number | as expected | Done |
| CDM-39 | BR-12 | `IF CARD-FOUND-XREF` guards | `CLC` returns absent on an unknown card and `ADE.decide` declines without any further lookup; `ARPR` writes no authorization | `request` | `AuthorizationDecisionEngineTest.anUnknownCardIsDeclinedWithTheRecordNotFoundReason`, `AuthorizationRequestProcessorIntegrationTest.anUnknownCardIsDeclinedAndWritesNoAuthorization` | an unknown card stops the lookups and declines | as expected | Done |
| CDM-38 | BR-13 | `5500-READ-AUTH-SUMMRY` `GU SEGMENT(PAUTSUM0)` | `AuthorizationSummaryRepository.findByIdForUpdate` by account id | `request` | `AuthorizationRequestProcessorIntegrationTest.anApprovedAuthorizationCreatesTheSummaryAndTheDetail` | the account's summary is read before the decision | as expected | Done, CMD-14 |
| CDM-38 | BR-14 | `5600-READ-PROFILE-DATA` `CONTINUE` | not implemented, because the source paragraph does nothing | n/a | — | profile data does not affect the decision | recorded, not implemented | Done, UCR-20 |
| CDM-39 | BR-15 | `6000-MAKE-DECISION` 666-668 | `ADE.decide` — `creditLimit - creditBalance` of the summary | `request` | `AuthorizationDecisionEngineTest.anAmountWithinTheSummaryAvailableCreditIsApproved` | available credit is the summary's limit less its credit balance | as expected | Done |
| CDM-39 | BR-16 | `6000-MAKE-DECISION` 674-676 | `ADE.decide` fallback — the account's credit limit less its current balance | `request` | `AuthorizationDecisionEngineTest.theFirstAuthorizationOfAnAccountIsDecidedFromTheAccountMaster` | the first authorization of an account is decided from the account master | as expected | Done |
| CDM-39 | BR-17 | `6000-MAKE-DECISION` 669-672, 677-680 | `ADE.decide` insufficient-funds branch | `request` | `AuthorizationDecisionEngineTest.anAmountOverTheSummaryAvailableCreditIsDeclinedForInsufficientFunds`, `anAmountEqualToTheAvailableCreditIsApproved` | over available credit declines; equal to it approves | as expected | Done |
| CDM-39 | BR-18 | `6000-MAKE-DECISION` 681-683 | `ADE.decide` record-not-found branch, which outranks the funds branch | `request` | `AuthorizationDecisionEngineTest.aMissingCustomerRecordOutranksInsufficientFunds` | a request without an account is declined | as expected | Done |
| CDM-39 | BR-19 | `6000-MAKE-DECISION` 686-698 | `AuthorizationDecision.responseCode` — `00` approved, `05` declined | `request` | `AuthorizationDecisionEngineTest.anAmountWithinTheSummaryAvailableCreditIsApproved`, `…IsDeclinedForInsufficientFunds` | `00` on approval, `05` on decline | as expected | Done |
| CDM-39 | BR-20 | `6000-MAKE-DECISION` 689-697 | `AuthorizationDecision.approvedAmount` — the transaction amount on approval, zero on decline | `request` | same two tests | approved amount equals the request on approval, zero otherwise | as expected | Done |
| CDM-39 | BR-21 | `6000-MAKE-DECISION` `EVALUATE TRUE` reason table | `DeclineReasonFlag` → `DR` codes `3100`/`4100`/`4200`/`4300`/`5100`/`5200`/`9000` | `request` | `DeclineReasonTest.everyDecisionFlagResolvesToATableEntry`, `AuthorizationDecisionEngineTest.theUnknownReasonRemainsDefinedEvenThoughNoDecisionPathReachesIt` | each decline carries the source's reason code | as expected | Done, UCR-18 |
| CDM-38 | BR-22 | `6000-MAKE-DECISION` `STRING … DELIMITED BY SIZE` | `RLM.toBuffer` keeps the `CCPAURLY` widths and the edited amount picture | `reply` | `AuthorizationMessageLayoutTest.theReplyBufferKeepsTheCopybookWidths`, `aNegativeApprovedAmountCarriesTheHyphenOfTheEditedPicture` | the reply layout is the source's, byte position for byte position | as expected | Done |
| CDM-38 | BR-23 | `MOVE PA-RQ-AUTH-TIME TO PA-RL-AUTH-ID-CODE` | `ARPR` copies the request's authorization time into the reply's authorization id code | `reply` | `AuthorizationMessageLayoutTest.theRequestIdIsTheCardTheStampedTimestampAndTheTransactionId` | the authorization id code is the request's time | as expected | Done |
| CDM-38 | BR-24 | `7100-SEND-RESPONSE` `MQMT-REPLY`, expiry 50, not persistent | `ARP.publish` — correlation id and reply destination preserved as headers; MQ persistence, expiry and message type have no Kafka counterpart and are replaced by topic retention | `reply` | `AuthorizationReplyPublisherTest.theNamedReplyDestinationAndCorrelationIdOfTheRequestAreCarriedOnTheReply`, `aReplyWithoutAReplyDestinationOrCorrelationIdGoesToTheDefaultTopic` | the reply is addressed and correlated as the requester expects | as expected for addressing and correlation; expiry/persistence are platform mechanisms | Done, UCR-21 |
| CDM-38 | BR-25 | `5000-PROCESS-AUTH` 460-466 (reply sent, then stored) | **deliberate delta**: `ARPR` writes the reply to `authorization_reply_outbox` inside the same transaction as the authorization, and `ARP` publishes it after commit | `reply` | `AuthorizationRequestProcessorIntegrationTest.theReplyIsWrittenToTheOutboxInTheSameTransaction` | the requester is answered for every authorization that was stored, and only for those | as expected — the source could answer an authorization it then failed to store | Done, CMD-11 |
| CDM-38 | BR-26 | `8400-UPDATE-SUMMARY` `INITIALIZE … REPLACING NUMERIC DATA BY ZERO` | `AuthorizationSummaryEntity` zero-initialised numeric fields | `request` | `AuthorizationRequestProcessorIntegrationTest.anApprovedAuthorizationCreatesTheSummaryAndTheDetail` | a new summary starts from zero | as expected | Done |
| CDM-38 | BR-27 | `8400-UPDATE-SUMMARY` 811-812 | `ARPR` refreshes credit and cash limits from the looked-up account on every decision | `request` | same test | the summary's limits follow the account master | as expected | Done |
| CDM-38 | BR-28 | `8400-UPDATE-SUMMARY` 814-819 | `ARPR` approved branch: count + 1, approved amount + amount, credit balance + amount, cash balance set to zero | `request` | same test | an approved authorization consumes credit and is counted | as expected | Done, UCR-22 |
| CDM-38 | BR-29 | `8400-UPDATE-SUMMARY` 820-823 | `ARPR` declined branch: count + 1, declined amount + **this** request's amount | `request` | `AuthorizationRequestProcessorIntegrationTest.anAuthorizationOverTheSummaryCreditIsDeclinedForInsufficientFunds` | declines are counted and totalled | **deliberate delta** — the source added the previous request's amount | Done, UCR-30 |
| CDM-38 | BR-30 | `8400-UPDATE-SUMMARY` 825-849 (ISRT or REPL) | `AuthorizationSummaryRepository.save` — insert or update on the same primary key | `request` | `AuthorizationRequestProcessorIntegrationTest.anApprovedAuthorizationCreatesTheSummaryAndTheDetail` | the summary is created on first use and updated afterwards | as expected | Done |
| CDM-38 | BR-31 | `8500-INSERT-AUTH` 858-875 | `AK.of` — `99999 - YYDDD` and `999999999 - HHMMSSmmm` | `request` | `AuthorizationKeyTest.theKeyIsTheNinesComplementOfTheJulianDateAndTheTimeOfDay`, `aLaterAuthorizationHasTheLowerKey`, `theJulianDateIsRecoveredFromTheComplement` | the key is the nines complement of date and time, so newest sorts first | as expected | Done |
| CDM-38 | BR-32 | `8500-INSERT-AUTH` 877-904 | `ARPR` maps every `CIPAUDTY` field from the request; `AuthorizationDetailEntity` keeps the widths | `request` | `AuthorizationRequestProcessorIntegrationTest.anApprovedAuthorizationCreatesTheSummaryAndTheDetail`, `AuthorizationPostgresIntegrationTest` | the authorization holds the source's detail fields | as expected | Done |
| CDM-38 | BR-33 | `8500-INSERT-AUTH` 906-910 | `MS` — `P` on approval, `D` on decline | `request` | `AuthorizationRequestProcessorIntegrationTest` (both decision tests assert the stored status) | approved is `P`, declined `D` | as expected | Done |
| CDM-38 | BR-34 | `8500-INSERT-AUTH` 912-913 | `ARPR` leaves fraud flag and fraud report date blank | `request` | same tests | a new authorization carries no fraud state | as expected | Done |
| CDM-38 | BR-35 | `8500-INSERT-AUTH` 915-934 | `AuthorizationDetailEntity` composite id (`acct_id`, `auth_key`) with a foreign key to `pending_auth_summary` | `request` | `AuthorizationPostgresIntegrationTest`, `AuthorizationRequestProcessorIntegrationTest` | an authorization exists only under its account's summary | as expected | Done |
| CDM-36 | BR-36 | `1200-SCHEDULE-PSB` | retired: a JPA `EntityManager` needs no PSB schedule; the PSB's segment list becomes the module's repository set | n/a | — | the program has access to exactly the segments it declared | platform mechanism, not reproduced | Done |
| CDM-38 | BR-37 | `9500-LOG-ERROR`, `CCPAUERY` | `ARPR`/`ARL` structured logging with the masked card number; the record is still answered or dead-lettered rather than failed back to the requester | `dlt` | `AuthorizationMessageLayoutTest.theCardNumberIsMaskedForLogging`, `AuthorizationRequestListenerIntegrationTest.aMalformedRequestIsDeadLetteredWithoutRetrying` | an internal failure is recorded, not raised to the requester | as expected | Done, UCR-19 |
| CDM-38 | BR-38 | `9000-TERMINATE`, `9100-CLOSE-REQUEST-QUEUE` | retired: Spring stops the listener container on shutdown | n/a | — | the input source is released cleanly at the end | platform mechanism, not reproduced | Done |

---

## 9. COPAUS0C — pending authorization summary (transaction CPVS)

React component: `AuthorizationSummaryScreen.tsx`. API: `listAuthorizations`. PostgreSQL objects:
`pending_auth_summary`, `pending_auth_detail` (read-only). Batch: n/a.

| Ticket | Rule | Source location | Target | API | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-40 | BR-01 | `PROCESS-ENTER-KEY` 261-270 | `AC.requireNumericAccountId` blank branch → `AM.PLEASE_ENTER_ACCT_ID` | `listAuthorizations` | `AuthorizationApiIntegrationTest.anEmptyAccountIdIsRejectedWithTheSourcePrompt` | an empty account id answers `Please enter Acct Id...` | as expected | Done |
| CDM-40 | BR-02 | `PROCESS-ENTER-KEY` 272-283 | `AC.requireNumericAccountId` digit check → `AM.ACCT_ID_MUST_BE_NUMERIC`, no read attempted | `listAuthorizations` | `AuthorizationApiIntegrationTest.aNonNumericAccountIdIsRejectedWithTheSourceMessage` | a non-numeric account id is rejected before any read | as expected | Done |
| CDM-40 | BR-03 | `MAIN-PARA` 202-215 | `AuthorizationSummaryScreen` passes the account id it was navigated with and lists immediately | `listAuthorizations` | `AuthorizationSummaryScreen.test.tsx` — `shows the totals of PAUTSUM0 and the five row page` | an account id supplied by the caller is used at once | as expected | Done |
| CDM-40 | BR-04 | `GATHER-ACCOUNT-DETAILS`, `GETCARDXREF-BYACCT`, `GETACCTDATA-BYACCT`, `GETCUSTDATA-BYCUST` | `AIS.summary` header built from `CLC` (account-service closure), never from the account database | `listAuthorizations`, `lookupCardholder` | `AuthorizationApiIntegrationTest.theFirstPageShowsTheHeaderTheTotalsAndFiveNewestAuthorizations`, `anAccountThatIsNotInTheCrossReferenceIsReportedWithTheSourceMessage` | the header is the cardholder closure of the account | as expected | Done |
| CDM-40 | BR-05 | `GATHER-ACCOUNT-DETAILS` 757-765 | `AIS.formatName` — first, space, middle initial, space, last, each trimmed | `listAuthorizations` | `AuthorizationInquiryServiceTest.summaryOfAnAccountWithoutASummarySegmentShowsZeroTotalsAndTheCustomerHeader` | the source's name composition | as expected | Done |
| CDM-40 | BR-06 | `GATHER-ACCOUNT-DETAILS` 767-779 | `AIS.formatAddress` — line 1 + line 2 comma-separated; line 3, state and five-digit zip comma-separated | `listAuthorizations` | same test | the source's address composition | as expected | Done |
| CDM-40 | BR-07 | `GATHER-ACCOUNT-DETAILS` 787-800 | `AIS.summary` totals from `AuthorizationSummaryEntity` | `listAuthorizations` | `AuthorizationApiIntegrationTest.theFirstPageShowsTheHeaderTheTotalsAndFiveNewestAuthorizations` | the six metrics come from the summary | as expected | Done |
| CDM-40 | BR-08 | `GATHER-ACCOUNT-DETAILS` 801-807 | `AIS.summary` zero totals when no summary row exists | `listAuthorizations` | `AuthorizationApiIntegrationTest.anAccountWithoutASummarySegmentShowsZeroTotals`, `AuthorizationInquiryServiceTest.summaryOfAnAccountWithoutASummarySegmentShowsZeroTotalsAndTheCustomerHeader` | no summary shows zeros, not an error | as expected | Done |
| CDM-40 | BR-09 | `GATHER-DETAILS` 352-355 | `AIS.summary` lists children only when the summary exists | `listAuthorizations` | `AuthorizationApiIntegrationTest.anAccountWithoutASummarySegmentShowsZeroTotals` | no summary means no list | as expected | Done |
| CDM-40 | BR-10 | `PROCESS-PAGE-FORWARD`, `POPULATE-AUTH-LIST` `WHEN 1 … WHEN 5` | `AIS.PAGE_SIZE = 5` with `PageRequest.of(0, 5)` | `listAuthorizations` | `AuthorizationApiIntegrationTest.theFirstPageShowsTheHeaderTheTotalsAndFiveNewestAuthorizations`; `AuthorizationSummaryScreen.test.tsx` | at most five authorizations a page | as expected | Done |
| CDM-40 | BR-11 | `GET-AUTHORIZATIONS` `GNP SEGMENT(PAUTDTL1)` | `AuthorizationDetailRepository.findChildren` ordered by `auth_key` ascending — the nines-complement key, so newest first | `listAuthorizations` | `AuthorizationApiIntegrationTest.theFirstPageShowsTheHeaderTheTotalsAndFiveNewestAuthorizations`, `AuthorizationKeyTest.aLaterAuthorizationHasTheLowerKey` | newest first, by key order | as expected | Done |
| CDM-40 | BR-12 | `POPULATE-AUTH-LIST` 522-605 | `AuthorizationListRow` — transaction id, date, time, type, approval indicator, match status, approved amount | `listAuthorizations` | same test; `AuthorizationSummaryScreen.test.tsx` | the source's seven row fields | as expected | Done |
| CDM-40 | BR-13 | `POPULATE-AUTH-LIST` 526-534 | `AIS.editDate`/`editTime` — `MM/DD/YY` and `HH:MM:SS` from the original request values | `listAuthorizations` | `AuthorizationApiIntegrationTest.theFirstPageShowsTheHeaderTheTotalsAndFiveNewestAuthorizations`, `AuthorizationInquiryServiceTest.detailBuiltFromASegmentWithShortDateTimeAndExpiryLeavesTheMapFieldsEmpty` | the source's edited date and time | as expected; the two-digit year is carried forward | Done, UCR-23 |
| CDM-40 | BR-14 | `POPULATE-AUTH-LIST` 536-540 | `AuthorizationListRow.approvalIndicator` — `A` for response code `00`, else `D` | `listAuthorizations` | `AuthorizationApiIntegrationTest.theFirstPageShowsTheHeaderTheTotalsAndFiveNewestAuthorizations` | `A` only for `00` | as expected | Done |
| CDM-40 | BR-15 | `INITIALIZE-AUTH-DATA` | retired: the response carries only the rows that exist, so no stale row can be displayed | `listAuthorizations` | `AuthorizationApiIntegrationTest.pagingPastTheLastAuthorizationReportsTheBottomOfThePage` | a short page shows no leftover data | as expected | Done |
| CDM-40 | BR-16 | `MOVE DFHBMUNP/DFHBMPRO TO SEL000nA` | retired attribute-byte mechanism: the UI offers a selection action only for a returned row | `listAuthorizations` | `AuthorizationSummaryScreen.test.tsx` — `transfers to the detail screen on S and refuses any other selection` | only a real authorization can be selected | as expected | Done |
| CDM-40 | BR-17 | `PROCESS-ENTER-KEY` 300-322 | retired: the UI sends one selected authorization key, so "first selected wins" cannot arise | `getAuthorization` | `AuthorizationSummaryScreen.test.tsx` — same test | exactly one authorization is opened | as expected | Done |
| CDM-40 | BR-18 | `PROCESS-ENTER-KEY` 325-340 | `AC.requireValidSelection` → `AM.INVALID_SELECTION` (`Invalid selection. Valid value is S`), accepting `S` or `s` | `getAuthorization` | `AuthorizationApiIntegrationTest.aSelectionOtherThanSIsRejected`; `AuthorizationSummaryScreen.test.tsx` | only `S` selects | as expected | Done |
| CDM-40 | BR-19 | `PROCESS-ENTER-KEY` 305-341, `CDEMO-CPVS-PAU-SELECTED` | the selected authorization key is a path variable of `getAuthorization` instead of a COMMAREA field | `getAuthorization` | `AuthorizationApiIntegrationTest.theDetailScreenShowsTheEditedAuthorizationFields` | the selection carries the authorization key to the detail screen | as expected | Done |
| CDM-40 | BR-20 | `PROCESS-PAGE-FORWARD` 439-443, `PROCESS-PF8-KEY`, `REPOSITION-AUTHORIZATIONS` | `AIS.summary(accountId, afterAuthKey)` keyset paging — the last key of the page shown | `listAuthorizations` | `AuthorizationApiIntegrationTest.pagingForwardContinuesAfterTheLastKeyOfThePreviousPage`; `AuthorizationSummaryScreen.test.tsx` — `pages forward from the last key shown, as PF8 did` | the next page continues after the last key shown | as expected | Done |
| CDM-40 | BR-21 | `PROCESS-PAGE-FORWARD` 444-449, `PROCESS-PF7-KEY` | the UI keeps the first key of each page it has shown and re-requests it, so backward paging needs no server state | `listAuthorizations` | `AuthorizationSummaryScreen.test.tsx` — `reports the top of the page instead of paging back off the first page` | paging back returns to the page previously shown | as expected | Done |
| CDM-40 | BR-22 | `PROCESS-PF7-KEY` 380-385 | `AM.ALREADY_AT_TOP` (`You are already at the top of the page...`) raised by the screen on the first page | `listAuthorizations` | `AuthorizationSummaryScreen.test.tsx` — same test | the source's top-of-list message | as expected | Done |
| CDM-40 | BR-23 | `PROCESS-PF8-KEY` 405-413 | `AM.ALREADY_AT_BOTTOM` (`You are already at the bottom of the page...`) returned when no further authorization exists | `listAuthorizations` | `AuthorizationApiIntegrationTest.pagingPastTheLastAuthorizationReportsTheBottomOfThePage`; `AuthorizationSummaryScreen.test.tsx` | the source's bottom-of-list message | as expected | Done |
| CDM-40 | BR-24 | `PROCESS-PAGE-FORWARD` 452-457 | `AIS.summary` reads `PAGE_SIZE + 1` rows and reports `moreRows` from the extra one | `listAuthorizations` | `AuthorizationApiIntegrationTest.pagingForwardContinuesAfterTheLastKeyOfThePreviousPage` | one read ahead decides whether a next page exists | as expected | Done |
| CDM-40 | BR-25 | `GET-AUTHORIZATIONS` 466-470, `REPOSITION-AUTHORIZATIONS` 500-504 | an exhausted keyset page ends the list | `listAuthorizations` | `AuthorizationApiIntegrationTest.pagingPastTheLastAuthorizationReportsTheBottomOfThePage` | the end of the child chain ends the list | as expected | Done |
| CDM-40 | BR-26 | `GET-AUTHORIZATIONS` 471-480, `GET-AUTH-SUMMARY` 985-996 | `AuthorizationExceptionHandler` maps a read failure to the screen's error contract; `CardholderLookupException` becomes an upstream failure | `listAuthorizations` | `AuthorizationApiIntegrationTest.aFailureOfTheAccountServiceIsReportedAsAnUpstreamFailure` | a read failure is reported on the screen, not silently | as expected | Done |
| CDM-40 | BR-27 | `SEND-PAULST-SCREEN` 683-688 | retired: a read-only query needs no syncpoint; the transaction ends with the request | `listAuthorizations` | — | held database resources are released when the screen is sent | platform mechanism, not reproduced | Done, CMD-10 |
| CDM-40 | BR-28 | `POPULATE-HEADER-INFO` | `AuthorizationSummaryResponse` title/programme/date/time header fields rendered by the screen | `listAuthorizations` | `AuthorizationSummaryScreen.test.tsx` — `shows the totals of PAUTSUM0 and the five row page` | the source's screen header | as expected | Done |
| CDM-40 | BR-29 | `MAIN-PARA` 253-258, `CCDA-MSG-INVALID-KEY` | retired PF-key mechanism: the screen exposes only the actions that exist, so no invalid key can be sent | n/a | — | an action the screen does not offer is refused | platform mechanism, not reproduced | Done |
| CDM-40 | BR-30 | `MAIN-PARA` 235-239, `RETURN-TO-PREV-SCREEN` | the screen's back action returns to `MenuScreen` | n/a | `AuthorizationSummaryScreen.test.tsx` | leaving the screen returns to the caller | as expected | Done |

---

## 10. COPAUS1C — authorization detail and fraud marking (transaction CPVD)

React component: `AuthorizationDetailScreen.tsx`. APIs: `getAuthorization`, `toggleFraud`.
PostgreSQL objects: `pending_auth_detail`, `auth_fraud`.

| Ticket | Rule | Source location | Target | API | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-40 | BR-01 | `PROCESS-ENTER-KEY` 208-225 | `AC.detail` requires a numeric account id and a 14-digit key (`AK.parse`) | `getAuthorization` | `AuthorizationApiIntegrationTest.anAuthorizationThatIsNotOnTheAccountIsReportedAsNotFound`, `AuthorizationKeyTest.aKeyThatIsNotFourteenDigitsIsRejected` | an authorization is identified by account and key | as expected | Done |
| CDM-40 | BR-02 | `READ-AUTH-RECORD` `GU … SEGMENT(PAUTDTL1) WHERE(PAUT9CTS=…)` | `AuthorizationDetailRepository.findById` on the composite id, so a key belonging to another account is not found | `getAuthorization` | `AuthorizationApiIntegrationTest.anAuthorizationThatIsNotOnTheAccountIsReportedAsNotFound` | the authorization is read by its key under its account | as expected | Done |
| CDM-40 | BR-03 | `POPULATE-AUTH-DETAILS` 291-359 | `AuthorizationDetailResponse` carries every field of map `COPAU1A` | `getAuthorization` | `AuthorizationApiIntegrationTest.theDetailScreenShowsTheEditedAuthorizationFields`; `AuthorizationDetailScreen.test.tsx` — `shows the edited fields of map COPAU1A` | the source's detail fields | as expected | Done |
| CDM-40 | BR-04 | `POPULATE-AUTH-DETAILS` 297-306 | `AIS.editDate`/`editTime`, leaving the field empty when the stored value is too short to edit | `getAuthorization` | `AuthorizationInquiryServiceTest.detailBuiltFromASegmentWithShortDateTimeAndExpiryLeavesTheMapFieldsEmpty` | `MM/DD/YY` and `HH:MM:SS` | as expected | Done, UCR-23 |
| CDM-40 | BR-05 | `POPULATE-AUTH-DETAILS` 311-317 | `AuthorizationDetailResponse.approvalStatus` from the response code | `getAuthorization` | `AuthorizationApiIntegrationTest.theDetailScreenShowsTheEditedAuthorizationFields` | approved only for `00` | as expected | Done |
| CDM-40 | BR-06 | `POPULATE-AUTH-DETAILS` 319-328 `SEARCH ALL WS-DECLINE-REASON-TAB` | `DR.describe` — the same table, hyphenated for display, `9999-ERROR` for an entry the table does not hold | `getAuthorization` | `DeclineReasonTest.eachTableEntryIsShownWithItsSourceDescription`, `aReasonTheTableDoesNotHoldIsShownAsTheErrorEntry` | the source's reason text, including the not-found entry | as expected | Done |
| CDM-40 | BR-07 | `POPULATE-AUTH-DETAILS` 336-338 | `AIS.editExpiry` — `MM/YY` | `getAuthorization` | `AuthorizationInquiryServiceTest.detailBuiltFromASegmentWithShortDateTimeAndExpiryLeavesTheMapFieldsEmpty` | the card expiry is shown as `MM/YY` | as expected | Done |
| CDM-40 | BR-08 | `POPULATE-AUTH-DETAILS` 344-350 | `FS.describe` — `F-<report date>`, `R-<report date>` or `-` | `getAuthorization` | `FraudStatusTest` (all three states); `AuthorizationApiIntegrationTest.markingAndUnmarkingFraudTogglesTheFlagAndKeepsOneFraudRow` | the source's fraud indicator | as expected | Done |
| CDM-41 | BR-09 | `MARK-AUTH-FRAUD` 236-243 | `FS.toggle` — a confirmed report becomes removed, any other state becomes confirmed | `toggleFraud` | `FraudStatusTest.anUnreportedAuthorizationBecomesConfirmedFraud`, `aConfirmedFraudIsRemoved`, `aRemovedReportIsConfirmedAgain` | marking toggles the fraud state | as expected | Done |
| CDM-41 | BR-10 | `MARK-AUTH-FRAUD` 245-252 `LINK PROGRAM('COPAUS2C')` | `FMS.toggleFraud` writes `auth_fraud` before updating the authorization, in one local transaction instead of a program link | `toggleFraud` | `AuthorizationApiIntegrationTest.markingAndUnmarkingFraudTogglesTheFlagAndKeepsOneFraudRow` | the fraud record is written first | as expected | Done, CMD-13 |
| CDM-41 | BR-11 | `MARK-AUTH-FRAUD` 253-263 | the authorization update and the fraud row share one `@Transactional` method, so neither can be applied without the other | `toggleFraud` | same test | the authorization is updated only if the fraud record was | as expected | Done, CMD-13 |
| CDM-41 | BR-12 | `MARK-AUTH-FRAUD` 260-263 (`ROLLBACK`) | an exception rolls the single transaction back | `toggleFraud` | `FraudMarkingServiceTest.aSegmentWhoseComplementedTimeIsAbsentIsRejectedRatherThanTimestampedWrongly` | a failure leaves nothing applied | as expected | Done, CMD-13 |
| CDM-41 | BR-13 | `UPDATE-AUTH-DETAILS` 531-538 | `AM.FRAUD_MARKED` / `AM.FRAUD_REMOVED` returned with the updated detail | `toggleFraud` | `AuthorizationApiIntegrationTest.markingAndUnmarkingFraudTogglesTheFlagAndKeepsOneFraudRow`; `AuthorizationDetailScreen.test.tsx` — `marks the authorization as fraud and shows the message the service returns` | the source's marking messages | as expected | Done |
| CDM-41 | BR-14 | `UPDATE-AUTH-DETAILS` 539-550 | `FMS` raises on a missing authorization and the transaction rolls back | `toggleFraud` | `FraudMarkingServiceTest.anAuthorizationThatIsNoLongerThereReturnsTheSourceMessage` | a failed update is reported and rolled back | as expected | Done |
| CDM-41 | BR-15 | `UPDATE-AUTH-DETAILS` 531-532, `TAKE-SYNCPOINT` | the transaction commits when `FMS.toggleFraud` returns | `toggleFraud` | `AuthorizationApiIntegrationTest.markingAndUnmarkingFraudTogglesTheFlagAndKeepsOneFraudRow` | successful marking is committed | as expected | Done |
| CDM-40 | BR-16 | `PROCESS-PF8-KEY` 268-289, `READ-NEXT-AUTH-RECORD` | `AIS.detail` returns `nextAuthKey` and the screen requests it | `getAuthorization` | `AuthorizationInquiryServiceTest.theFraudMessageIsReturnedWithTheKeyOfTheFollowingAuthorization`; `AuthorizationDetailScreen.test.tsx` — `reads the next authorization of the chain, as PF8 did` | the next authorization of the account can be shown | as expected | Done |
| CDM-40 | BR-17 | `PROCESS-PF8-KEY` 281-286 | `AM.NO_MORE_AUTHORIZATIONS` when no following authorization exists | `getAuthorization` | `AuthorizationApiIntegrationTest.theOldestAuthorizationReportsThatItIsTheLastOne`; `AuthorizationDetailScreen.test.tsx` — `reports the end of the chain instead of reading past it` | the source's end-of-chain message | as expected | Done |
| CDM-41 | BR-18 | `MARK-AUTH-FRAUD` 264-266 | `FMS.toggleFraud` returns the re-read detail, so the screen shows the stored state | `toggleFraud` | `AuthorizationApiIntegrationTest.markingAndUnmarkingFraudTogglesTheFlagAndKeepsOneFraudRow` | the screen is refreshed from the database after marking | as expected | Done |
| CDM-40 | BR-19 | `PROCESS-ENTER-KEY` 219-222, `SEND-AUTHVIEW-SCREEN` | retired: the read-only request ends its own transaction | `getAuthorization` | — | held read resources are released when the screen is sent | platform mechanism, not reproduced | Done, CMD-10 |
| CDM-40 | BR-20 | `MAIN-PARA` 165-170 | the detail route is reachable only with an account id and a key; without them the UI stays on the summary screen | n/a | `AuthorizationDetailScreen.test.tsx` | entering with no context returns to the summary | as expected | Done |
| CDM-40 | BR-21 | `MAIN-PARA` 193-198 | retired PF-key mechanism, as COPAUS0C BR-29 | n/a | — | an action the screen does not offer is refused | platform mechanism, not reproduced | Done, UCR-24 |

---

## 11. COPAUS2C — fraud report recording (DB2 `AUTHFRDS`)

Target: `FMS` (the source program is a linked-to module, consolidated into the fraud marking service).
PostgreSQL object: `auth_fraud`. API: `toggleFraud`.

| Ticket | Rule | Source location | Target | API | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-41 | BR-01 | `MAIN-PARA` 91-101 `FORMATTIME MMDDYY` | `FMS` stamps the authorization's fraud report date from the injected `Clock` | `toggleFraud` | `AuthorizationApiIntegrationTest.markingAndUnmarkingFraudTogglesTheFlagAndKeepsOneFraudRow` | the report date is the current date | as expected | Done, UCR-23 |
| CDM-41 | BR-02 | `MAIN-PARA` 103-112, `ddl/XAUTHFRD.ddl` | `FraudReportId` (card number + authorization timestamp) as the primary key of `auth_fraud` | `toggleFraud` | `AuthorizationPostgresIntegrationTest`, same API test | one fraud row per card and authorization | as expected | Done |
| CDM-41 | BR-03 | `MAIN-PARA` 107 `999999999 - PA-AUTH-TIME-9C` | `AK.fraudTimestamp` | `toggleFraud` | `AuthorizationKeyTest.theFraudTimestampCombinesTheOriginalDateAndTheUncomplementedTime`, `aFraudTimestampNeedsASixDigitOriginalDate` | the stored timestamp is the real authorization time | as expected | Done |
| CDM-41 | BR-04 | `MAIN-PARA` 113-197 | `FraudReportEntity` — every `AUTHFRDS` column mapped from the authorization | `toggleFraud` | `FraudMarkingServiceTest.aSuppliedPosEntryModeIsCarriedAsTheNumericHostVariableOfTheSource`, `aSegmentWithoutAPosEntryModeWritesTheFraudRowWithTheNullHostVariable` | the source's fraud row content, including the nullable POS entry mode | as expected | Done |
| CDM-41 | BR-05 | `MAIN-PARA` 194 `,CURRENT DATE` | `auth_fraud.fraud_rpt_date` set from the same clock as the authorization's flag, so the two never disagree | `toggleFraud` | `AuthorizationApiIntegrationTest.markingAndUnmarkingFraudTogglesTheFlagAndKeepsOneFraudRow` | the row carries the date the report was taken | as expected — the source read the database's date, the target the application's | Done |
| CDM-41 | BR-06 | `MAIN-PARA` 141-200 (`SQLCODE = ZERO`) | `FraudReportRepository.save` insert path | `toggleFraud` | same test | a first report is inserted | as expected | Done |
| CDM-41 | BR-07 | `MAIN-PARA` 203-206 (`SQLCODE = -803`), `FRAUD-UPDATE` | `FraudReportRepository.save` update path on the existing key — the duplicate-key retry of the source becomes an upsert on the same primary key | `toggleFraud` | `AuthorizationApiIntegrationTest.markingAndUnmarkingFraudTogglesTheFlagAndKeepsOneFraudRow` (one row after marking, unmarking and re-marking) | a repeated report updates the existing row | as expected | Done |
| CDM-41 | BR-08 | `MAIN-PARA` 207-215, `FRAUD-UPDATE` 234-243 | any other persistence failure propagates and rolls the fraud transaction back | `toggleFraud` | `FraudMarkingServiceTest.aSegmentWhoseComplementedTimeIsAbsentIsRejectedRatherThanTimestampedWrongly` | a database failure is a failure of the marking | as expected | Done |
| CDM-41 | BR-09 | no `SYNCPOINT` in the program | `FMS` owns the single transaction that covers both the fraud row and the authorization flag | `toggleFraud` | `AuthorizationApiIntegrationTest.markingAndUnmarkingFraudTogglesTheFlagAndKeepsOneFraudRow` | the caller decides when the work commits | as expected | Done, CMD-13 |

---

## 12. CBPAUP0C under CBPAUP0J — expired authorization purge

Batch job/step: `purgeExpiredAuthorizationsJob` / `purgeExpiredAuthorizationsStep` (`PAJC`).
PostgreSQL objects: `pending_auth_summary`, `pending_auth_detail`. React component: n/a.

| Ticket | Rule | Source location | Target | Batch step | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-43 | BR-01 | `1000-INITIALIZE` `ACCEPT PRM-INFO FROM SYSIN`, `CBPAUP0J` `SYSIN DD *` | `APP` bound from `carddemo.batch.authpurge.*`, overridable on the command line as the SYSIN card was | job parameters | `AuthPurgePropertiesTest.aSuppliedFrequencyIsUsedAsGiven` | run parameters come from the job's configuration | as expected | Done |
| CDM-43 | BR-02 | `1000-INITIALIZE` 198-202 | `APP.expiryDays` defaults to 5 when the value is unusable | job parameters | `AuthPurgePropertiesTest.anUnusableCheckpointFrequencyFallsBackToTheDefaultsOfTheSource` | retention defaults to five days | as expected | Done |
| CDM-43 | BR-03 | `1000-INITIALIZE` 203-205 | `APP.checkpointFrequency` defaults to 5 | chunk size | same test | checkpoint frequency defaults to five accounts | as expected | Done |
| CDM-43 | BR-04 | `1000-INITIALIZE` 206-208 | `APP.checkpointDisplayFrequency` defaults to 10 | reporting | same test | checkpoint reporting defaults to every tenth checkpoint | as expected | Done |
| CDM-43 | BR-05 | `1000-INITIALIZE` 209-211 | `APP.debug` is on only when explicitly requested | logging | same test | debug reporting is off unless asked for | as expected | Done |
| CDM-43 | BR-06 | `MAIN-PARA` 138-167, `2000-FIND-NEXT-AUTH-SUMMARY` `GN`, `3000-FIND-NEXT-AUTH-DTL` `GNP` | `ASKR` walks the roots in key order and `EAP` reads each root's children | reader/processor | `PurgeAuthJobIntegrationTest.expiredChildrenArePurgedAndTheApprovedTotalsAreReversed`, `AuthSummaryKeysetReaderTest.aFirstRunStartsAtTheTopOfTheDatabaseAndEndsWhenTheWalkIsExhausted` | every account's authorizations are examined once | as expected | Done |
| CDM-43 | BR-07 | `2000-FIND-NEXT-AUTH-SUMMARY` `WHEN 'GB'` | `ASKR` returns `null` when the keyset walk is exhausted, ending the step | reader | `AuthSummaryKeysetReaderTest.aFirstRunStartsAtTheTopOfTheDatabaseAndEndsWhenTheWalkIsExhausted` | the run ends at the end of the database | as expected | Done |
| CDM-43 | BR-08 | `2000-FIND-NEXT-AUTH-SUMMARY` 233-238, `9999-ABEND` | an unexpected read failure propagates: the step fails and the job's exit status is `FAILED`, replacing return code 16 | reader | — (no fault injected; the abend path is exercised only by Spring Batch's own failure handling) | a read failure ends the run abnormally | not exercised by a test | Done, UCR-25 |
| CDM-43 | BR-09 | `3000-FIND-NEXT-AUTH-DTL` `WHEN 'GE' WHEN 'GB'` | `EAP` finishing a root's children moves on to the next root | processor | `PurgeAuthJobIntegrationTest.expiredChildrenArePurgedAndTheApprovedTotalsAreReversed` | the end of an account's authorizations moves on | as expected | Done |
| CDM-43 | BR-10 | `3000-FIND-NEXT-AUTH-DTL` 267-272 | as BR-08, for the child read | processor | — | an authorization read failure ends the run abnormally | not exercised by a test | Done, UCR-25 |
| CDM-43 | BR-11 | `4000-CHECK-IF-EXPIRED` 280-282 | `EAP.isExpired` — `currentYyddd - (99999 - authDate9c)`, the source's Julian-day subtraction | processor | `ExpiredAuthorizationProcessorTest.anAuthorizationInsideTheRetentionWindowIsLeftAloneAndItsRootIsKept`, `PurgeAuthJobIntegrationTest.anAuthorizationInsideTheExpiryWindowIsKept` | age is the difference of the two Julian days | as expected; wrong across a year boundary, as in the source | Done, UCR-25 |
| CDM-43 | BR-12 | `4000-CHECK-IF-EXPIRED` 284-297 | `EAP` purges an authorization whose age is at least the retention period | processor | `PurgeAuthJobIntegrationTest.expiredChildrenArePurgedAndTheApprovedTotalsAreReversed`, `anAuthorizationInsideTheExpiryWindowIsKept` | at or beyond retention is purged | as expected | Done |
| CDM-43 | BR-13 | `4000-CHECK-IF-EXPIRED` 287-289 | `EAP` reverses approved count and approved amount for an expired `00` authorization | processor | `PurgeAuthJobIntegrationTest.expiredChildrenArePurgedAndTheApprovedTotalsAreReversed` | approved credit is released | as expected | Done |
| CDM-43 | BR-14 | `4000-CHECK-IF-EXPIRED` 290-292 | `EAP` reverses declined count and declined amount otherwise | processor | `PurgeAuthJobIntegrationTest.anExpiredDeclinedAuthorizationReversesTheDeclinedTotals` | declined totals are released | as expected | Done |
| CDM-43 | BR-15 | `5000-DELETE-AUTH-DTL` | `APW` deletes the expired children | writer | `AuthPurgeWriterTest.aRootWhoseApprovedAuthorizationsAllExpiredIsDeletedWithItsWholeHierarchy`, `withoutAStepExecutionTheDeletesStillHappenAndNoCountersAreKept` | purged authorizations are deleted | as expected | Done |
| CDM-43 | BR-16 | `MAIN-PARA` 156-158, `6000-DELETE-AUTH-SUMMARY` | `EAP` reproduces the source's duplicated approved-count condition, including that the declined counter is never consulted | processor/writer | `PurgeAuthJobIntegrationTest.aRootIsDeletedOnTheApprovedCounterAloneAsTheSourceDoes`, `AuthPurgeWriterTest.aRootThatKeepsChildrenHasItsCountersRewrittenInTheRemediatedMode` | an account with no approved authorizations left loses its summary | as expected | Done, UCR-26 |
| CDM-43 | BR-17 | absence of `REPL SEGMENT(PAUTSUM0)` | **deliberate delta**: `APW` writes the reversals back by default (`remediate-counters: true`); the AS-IS behaviour of discarding them is retained behind the same flag | writer | `AuthPurgeWriterTest.theAsIsModeDiscardsTheCounterReversalsTheSourceNeverRewrote`, `aRootThatKeepsChildrenHasItsCountersRewrittenInTheRemediatedMode`, `ExpiredAuthorizationProcessorTest.aSummaryWithUnpopulatedCountersAndAnUnpopulatedDetailDateReversesFromZero` | released credit becomes available again | remediated, with the AS-IS mode preserved and tested | Done, UCR-27 |
| CDM-43 | BR-18 | `MAIN-PARA` 160-164, `9000-TAKE-CHECKPOINT` `EXEC DLI CHKP` | chunk size = `APP.checkpointFrequency`, and `ASKR` saves the last root in the execution context | step | `PurgeAuthJobIntegrationTest.rootsAreCommittedAtTheConfiguredCheckpointFrequency`, `AuthSummaryKeysetReaderTest.aRestartResumesAtTheRootAfterTheLastCheckpointedOne` | work is committed every *n* accounts and a restart resumes there | as expected | Done, CMD-15 |
| CDM-43 | BR-19 | `WK-CHKPT-ID` (`'RMAD'`) | `ASKR` records the checkpoint id prefix `RMAD` with the sequence in the execution context | step | `AuthSummaryKeysetReaderTest.aRestartResumesAtTheRootAfterTheLastCheckpointedOne` | checkpoints are identifiable as this job's | as expected | Done |
| CDM-43 | BR-20 | `9000-TAKE-CHECKPOINT` 359-365 | `APW` logs a checkpoint line every `checkpointDisplayFrequency` checkpoints | step | `PurgeAuthJobIntegrationTest.rootsAreCommittedAtTheConfiguredCheckpointFrequency`; `evidence/authorization-purge-batch-run.txt` | checkpoints are reported periodically | as expected | Done |
| CDM-43 | BR-21 | `9000-TAKE-CHECKPOINT` 366-371 | a commit failure fails the chunk and the job, replacing return code 16 | step | — | a checkpoint failure ends the run abnormally | not exercised by a test | Done |
| CDM-43 | BR-22 | `MAIN-PARA` 170 | the final chunk commits when the step completes | step | `PurgeAuthJobIntegrationTest.reRunningTheJobPurgesNothingFurther` | the last partial unit of work is committed | as expected | Done, CMD-15 |
| CDM-43 | BR-23 | `MAIN-PARA` 172-180 | `PAJC` job-completion listener prints the source's five report lines | job | `evidence/authorization-purge-batch-run.txt` | totals read, deleted and adjusted are reported | as expected | Done |
| CDM-43 | BR-24 | `IF DEBUG-ON` blocks | `APP.debug` raises the same per-segment lines to `DEBUG` logging | job | `AuthPurgePropertiesTest.anUnusableCheckpointFrequencyFallsBackToTheDefaultsOfTheSource` | debug output is available on request | as expected | Done |

---

## 13. PAUDBLOD / PAUDBUNL / DBUNLDGS and the IMS database definitions

Target: the data-migration pipeline (`tools/ims_authorization_unload.py`,
`tools/generate_authorization_seed_sql.py`, Flyway `V1`/`V2` of `carddemo_authorization`,
`tools/reconcile_authorization_data.py`). No runtime component: these are one-off utilities.

| Ticket | Rule | Source location | Target | Migration object | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-37 | BR-01 | `PAUDBLOD` `MAIN-PARA` 175-181 | the generated seed inserts `pending_auth_summary` before `pending_auth_detail` — the foreign key enforces what the source's ordering assumed | `V2__load_authorization_sample_data.sql` | `evidence/authorization-data-reconciliation.txt` | summaries are loaded before authorizations | as expected | Done |
| CDM-37 | BR-02 | `PAUDBLOD` `1000-INITIALIZE` 200-215 | `ims_authorization_unload.py` fails the run if either supplied unload file is missing or unreadable | pipeline | `evidence/authorization-data-reconciliation.txt` | the load does not start without its inputs | as expected | Done |
| CDM-37 | BR-03 | `PAUDBLOD` `2000-READ-ROOT-SEG-FILE`, `CIPAUSMY` | `SUMMARY_LAYOUT` in `ims_authorization_unload.py` — the `CIPAUSMY` picture clauses, COMP-3 fields decoded from packed decimal | `pending_auth_summary` | `AuthorizationPostgresIntegrationTest`, `evidence/authorization-data-reconciliation.txt` (231 summary field comparisons) | the summary record layout of the source | as expected | Done |
| CDM-37 | BR-04 | `PAUDBLOD` `3000-READ-CHILD-SEG-FILE`, `CIPAUDTY` | `DETAIL_LAYOUT` — the `CIPAUDTY` picture clauses, with the account key taken from the preceding root | `pending_auth_detail` | `evidence/authorization-data-reconciliation.txt` (3030 detail field comparisons) | the authorization record layout of the source | as expected | Done |
| CDM-37 | BR-05 | `PAUDBLOD` 275-284 (`IF ROOT-SEG-KEY IS NUMERIC`, no `ELSE`) | **not carried forward silently**: the decoder rejects the record, reports it and continues, instead of discarding it without trace | pipeline | `evidence/authorization-data-reconciliation.txt` (`known supplied-data defect (UCR-31)`) | a record whose account key is not numeric is not loaded | reported instead of silently skipped | Done, UCR-28, UCR-31 |
| CDM-37 | BR-06 | `PAUDBLOD` `3100`/`3200-INSERT-…` | the child insert carries the root's account id, enforced by the foreign key | `pending_auth_detail` | `AuthorizationPostgresIntegrationTest` | an authorization is loaded beneath its account | as expected | Done |
| CDM-37 | BR-07 | `PAUDBLOD` `'II'` status handling | a duplicate key in the generated seed would fail the migration rather than be reported and skipped; the generator emits each key once | `V2__…sql` | `evidence/authorization-data-reconciliation.txt` (row counts match the decoded segments exactly) | a record already present is not loaded twice | as expected — reported at generation time instead of at load time | Done, UCR-28 |
| CDM-37 | BR-08 | `PAUDBLOD` `9999-ABEND` | Flyway aborts the migration and leaves the schema unchanged on any other load failure | `V2__…sql` | `evidence/authorization-data-reconciliation.txt` (the NUL-character failure of AF-27 aborted the migration) | any other load failure ends the run abnormally | as expected | Done |
| CDM-37 | BR-09 | `PAUDBLOD` file status `'10'` | the decoder ends each phase at end of file | pipeline | `evidence/authorization-data-reconciliation.txt` | end of an input file ends that phase | as expected | Done |
| CDM-37 | BR-10 | `PAUDBUNL`/`DBUNLDGS` `2000`/`3000` | `ims_authorization_unload.py` walks the supplied unload in hierarchic order, pairing each root with the children that follow it | pipeline | `evidence/authorization-data-reconciliation.txt` (21 roots, 202 children, 21 accounts with children) | the whole hierarchy is read | as expected | Done |
| CDM-37 | BR-11 | `PAUDBUNL` `4000-FILE-CLOSE`, `DBUNLDGS` `3100`/`3200` | the decoder emits summaries and authorizations as two separate record sets, as the two output files did | pipeline | same evidence | summaries and authorizations stay separate | as expected | Done |
| CDM-37 | BR-12 | `PAUDBUNL`/`DBUNLDGS` `EVALUATE DIBSTAT` | the decoder ends at the end of the unload and reports any segment name it does not recognise | pipeline | same evidence | the unload ends at the end of the database | as expected | Done |
| CDM-37 | BR-13 | `ims/DBPAUTP0.dbd`, `ims/DBPAUTX0.dbd` | `pending_auth_summary` keyed by `acct_id`, `pending_auth_detail` keyed by (`acct_id`, `auth_key`) with `ON DELETE CASCADE`; the HIDAM primary index is the primary-key b-tree and needs no separate object | `V1__create_authorization_schema.sql` | `AuthorizationPostgresIntegrationTest`, `evidence/authorization-data-reconciliation.txt` | a hierarchy of accounts and their authorizations, keyed as the DBD defines | as expected | Done, UCR-29 |

---

## 14. Non-rule tickets (authorization closure)

| Ticket | Deliverable | Evidence | Status |
|---|---|---|---|
| CDM-34 | Authorization source inventory, coverage and dependency closure | `02-source-inventory-and-coverage.md`, `13-artifact-disposition.md` | Done |
| CDM-35 | Business rule extraction — 135 rules over six catalogues | `BRD-COPAUA0C.md`, `BRD-COPAUS0C.md`, `BRD-COPAUS1C.md`, `BRD-COPAUS2C.md`, `BRD-CBPAUP0C.md`, `BRD-PAUDBLOD-PAUDBUNL-DBUNLDGS.md`, `BRE-Report-CARDDEMO-AUTHORIZATION.md` | Done |
| CDM-36 | Authorization bounded context and domain module | `authorization-domain/**`, `04-architecture.md` §7 | Done |
| CDM-37 | Authorization schema, EBCDIC migration and reconciliation | `V1`/`V2` of `authorization-service`, `tools/*authorization*.py`, `05-data-mapping.md`, `evidence/authorization-data-reconciliation.txt` | Done |
| CDM-38 | MQ → Kafka conversion of COPAUA0C | `messaging/**`, `config/KafkaConfig.java`, `23-messaging-modernization-mapping.md` | Done |
| CDM-39 | Authorization decision engine | `AuthorizationDecisionEngine`, `DeclineReason`, `AuthorizationDecision` | Done |
| CDM-40 | Summary and detail APIs and screens | `openapi/openapi.yaml`, `api/**`, `AuthorizationSummaryScreen.tsx`, `AuthorizationDetailScreen.tsx`, `06-screen-mapping.md` §8 | Done |
| CDM-41 | Fraud marking | `FraudMarkingService`, `FraudReportEntity`, `toggleFraud` | Done |
| CDM-42 | Authorization concurrency and idempotency controls | `AuthorizationRequestLogEntity`, `AuthorizationReplyOutboxEntity`, `12-consistency-model-delta-register.md` CMD-10 … CMD-15 | Done |
| CDM-43 | Purge job conversion | `batch-auth-purge/**`, `07-batch.md` §7, `evidence/authorization-purge-batch-run.txt` | Done |
| CDM-44 | Gateway, Compose, Helm, Dockerfile and CI extension | `gateway/src/main/resources/application.yml`, `docker-compose.yml`, `deploy/helm/carddemo/**`, `.github/workflows/modernization-ci.yml` | Done |
| CDM-45 | Authorization test suite and evidence | 371 backend tests, 42 frontend tests, `evidence/authorization-*.txt` | Done |
| CDM-46 | Section 8.12 operational artifacts | `18-coding-standards-checklist.md` … `24-dr-bcp.md` | Done |

---

## 15. Coverage of Part B

135 of 135 extracted authorization rules are traced.

- 122 rows cite at least one executed test; 8 cite a captured evidence log
  (`evidence/authorization-data-reconciliation.txt`, `evidence/authorization-purge-batch-run.txt`)
  as their only proof.
- 8 rows are **retired platform mechanisms** and say so rather than claiming a test: the MQ wait
  interval and open/close, the 500-request task cap, PSB scheduling, the two read-only syncpoints and
  the two invalid-PF-key paths. Their business intent is either preserved by the platform (consumer
  groups, listener lifecycle, request-scoped transactions) or unreachable in the target's action set.
- 3 rows are **not exercised by a test**: the two IMS read-failure abend paths and the checkpoint
  failure path of `CBPAUP0C` (BR-08, BR-10, BR-21), where the source condition is an IMS status code
  that has no equivalent to inject. Spring Batch's own failure handling covers the outcome.
- 3 rows are **deliberate deltas**, each recorded in `11-unsupported-construct-register.md` and in
  the parity evidence: `COPAUA0C` BR-25 (reply after commit, CMD-11), `COPAUA0C` BR-29 (declined
  total, UCR-30) and `CBPAUP0C` BR-17 (counter reversals persisted, UCR-27).
- 1 row is implemented as a **report rather than a silent skip**: `PAUDBLOD` BR-05 (UCR-28, UCR-31).
- Every REVIEW REQUIRED marker resolves to an entry in `11-unsupported-construct-register.md` or
  `12-consistency-model-delta-register.md` and is listed in `17-limitations-and-review-required.md`.

---

# Part C — Transactions, Cards, Statements and Branch Migration

The closure that converts the remaining twenty programs. The rules are held per program in
`BRD-COTRN00C-01C-02C.md`, `BRD-COBIL00C.md`, `BRD-CORPT00C.md`, `BRD-COCRDLIC-SLC-UPC.md`,
`BRD-CBTRN01C-02C-03C.md`, `BRD-CBACT04C.md`, `BRD-CBACT02C-CBACT03C-CBCUS01C.md`,
`BRD-CBSTM03A-CBSTM03B.md` and `BRD-CBEXPORT-CBIMPORT-COBSWAIT.md`. Rows here are grouped by the
behaviour they trace, because each group is proved by the same test; a group cites every rule it
covers, so every extracted rule appears exactly once.

Package prefix `com.carddemo` is omitted. `BPS` = `service.BillPaymentService`,
`TLS` = `service.TransactionListService`, `TVS` = `service.TransactionViewService`,
`TAS` = `service.TransactionAddService`, `RRS` = `service.ReportRequestService`,
`CLS` = `service.CardListService`, `CDS` = `service.CardDetailService`,
`CUS` = `service.CardUpdateService`, `BAS` = `service.BalanceAdjustmentService`.

## 16. COTRN00C / COTRN01C / COTRN02C — transaction list, view and add (CT00, CT01, CT02)

React components: `TransactionListScreen.tsx`, `TransactionViewScreen.tsx`,
`TransactionAddScreen.tsx`. PostgreSQL objects: `transaction`, `card_xref`. Batch: n/a.

| Ticket | Rules | Source location | Target | API | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-50 | COTRN00C BR-01 … BR-04 | `0000-MAIN`, `2000-PROCESS-INPUTS` | `TLS.first`, `.next`, `.previous` | `GET /api/transactions` | `TransactionListServiceTest`, `TransactionApiIntegrationTest` | ten rows a page, ordered by transaction id, forward and backward paging | as expected | Done |
| CDM-50 | COTRN00C BR-05 … BR-09 | `2100-EDIT-INPUTS`, `9000-READ-FORWARD` | `TLS` filter and selection handling | `GET /api/transactions` | `TransactionListServiceTest` | the id filter positions the browse; `S` selects one row; only one row may be selected | as expected | Done |
| CDM-50 | COTRN00C BR-10 … BR-13 | `SEND-TRNLST-SCREEN`, `9100-READ-BACKWARD` | `TLS` message and boundary handling | `GET /api/transactions` | `TransactionListServiceTest`, `TransactionListScreen.test.tsx` | empty result, first-page and last-page wording preserved | as expected | Done |
| CDM-50 | COTRN00C BR-14 … BR-16 | `0000-MAIN` `EVALUATE EIBAID` | the screen's action set | `GET /api/transactions` | `TransactionListScreen.test.tsx` | exit, paging and selection are the only actions; an unknown key is reported | as expected | Done |
| CDM-51 | COTRN01C BR-01 … BR-11 | `0000-MAIN`, `9000-READ-TRANSACT`, `1000-SEND-MAP` | `TVS.view` | `GET /api/transactions/{tranId}` | `TransactionViewServiceTest`, `TransactionViewScreen.test.tsx` | a 16-character id is required, the record is displayed field for field, not-found and fatal wording preserved | as expected | Done |
| CDM-52 | COTRN02C BR-01 … BR-11 | `2000-EDIT-INPUTS` | `TAS.add` field editors | `POST /api/transactions` | `TransactionAddServiceTest`, `TransactionAddScreen.test.tsx` | account or card is required, amount, dates, merchant fields validated in source order with source messages | as expected | Done |
| CDM-52 | COTRN02C BR-12 … BR-16 | `WRITE-TRANSACT-FILE`, `2000-…` confirmation | `TAS.add` | `POST /api/transactions` | `TransactionAddServiceTest`, `TransactionApiIntegrationTest` | the id is the highest existing id plus one; the confirmation step and its wording are preserved | as expected | Done |

## 17. COBIL00C — bill payment (CB00)

React component: `BillPaymentScreen.tsx`. PostgreSQL objects: `bill_payment`, `transaction`;
`account` through the account service. Batch: n/a.

| Ticket | Rules | Source location | Target | API | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-53 | BR-01 … BR-05 | `2000-PROCESS-ENTER-KEY`, `READ-ACCTDAT-FILE` | `BPS.balance` | `GET /api/bill-payments/{accountId}` | `BillPaymentServiceTest`, `BillPaymentScreen.test.tsx` | an account id is required and must exist; the balance is shown; a zero or negative balance is refused with the source wording | as expected | Done |
| CDM-53 | BR-06 … BR-12 | `EVALUATE CONFIRMI`, `WRITE-TRANSACT-FILE` | `BPS.pay` | `POST /api/bill-payments` | `BillPaymentServiceTest` | `Y`/`N`/blank/other behave as in the source; the payment is the whole balance, classified `02`/`2`/`POS TERM` with the placeholder merchant | as expected (AS-IS literals) | Done, **R** UCR-07 |
| CDM-53 | BR-13, BR-14 | `UPDATE-ACCTDAT-FILE` + task syncpoint | `BAS.adjust` `BILL_PAYMENT` with the payment's idempotency key | `POST /api/accounts/{id}/balance-adjustments` | `BillPaymentServiceTest`, `TransactionApiIntegrationTest` | the balance is reduced by the payment, once, atomically with the transaction | compensated, not one commit | Done, CMD-16 |
| CDM-53 | BR-15 … BR-18 | `CLEAR-CURRENT-SCREEN`, `0000-MAIN` | the screen's action set | n/a | `BillPaymentScreen.test.tsx` | clear, exit, invalid key and empty-state behaviour preserved | as expected | Done |

## 18. CORPT00C — transaction report request (CR00)

React component: `ReportRequestScreen.tsx`. PostgreSQL object: `report_request`. Batch:
`transactionReportJob` consumes the request.

| Ticket | Rules | Source location | Target | API | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-54 | BR-01 … BR-06 | `2000-PROCESS-ENTER-KEY` monthly/yearly branches | `RRS.submit` range resolution | `POST /api/transaction-reports` | `ReportRequestServiceTest` | monthly is the current calendar month including the December year rollover; yearly is the current calendar year | as expected | Done |
| CDM-54 | BR-07 … BR-09 | custom branch, `CSUTLDTC` call | `RRS.submit` custom-range validation | `POST /api/transaction-reports` | `ReportRequestServiceTest`, `ReportRequestScreen.test.tsx` | every date part is required, numeric and valid by the shared date service | as expected | Done |
| CDM-54 | BR-10 … BR-12 | `EVALUATE CONFIRMI`, `SUBMIT-JOB-TO-INTRDR` | `RRS.submit` persisting a `report_request` | `POST /api/transaction-reports` | `ReportRequestServiceTest`, `TransactionBatchIntegrationTest` | the confirmed request is recorded with its resolved range and the operator is told it was submitted | recorded, not spooled | Done, CMD-17 |
| CDM-54 | BR-13, BR-14 | `0000-MAIN` `EVALUATE EIBAID` | the screen's action set | n/a | `ReportRequestScreen.test.tsx` | exit and invalid-key behaviour preserved | as expected | Done |

## 19. COCRDLIC / COCRDSLC / COCRDUPC — card list, view and update (CCLI, CCDL, CCUP)

React components: `CardListScreen.tsx`, `CardViewScreen.tsx`, `CardUpdateScreen.tsx`. PostgreSQL
objects: `card`, `card_xref`.

| Ticket | Rules | Source location | Target | API | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-55 | COCRDLIC BR-01 … BR-08 | `9000-READ-FORWARD`, `1200-EDIT-*` | `CLS.list` | `GET /api/cards` | `CardListServiceTest`, `CardListScreen.test.tsx` | seven rows a page in card-number order, account and card filters validated, asterisk clears a filter | as expected | Done |
| CDM-55 | COCRDLIC BR-09 … BR-14 | `1000-SEND-MAP`, selection edits | `CLS` selection and message handling | `GET /api/cards` | `CardListServiceTest` | `S`/`U` select one row only; empty result and paging wording preserved | as expected | Done |
| CDM-56 | COCRDSLC BR-01 … BR-08 | `9000-READ-DATA`, `1000-SEND-MAP` | `CDS.view` | `GET /api/cards/{cardNumber}` | `CardDetailServiceTest`, `CardViewScreen.test.tsx` | the card is displayed with its account; the security code is held but not sent to the screen | as expected | Done |
| CDM-57 | COCRDUPC BR-01 … BR-09 | `1200-EDIT-MAP-INPUTS` | `CUS.validate` | `POST /api/cards/validate` | `CardUpdateServiceTest`, `CardUpdateScreen.test.tsx` | key fields required; name alphabetic; status `Y`/`N`; expiry month and year validated with the source messages | as expected | Done |
| CDM-57 | COCRDUPC BR-10 … BR-14 | `9600-WRITE-PROCESSING` | `CUS.save` with the fetch-time baseline | `POST /api/cards` | `CardUpdateServiceTest` | no-change is reported not written; F5 confirms; the record is locked and re-compared before the write | as expected | Done, CMD-03/CMD-04 |
| CDM-57 | COCRDUPC BR-15 … BR-17 | `9600`, `0000-MAIN` | `CUS` outcome messages and the screen's action set | `POST /api/cards` | `CardUpdateServiceTest`, `CardUpdateScreen.test.tsx` | success, concurrent-change and cancel wording preserved | as expected | Done |

## 20. CBTRN01C / CBTRN02C / CBTRN03C under POSTTRAN and TRANREPT — daily transaction batch

PostgreSQL objects: `daily_transaction`, `transaction`, `transaction_category_balance`,
`report_request`; `account` through the account service. Jobs: `verifyDailyTransactionsJob`,
`postTransactionsJob`, `transactionReportJob`.

| Ticket | Rules | Source location | Target | Job/step | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-58 | CBTRN01C BR-01 … BR-07 | `PROCEDURE DIVISION` loop, `2000-LOOKUP-XREF`, `3000-READ-ACCOUNT` | `VerifyDailyTransactionsJobConfig` | `verifyDailyTransactionsJob` | `TransactionBatchIntegrationTest` | every daily transaction is verified against the cross-reference and the account, and each failure is reported with the source wording | as expected | Done |
| CDM-59 | CBTRN02C BR-01 … BR-08 | `1500-VALIDATE-TRAN` | `DailyTransactionValidator` | `postTransactionsJob` | `DailyTransactionValidatorTest` | reasons 100 … 103 with their source descriptions, including 103 overwriting 102 | as expected (AS-IS) | Done |
| CDM-59 | CBTRN02C BR-09 … BR-14 | `2000-POST-TRANSACTION`, `2500-WRITE-REJECT-REC` | `PostedTransactionWriter` | `postTransactionsJob` | `PostedTransactionWriterTest`, `TransactionBatchIntegrationTest` | the transaction is written, the category balance increased and the account balance and cycle amounts updated; rejects are written with their reason | account update is a compensated remote call | Done, CMD-18 |
| CDM-60 | CBTRN03C BR-01 … BR-08, BR-10, BR-11 | `1100-WRITE-TRANSACTION-REPORT` and the total paragraphs | `TransactionReportWriter`, `ReportLines` | `transactionReportJob` | `TransactionReportWriterTest`, `ReportLinesTest` | 133-character lines, twenty to a page, page, account and grand totals, type and category descriptions | as expected | Done |
| CDM-60 | CBTRN03C BR-09 | EOF branch adding `TRAN-AMT` again | omitted in `TransactionReportWriter` | `transactionReportJob` | `TransactionReportWriterTest` | source overstates the closing totals by the last amount | **deliberately corrected** | Done, **R** UCR-33 |

## 21. CBACT04C under INTCALC — monthly interest calculation

PostgreSQL objects: `transaction_category_balance`, `disclosure_group`, `transaction`; `account`
through the account service. Job: `interestCalculationJob`.

| Ticket | Rules | Source location | Target | Job/step | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-61 | BR-01 … BR-06 | `PROCEDURE DIVISION USING EXTERNAL-PARMS`, the key-break logic | `InterestCalculationJobConfig`, `InterestAccrualWriter` | `interestCalculationStep` | `InterestAccrualWriterTest` | the run date is a parameter; balances are read in account order and each account is settled at the key break | as expected | Done |
| CDM-61 | BR-07 … BR-11 | `1200-GET-INTEREST-RATE`, `1300-COMPUTE-INTEREST` | `InterestAccrualWriter.interestRate`, `.accrue` | `interestCalculationStep` | `InterestAccrualWriterTest` | the group rate with the `DEFAULT` fallback; interest is balance × rate ÷ 1200, truncated; a zero rate accrues nothing | as expected | Done |
| CDM-61 | BR-12 … BR-15 | `1300-B-WRITE-TX` | `InterestAccrualWriter.interestTransaction` | `interestCalculationStep` | `InterestAccrualWriterTest`, `TransactionBatchIntegrationTest` | one transaction per interest amount, id = run date + sequence, type `01`, category `5`, source `System` | as expected (AS-IS literals) | Done, **R** UCR-07 |
| CDM-61 | BR-16, BR-17 | `1050-UPDATE-ACCOUNT` and the EOF branch | `InterestAccrualWriter.settleCurrentAccount` via `BAS` `INTEREST_SETTLEMENT` | `interestCalculationStep` | `InterestAccrualWriterTest`, `TransactionBatchIntegrationTest` | the interest total is added and both cycle amounts cleared, once per account | idempotent remote call; nothing settled on an empty run | Done, CMD-18 |
| CDM-61 | BR-18 … BR-20 | `1400-COMPUTE-FEES`, the I/O status checks | not implemented / framework failure handling | `interestCalculationStep` | — | fees are not calculated; an I/O failure ends the run | as expected | Done, **R** UCR-38 |

## 22. CBACT02C / CBACT03C / CBCUS01C under READCARD, READXREF, READCUST — master file prints

Jobs: `printCardFileJob`, `printCardXrefFileJob`, `printCustomerFileJob`.

| Ticket | Rules | Source location | Target | Job/step | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-62 | BR-01 … BR-04, BR-07 | the read loop and the open/close paragraphs | `MasterFilePrintJobConfig` | the three print steps | `MasterFileRecordsTest` | every record printed once in key order, as the whole record image | as expected | Done |
| CDM-62 | BR-05, BR-06, BR-08 | the file-status checks | framework failure handling | the three print steps | — | end of file completes the run; any other status fails it | as expected | Done |

## 23. CBSTM03A / CBSTM03B under CREASTMT — account statements

PostgreSQL objects: `transaction`; cross-reference, customer and account through the account
service. Job: `accountStatementJob`.

| Ticket | Rules | Source location | Target | Job/step | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-63 | CBSTM03A BR-01, BR-02, BR-12, BR-13 | `1000-MAINLINE`, `2000`/`3000` keyed reads | `StatementPartyReader` over `GET /api/statement-parties` | `accountStatementStep` | `TransactionBatchIntegrationTest` | one statement per cross-reference entry, with its customer and account | as expected | Done |
| CDM-63 | CBSTM03A BR-03, BR-04, BR-08, BR-09 | `5000-CREATE-STATEMENT`, `5100`/`5200`, `6000-WRITE-TRANS` | `StatementRenderer`, `StatementLines`, `StatementHtmlLines`, `StatementWriter` | `accountStatementStep` | `StatementRendererTest`, `StatementLinesTest` | both the 80-column and the HTML statement, with the source's field truncation | as expected | Done |
| CDM-63 | CBSTM03A BR-05, BR-07 | `4000-TRNXFILE-GET` | transactions read by card number, amounts summed | `accountStatementStep` | `StatementRendererTest` | only the card's transactions are listed and totalled | as expected | Done |
| CDM-63 | CBSTM03A BR-06, BR-10, BR-11 | the 51 × 10 working-storage table | no table: a query per card | `accountStatementStep` | `TransactionBatchIntegrationTest` | source drops the last card group and caps at 51 × 10 | **deliberately corrected** | Done, **R** UCR-34 |
| CDM-63 | CBSTM03A BR-14 | the PSA/TCB/TIOT walk | the Spring Batch execution context in the log | `accountStatementStep` | — | the job and step are identified in the log | retired mechanism | Done |
| CDM-63 | CBSTM03B BR-01, BR-02 | `0000-START` dispatch and the `…900-EXIT` status moves | consolidated into the repository and client calls | `accountStatementStep` | — | each file operation returns its outcome to the caller | consolidated | Done |

## 24. CBEXPORT / CBIMPORT under CBEXPORT and CBIMPORT — branch migration

Jobs: `exportBranchMigrationJob`, `exportBranchMigrationTransactionsJob`,
`importBranchMigrationJob`.

| Ticket | Rules | Source location | Target | Job/step | Test | Expected | Actual | Status |
|---|---|---|---|---|---|---|---|---|
| CDM-64 | CBEXPORT BR-01 … BR-05, BR-08 | `1050-GENERATE-TIMESTAMP`, the five export paragraphs | `MigrationExportJobConfig`, `MigrationExportTransactionsJobConfig`, `MigrationExportAggregator` | the two export steps | `MigrationJobsIntegrationTest`, `MigrationExportRecordTest` | 500-byte records of the five types with the run timestamp and the source payloads, binary fields intact | as expected | Done |
| CDM-64 | CBEXPORT BR-06 | `ADD 1 TO WS-SEQUENCE-COUNTER` | per-file numbering in each context | the two export steps | `MigrationJobsIntegrationTest` | one sequence across all five types | numbered per export file | Done, CMD register |
| CDM-64 | CBEXPORT BR-07 | `MOVE '0001'`, `MOVE 'NORTH'` | the same literals in `MigrationExportHeader` | the two export steps | `MigrationExportRecordTest` | every record states branch `0001`, region `NORTH` | as expected (AS-IS) | Done, **R** UCR-37 |
| CDM-64 | CBEXPORT BR-09 … BR-11 | the status checks and the `DISPLAY` totals | framework failure handling, `MigrationImportCountListener` counterpart | the two export steps | `MigrationJobsIntegrationTest` | a write failure ends the run; the counts are reported | as expected | Done |
| CDM-65 | CBIMPORT BR-01 … BR-04, BR-06 | `2000-PROCESS-EXPORT-FILE`, `2200-PROCESS-RECORD-BY-TYPE` | `MigrationImportJobConfig`, `FixedLengthRecordReader`, `ImportMasterRecords` | `importBranchMigrationStep` | `MigrationJobsIntegrationTest`, `ImportMasterRecordsTest`, `FixedLengthRecordReaderTest` | every record is dispatched on its type and written field for field | as expected | Done |
| CDM-65 | CBIMPORT BR-05, BR-08 | `2700-PROCESS-UNKNOWN-RECORD`, `2750-WRITE-ERROR` | the error-file delegate of the classifying writer | `importBranchMigrationStep` | `MigrationJobsIntegrationTest` | an unknown type is rejected and counted, the run continues | as expected | Done |
| CDM-65 | CBIMPORT BR-07, BR-10 | the status checks and the `DISPLAY` totals | framework failure handling, `MigrationImportCountListener` | `importBranchMigrationStep` | `MigrationJobsIntegrationTest` | a master-file write failure ends the run; the counts are reported | as expected | Done |
| CDM-65 | CBIMPORT BR-09 | `3000-VALIDATE-IMPORT` | the same two log lines, no validation | `importBranchMigrationStep` | — | validation always reports success without examining anything | as expected (AS-IS) | Done, **R** UCR-35 |
| CDM-66 | COBSWAIT BR-01 | `CALL 'MVSWAIT'` | retired: job sequencing is the scheduler's | n/a | — | the step pauses for the supplied centiseconds | retired | Done |

## 25. Coverage of Part C

All rules extracted in the nine Part C catalogues are traced by the groups above.

- Rows citing an executed test are proved by `evidence/backend-build-test.log` and
  `evidence/frontend-test.log` of this closure.
- Five rows are **retired platform mechanisms** and say so rather than claiming a test: the MVS
  control-block walk of `CBSTM03A` BR-14, the `CBSTM03B` dispatch, `COBSWAIT` BR-01, and the two
  file-status groups whose behaviour is the framework's.
- Two rows are **deliberate corrections** of a source defect, each REVIEW REQUIRED:
  `CBTRN03C` BR-09 (the closing totals, UCR-33) and `CBSTM03A` BR-11 (the last card group, UCR-34).
  The `CBACT04C` deviations listed in §4 of its catalogue and the posting inconsistency of
  `CBTRN02C` are carried as UCR-39.
- Four rows are **consistency deltas**, each with a register entry: CMD-16 (bill payment), CMD-17
  (report request) and CMD-18 (posting and interest settlement).
- Every remaining **R** resolves to an entry in `11-unsupported-construct-register.md`: the AS-IS
  literals to UCR-07, the export branch and region to UCR-37, the empty import validation to
  UCR-35, the uncalculated fees to UCR-38 and the clear personal data to UCR-36.
