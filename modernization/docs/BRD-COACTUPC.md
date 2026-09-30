# Business Rules — COACTUPC (Account Update, CICS transaction CAUP)

Source: `app/cbl/COACTUPC.cbl` (4,236 lines). Copybooks: CVACT01Y, CVACT03Y, CVCUS01Y, CVCRD01Y,
COCOM01Y, CSLKPCDY (reference tables), CSUTLDWY + CSUTLDPY (date edits), CSSETATY (attribute
macro), CSSTRPFY (function-key mapping), CSDAT01Y, CSMSG01Y, CSMSG02Y, CSUSR01Y, COTTL01Y,
COACTUP (map), DFHBMSCA/DFHAID (IBM plumbing). Called program: `CSUTLDTC`.

Classification tags: Validation, Calculation, Derivation/Default, Data Selection, Control Flow,
Exception Handling, Formatting/Conversion. Origin: Business-derived or Hard-coded literal.

## Conversation and control flow

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | A new update starts with nothing fetched | An update that arrives directly, or from the main menu for the first time, starts with no account fetched and no remembered values. | `0000-MAIN` line 859ff: `IF EIBCALEN = 0 OR (CDEMO-FROM-PROGRAM = LIT-MENUPGM AND NOT CDEMO-PGM-REENTER)` → `SET ACUP-DETAILS-NOT-FETCHED` | Control Flow | Business-derived | — |
| BR-02 | Which operator actions are allowed depends on the stage | Display and exit are always allowed; "save" is allowed only once changes have been validated and not yet confirmed; "cancel/reload" is allowed only once details have been fetched. Any other key is treated as a display request. | `0000-MAIN`: `IF CCARD-AID-ENTER OR CCARD-AID-PFK03 OR (CCARD-AID-PFK05 AND ACUP-CHANGES-OK-NOT-CONFIRMED) OR (CCARD-AID-PFK12 AND NOT ACUP-DETAILS-NOT-FETCHED)` | Control Flow | Business-derived | Invalid key silently re-interpreted, no message |
| BR-03 | Exit returns to the caller, otherwise to the main menu | Leaving the update returns the operator to the calling function, or to the main menu when the caller is unknown. | `0000-MAIN`, `WHEN CCARD-AID-PFK03` | Control Flow | Business-derived | — |
| BR-04 | A completed update is not repeatable | Once an update has been committed, the next interaction restarts the conversation instead of writing again. | `0000-MAIN` reset branch on `ACUP-CHANGES-OKAYED-AND-DONE` | Control Flow | Business-derived | — |
| BR-05 | Unrecognised conversation state is a failure | If the update is re-entered in an unrecognised state the transaction fails with an unexpected-data condition. | `2000-DECIDE-ACTION` `WHEN OTHER` (line 2562ff): abend code `'0001'`, reason `UNEXPECTED DATA SCENARIO` | Exception Handling | Hard-coded literal (`0001`) | Hard-coded abend code |
| BR-06 | Before details are fetched only the key is checked | Until an account has been fetched, only the account number is validated; no other field is edited. | `1200-EDIT-MAP-INPUTS` lines 1433-1447: `IF ACUP-DETAILS-NOT-FETCHED PERFORM 1210-EDIT-ACCOUNT … GO TO …EXIT` | Control Flow | Business-derived | — |
| BR-07 | Blank key is "no search criteria" | A submission with no account number is treated as an enquiry without search criteria. | `1200-EDIT-MAP-INPUTS` lines 1440-1442 | Control Flow | Business-derived | — |
| BR-08 | Cancel reloads the stored values | Cancelling after details were fetched discards keyed changes and redisplays the values currently stored. | `2000-DECIDE-ACTION`, `WHEN CCARD-AID-PFK12 AND NOT ACUP-DETAILS-NOT-FETCHED` → `9000-READ-ACCT` | Control Flow | Business-derived | — |
| BR-09 | Save is only possible after validation | The stored records are written only from the state "changes validated, not yet confirmed"; the operator must confirm with the save action. | `2000-DECIDE-ACTION`, `WHEN CCARD-AID-PFK05 AND ACUP-CHANGES-OK-NOT-CONFIRMED` → `9600-WRITE-PROCESSING` | Control Flow | Business-derived | — |
| BR-10 | Validated changes must be confirmed | When all edits pass, the operator is told "Changes validated.Press F5 to save" and nothing is written yet. | `1200-EDIT-MAP-INPUTS` end: `SET ACUP-CHANGES-OK-NOT-CONFIRMED`; `3250-SETUP-INFOMSG` | Control Flow | Business-derived | — |

## Key and change detection

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-11 | An account number must be supplied | With no account number the operator is prompted for one and the remembered account is cleared to zero. | `1210-EDIT-ACCOUNT` lines 1783-1800 | Validation | Business-derived | — |
| BR-12 | The account number must be a non-zero 11-digit number | A non-numeric or all-zero account number is rejected with "Account Number if supplied must be a 11 digit Non-Zero Number". | `1210-EDIT-ACCOUNT` lines 1802-1822 | Validation | Business-derived | Message differs from the enquiry's wording for the same rule |
| BR-13 | Nothing to save when nothing changed | A submission whose values all match the values fetched is reported as "No change detected with respect to values fetched." and no field edit or write takes place. | `1205-COMPARE-OLD-NEW` (line 1681ff) → `NO-CHANGES-FOUND`; `1200-EDIT-MAP-INPUTS` lines 1568-1573 | Control Flow | Business-derived | — |
| BR-14 | Account status and group are compared ignoring case and padding | Changes to account status and account group are detected without regard to upper/lower case or trailing blanks. | `1205-COMPARE-OLD-NEW`, `FUNCTION LOWER-CASE`/`TRIM` on `ACCT-ACTIVE-STATUS`, `ACCT-GROUP-ID` | Data Selection | Business-derived | — |
| BR-15 | Customer text fields are compared ignoring case and padding | Changes to names, address lines, state, country and government id are detected without regard to case or trailing blanks. | `1205-COMPARE-OLD-NEW`, `FUNCTION UPPER-CASE` comparisons | Data Selection | Business-derived | — |
| BR-16 | Numbers, dates, phones and identifiers are compared exactly | Balances, limits, cycle amounts, dates, phone digits, social security number, EFT account, card-holder indicator and credit score are compared value-for-value. | `1205-COMPARE-OLD-NEW`, direct `EQUAL` comparisons | Data Selection | Business-derived | — |
| BR-17 | Already-validated submissions are not re-edited | Once a submission has been validated, or an update completed, the field edits are not run again on the same values. | `1200-EDIT-MAP-INPUTS` lines 1568-1573 (`OR ACUP-CHANGES-OK-NOT-CONFIRMED OR ACUP-CHANGES-OKAYED-AND-DONE`) | Control Flow | Business-derived | — |

## Field edits (fixed order, first message wins)

Order is significant: `1200-EDIT-MAP-INPUTS` edits account status, open date, credit limit, expiry
date, cash credit limit, reissue date, current balance, current cycle credit, current cycle debit,
social security number, date of birth, credit score, first/middle/last name, address line 1, state,
zip, city, country, phone 1, phone 2, EFT account id, primary card-holder indicator, then the
state/zip cross-check. Only the first message raised is shown; every failing field is highlighted.

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-18 | An asterisk clears a field | A field containing only asterisks is treated as if it had been left empty. | `1100-RECEIVE-MAP` (line 1039ff) asterisk-to-LOW-VALUES normalisation | Formatting/Conversion | Business-derived | — |
| BR-19 | Mandatory fields must be supplied | A mandatory field that is empty or blank is rejected with "<field> must be supplied." | `1215-EDIT-MANDATORY` lines 1824-1855 | Validation | Business-derived | — |
| BR-20 | Yes/no fields | Account status and the primary card-holder indicator must be supplied and must be Y or N ("<field> must be Y or N."). | `1220-EDIT-YESNO` lines 1856-1897, lookup in CSLKPCDY | Validation | Business-derived | — |
| BR-21 | Required alphabetic fields | First name, last name, state, city and country must be supplied and may contain letters and spaces only ("<field> can have alphabets only."). | `1225-EDIT-ALPHA-REQD` lines 1898-1954 | Validation | Business-derived | — |
| BR-22 | Optional alphabetic fields | Middle name may be left empty, but if supplied may contain letters and spaces only. | `1235-EDIT-ALPHA-OPT` lines 2012-2060 | Validation | Business-derived | — |
| BR-23 | Alphanumeric fields | Where an alphanumeric edit is used the value may contain letters, digits and spaces only ("<field> can have numbers or alphabets only."). | `1230-EDIT-ALPHANUM-REQD` lines 1955-2011, `1240-EDIT-ALPHANUM-OPT` lines 2061-2108 | Validation | Business-derived | `1230`/`1240` are not reached from `1200` in the supplied source — unreachable branches |
| BR-24 | Required numeric fields must be non-zero | Zip, credit score and EFT account id must be supplied, must be all digits and must not be zero ("<field> must be all numeric.", "<field> must not be zero."). | `1245-EDIT-NUM-REQD` lines 2109-2179 | Validation | Business-derived | — |
| BR-25 | Money fields keep sign and two decimals | Current balance, credit limit, cash credit limit and both cycle amounts must be supplied and must be a signed amount with two decimal places; otherwise "<field> is not valid". | `1250-EDIT-SIGNED-9V2` lines 2180-2224 | Validation | Business-derived | — |
| BR-26 | Phone numbers are optional but must be complete | A phone number may be omitted entirely; if any part is supplied all three parts are required. | `1260-EDIT-US-PHONE-NUM` lines 2225-2245 | Validation | Business-derived | — |
| BR-27 | Area code must be a known North American code | The area code must be supplied, must be a non-zero 3-digit number and must appear in the North American general-purpose area code table, otherwise ": Not valid North America general purpose area code". | `EDIT-AREA-CODE` lines 2246-2315, `SEARCH ALL` over the CSLKPCDY table | Validation | Business-derived | Reference table is a source literal table — needs business sign-off on currency |
| BR-28 | Phone prefix | The prefix must be supplied, must be a 3-digit number and must not be zero. | `EDIT-US-PHONE-PREFIX` lines 2316-2369 | Validation | Business-derived | — |
| BR-29 | Phone line number | The line number must be supplied, must be a 4-digit number and must not be zero. | `EDIT-US-PHONE-LINENUM` lines 2370-2430 | Validation | Business-derived | — |
| BR-30 | Social security number | All three parts must be numeric and non-zero, and the first part may not be 000, 666 or 900-999 ("SSN: First 3 chars: should not be 000, 666, or between 900 and 999"). | `1265-EDIT-US-SSN` lines 2431-2492 | Validation | Business-derived | — |
| BR-31 | State code must be known | The state code must appear in the state code table, otherwise "<field>: is not a valid state code". | `1270-EDIT-US-STATE-CD` lines 2493-2513, `SEARCH ALL` over CSLKPCDY | Validation | Business-derived | — |
| BR-32 | Credit score range | The credit score must be between 300 and 850 ("<field>: should be between 300 and 850"). | `1275-EDIT-FICO-SCORE` lines 2514-2535 | Validation | Hard-coded literal (300/850) | Bounds hard-coded — needs business sign-off |
| BR-33 | Zip code must match the state | The first two digits of the zip code must be a combination registered for the state, otherwise "Invalid zip code for state". | `1280-EDIT-US-STATE-ZIP-CD` lines 2536-2561, `SEARCH ALL` over CSLKPCDY | Validation | Business-derived | — |
| BR-34 | City is validated, second address line is not | The city is required and must be alphabetic; the second address line is accepted as keyed without validation. | `1200-EDIT-MAP-INPUTS` near line 1600: `* MOVE 'Address Line 2' …` commented out, `MOVE 'City' … PERFORM 1225-EDIT-ALPHA-REQD` on `ADDR-LINE-3` | Validation | Business-derived | Commented-out logic retained, not deleted (UCR-02) |

## Date edits (CSUTLDPY / CSUTLDTC)

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-35 | Year | The year must be supplied, must be a 4-digit number and must fall in the 20th or 21st century ("<field> : Year must be supplied.", "<field> must be 4 digit number.", "<field> : Century is not valid."). | `EDIT-YEAR-CCYY` (CSUTLDPY lines 25-90) | Validation | Hard-coded literal (centuries 19/20) | Century range hard-coded; will fail after 2099 |
| BR-36 | Month | The month must be supplied and must be a number between 1 and 12. | `EDIT-MONTH` (CSUTLDPY lines 91-147) | Validation | Business-derived | — |
| BR-37 | Day | The day must be supplied and must be a number between 1 and 31. | `EDIT-DAY` (CSUTLDPY lines 150-207) | Validation | Business-derived | — |
| BR-38 | Days per month | A 31st day is refused for months that have 30 days, a 30th of February is refused, and a 29th of February is accepted only in a leap year (century years divisible by 400). | `EDIT-DAY-MONTH-YEAR` (CSUTLDPY lines 209-282) | Validation | Business-derived | — |
| BR-39 | Final date check by the date service | A date that passed every edit is still verified by the date service, and a non-zero severity is reported as "<field> validation error Sev code: <sev> Message code: <msg>". | `EDIT-DATE-LE` (CSUTLDPY lines 284-325) `CALL 'CSUTLDTC'` | Validation | Business-derived | Return code is checked (severity), message code is only echoed |
| BR-40 | Date of birth cannot be in the future | A date of birth on or after today is refused with "<field>:cannot be in the future". | `EDIT-DATE-OF-BIRTH` (CSUTLDPY lines 341-372) | Validation | Business-derived | Today itself is refused (comparison is strictly greater-than) |

## Read, write and concurrency

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-41 | Accounts are located through the card cross-reference | The update resolves the account by reading the card cross-reference by account number, then the account master, then the customer master, stopping at the first failure. | `9000-READ-ACCT` lines 3608-3644 | Data Selection | Business-derived | — |
| BR-42 | Unknown account / customer messages | Missing cross-reference, account master or customer master records are reported with the same wording as the enquiry, including the response codes. | `9200` (3650), `9300` (3701), `9400` (3752) `WHEN DFHRESP(NOTFND)` | Exception Handling | Business-derived | — |
| BR-43 | Any other storage failure is fatal to the update | Any read outcome other than success or not-found is reported as a file error naming the operation, file and response codes. | `9200`/`9300`/`9400` `WHEN OTHER` | Exception Handling | Business-derived | Long-message send commented out — dead code |
| BR-44 | The fetched values become the comparison baseline | The values read are kept as the "as fetched" baseline used both for change detection and for the later concurrency check. | `9500-STORE-FETCHED-DATA` lines 3801-3887 | Derivation/Default | Business-derived | — |
| BR-45 | Dates are held as separate year, month and day | Open, expiry, reissue and birth dates are split into year, month and day for editing and rebuilt as `YYYY-MM-DD` when stored. | `9500-STORE-FETCHED-DATA` lines 3832-3845; `9600-WRITE-PROCESSING` lines 3976-4000, 4047-4052 | Formatting/Conversion | Business-derived | — |
| BR-46 | Both records are locked before anything is written | The account record and then the customer record are locked for update before any comparison or write; failure to lock either one aborts the save with a "could not lock" message. | `9600-WRITE-PROCESSING` lines 3894-3942 | Control Flow | Business-derived | — |
| BR-47 | Someone else's change wins | If the locked account or customer record no longer matches the values that were fetched, nothing is written and the operator is told "Record changed by some one else. Please review". | `9700-CHECK-CHANGE-IN-REC` lines 4109-4192 called from `9600` line 3947 | Validation | Business-derived | — |
| BR-48 | Concurrency comparison ignores case for text | The concurrency check compares account group and customer text fields ignoring case, and all other fields exactly. | `9700-CHECK-CHANGE-IN-REC`, `FUNCTION LOWER-CASE`/`UPPER-CASE` | Data Selection | Business-derived | Date-of-birth comparison uses offsets 1/6/9 against 1/5/7 in the saved area — inconsistent offsets, REVIEW REQUIRED |
| BR-49 | Account is written before customer | The account record is rewritten first; only if that succeeds is the customer record rewritten. | `9600-WRITE-PROCESSING` lines 4065-4091 | Control Flow | Business-derived | — |
| BR-50 | A failed customer write undoes the account write | If the customer rewrite fails, the whole save is backed out and reported as failed. | `9600-WRITE-PROCESSING` lines 4095-4103, `EXEC CICS SYNCPOINT ROLLBACK` | Exception Handling | Business-derived | A failed *account* rewrite exits without a rollback — asymmetric, REVIEW REQUIRED |
| BR-51 | Phone numbers are stored formatted | Phone numbers are stored as `(AAA)PPP-LLLL`. | `9600-WRITE-PROCESSING` lines 4027-4041 | Formatting/Conversion | Business-derived | — |
| BR-52 | Successful save is confirmed | A completed save reports "Changes committed to database" and redisplays the stored values. | `2000-DECIDE-ACTION` success branch, `3250-SETUP-INFOMSG` | Control Flow | Business-derived | — |
| BR-53 | Reissue date is moved twice | The stored reissue date is copied into the update record and then immediately overwritten by the keyed year/month/day. | `9600-WRITE-PROCESSING` lines 3993-4000 | Derivation/Default | Business-derived | Dead statement (first `MOVE` has no effect) |
| BR-54 | Account number and customer number are never edited | The account number and customer number are displayed but are not changeable on this screen. | `3310-PROTECT-ALL-ATTRS` (3441) / `3320-UNPROTECT-FEW-ATTRS` (3500) | Validation | Business-derived | — |
| BR-55 | Failing fields are highlighted | Every field that failed an edit is highlighted, independently of which message is displayed. | `3300-SETUP-SCREEN-ATTRS` lines 2986-3440 (`COPY CSSETATY REPLACING`, 40 expansions) | Formatting/Conversion | Business-derived | — |
| BR-56 | Unhandled failure ends the transaction | An unexpected failure sends a plain-text abend notice naming this program and terminates the transaction with abend code 9999. | `ABEND-ROUTINE` lines 4203-4225 | Exception Handling | Hard-coded literal (`9999`) | Hard-coded abend code |

## Exception scenarios (file and I/O)

| Condition | Source treatment | Target treatment |
|---|---|---|
| read success | continue | repository returns the row |
| read not-found | not-found message, no data | `RecordNotFoundException` → HTTP 404, identical text |
| read other response | file-error message | `StorageAccessException` → HTTP 500, identical text |
| lock (READ UPDATE) failure | "could not lock" message, no write | `LOCK_ERROR` status, HTTP 200 body carrying the same message |
| account rewrite failure | update-failed message, no rollback issued | `UpdateFailedException` → HTTP 500, transaction rolled back (delta CMD-03) |
| customer rewrite failure | update-failed message, `SYNCPOINT ROLLBACK` | `UpdateFailedException` → HTTP 500, transaction rolled back |
| unhandled abend | `ABEND ABCODE('9999')` | unchecked exception → HTTP 500 (UCR-08) |

## Target implementation

`AccountUpdateService`, `AccountUpdateValidator`, `AccountChangeDetector`, `FieldEditor`,
`DateEditor`, `DateValidationService`, `LookupTables`, `AccountUpdateController`; React
`AccountUpdateScreen.tsx`. Paragraph-level mapping is in `09-cobol-to-java-mapping.md`; per-rule
tests are in `14-rtm.md`.
