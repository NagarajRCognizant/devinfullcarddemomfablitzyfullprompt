# Business Rules — COACTVWC (Account View, CICS transaction CAVW)

Source: `app/cbl/COACTVWC.cbl` (941 lines). Copybooks: CVACT01Y, CVACT02Y, CVACT03Y, CVCUS01Y,
CVCRD01Y, COCOM01Y, COTTL01Y, CSDAT01Y, CSMSG01Y, CSMSG02Y, CSUSR01Y, CSSTRPFY, COACTVW (map),
DFHBMSCA/DFHAID (IBM plumbing).

Classification tags: Validation, Calculation, Derivation/Default, Data Selection, Control Flow,
Exception Handling, Formatting/Conversion. Origin: Business-derived or Hard-coded literal.

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | New enquiry starts clean | An enquiry that arrives directly, or arrives from the main menu for the first time, starts with no remembered account and no remembered search value. | `0000-MAIN` line 282: `IF EIBCALEN = 0 OR (CDEMO-FROM-PROGRAM = LIT-MENUPGM AND NOT CDEMO-PGM-REENTER)` → `INITIALIZE CARDDEMO-COMMAREA` | Control Flow | Business-derived | — |
| BR-02 | Only "show" and "exit" actions are honoured | The enquiry screen accepts only the display request and the exit request; any other function key is treated as a display request rather than rejected. | `0000-MAIN` lines 306-314: `SET PFK-INVALID`, `IF CCARD-AID-ENTER OR CCARD-AID-PFK03 … IF PFK-INVALID SET CCARD-AID-ENTER` | Control Flow | Business-derived | Silent re-interpretation of an invalid key; no user message |
| BR-03 | Exit returns to the caller, otherwise to the main menu | Leaving the enquiry returns the operator to whichever function called it; when that is unknown the operator is returned to the main menu. | `0000-MAIN` lines 324-352 (`WHEN CCARD-AID-PFK03`), defaults `LIT-MENUTRANID`/`LIT-MENUPGM` when the caller is LOW-VALUES or spaces | Control Flow | Business-derived | — |
| BR-04 | Exit resets the operator to a regular user | On exit the enquiry declares the operator to be a regular (non-administrator) user regardless of how the operator signed on. | `0000-MAIN` line 344: `SET CDEMO-USRTYP-USER TO TRUE` | Derivation/Default | Business-derived | Overwrites the signed-on user type — REVIEW REQUIRED |
| BR-05 | Unrecognised conversation state is a failure | If the enquiry is re-entered in a state that is neither "first display" nor "returning input", the transaction fails with an unexpected-data condition. | `0000-MAIN` lines 375-382 `WHEN OTHER` → abend code `'0001'`, message `UNEXPECTED DATA SCENARIO` | Exception Handling | Hard-coded literal (`0001`) | Hard-coded abend code |
| BR-06 | Asterisk and blank mean "nothing entered" | An account filter of a single asterisk, or of blanks, counts as no account having been entered. | `2200-EDIT-MAP-INPUTS` lines 628-633: `IF ACCTSIDI = '*' OR SPACES MOVE LOW-VALUES TO CC-ACCT-ID` | Validation | Business-derived | — |
| BR-07 | An account number must be entered | When no account number is entered the operator is prompted for one, the remembered account is cleared to zero, and no file is read. | `2210-EDIT-ACCOUNT` lines 653-662: blank/LOW-VALUES → `SET FLG-ACCTFILTER-BLANK`, `SET WS-PROMPT-FOR-ACCT`, `MOVE ZEROES TO CDEMO-ACCT-ID` | Validation | Business-derived | — |
| BR-08 | Blank filter is reported as "no search criteria" | A submission with no account number is treated as an enquiry without search criteria rather than as an invalid value. | `2200-EDIT-MAP-INPUTS` lines 640-642: `IF FLG-ACCTFILTER-BLANK SET NO-SEARCH-CRITERIA-RECEIVED` | Control Flow | Business-derived | — |
| BR-09 | The account number must be a non-zero 11-digit number | An account number that is not numeric, or is all zeroes, is rejected with "Account Filter must  be a non-zero 11 digit number" and the remembered account is cleared. | `2210-EDIT-ACCOUNT` lines 666-676 | Validation | Business-derived | Message contains a double space, preserved verbatim |
| BR-10 | Accounts are located through the card cross-reference | An account is resolved by reading the card cross-reference by account number first, which yields the customer and the card number used for the rest of the enquiry. | `9000-READ-ACCT` lines 691-694 → `9200-GETCARDXREF-BYACCT` reading `LIT-CARDXREFNAME-ACCT-PATH`; `XREF-CUST-ID`, `XREF-CARD-NUM` saved (lines 741-742) | Data Selection | Business-derived | — |
| BR-11 | Reads stop at the first failure | If the cross-reference read fails, the account master is not read; if the account master read fails, the customer master is not read. | `9000-READ-ACCT` lines 697-715 (`GO TO 9000-READ-ACCT-EXIT` after each step) | Control Flow | Business-derived | — |
| BR-12 | Unknown account (cross-reference) | An account that has no cross-reference entry is reported as "Account:<id> not found in Cross ref file." with the response codes appended. | `9200-GETCARDXREF-BYACCT`, `WHEN DFHRESP(NOTFND)` | Exception Handling | Business-derived | — |
| BR-13 | Unknown account (account master) | An account present in the cross-reference but absent from the account master is reported as "Account:<id> not found in Acct Master file." with the response codes appended. | `9300-GETACCTDATA-BYACCT`, `WHEN DFHRESP(NOTFND)` | Exception Handling | Business-derived | Data-integrity condition treated as a user error, not an abend |
| BR-14 | Unknown customer | A customer referenced by the cross-reference but absent from the customer master is reported as "CustId:<id> not found in customer master." with the response codes appended. | `9400-GETCUSTDATA-BYCUST`, `WHEN DFHRESP(NOTFND)` | Exception Handling | Business-derived | — |
| BR-15 | Any other storage failure is fatal to the enquiry | Any read outcome other than success or not-found is reported as a file error naming the operation, the file and the response codes, and the enquiry produces no data. | `9200`/`9300`/`9400` `WHEN OTHER` → `WS-FILE-ERROR-MESSAGE` | Exception Handling | Business-derived | The long-message send is commented out — dead code |
| BR-16 | Account fields are shown once either master record is found | Account status, balances, limits, cycle amounts, open/expiry/reissue dates and group id are displayed when the account or the customer record was found. | `1200-SETUP-SCREEN-VARS` lines 471-491 | Formatting/Conversion | Business-derived | Condition is `FOUND-ACCT … OR FOUND-CUST …`, so account fields can be displayed from a stale area when only the customer was found — REVIEW REQUIRED |
| BR-17 | Customer fields are shown only when the customer is found | Customer identity, address, phones, identifiers, credit score and card-holder indicator are displayed only when the customer record was found. | `1200-SETUP-SCREEN-VARS` lines 493-523 | Formatting/Conversion | Business-derived | — |
| BR-18 | Social security number is displayed grouped | The social security number is displayed as three groups separated by hyphens (3-2-4). | `1200-SETUP-SCREEN-VARS` lines 496-504 (`STRING CUST-SSN(1:3) '-' CUST-SSN(4:2) '-' CUST-SSN(6:4)`) | Formatting/Conversion | Business-derived | — |
| BR-19 | Prompt shown when nothing else to say | When the enquiry has no other message it prompts "Enter or update id of account to display". | `1200-SETUP-SCREEN-VARS` lines 528-530, `SET WS-PROMPT-FOR-INPUT` | Derivation/Default | Business-derived | — |
| BR-20 | Rejected account number is highlighted | An account number that failed validation is highlighted, and a blank one is redisplayed as an asterisk once the operator has submitted the screen at least once. | `1300-SETUP-SCREEN-ATTRS` lines 557-565 (`DFHRED`, `MOVE '*' TO ACCTSIDO`) | Formatting/Conversion | Business-derived | — |
| BR-21 | The cursor stays on the account number | The account number field always receives the cursor. | `1300-SETUP-SCREEN-ATTRS` lines 546-552 | Control Flow | Business-derived | Both `WHEN` branches of the `EVALUATE` are identical — duplicate/contradictory condition |
| BR-22 | Enquiry never changes stored data | The enquiry only reads; it issues no write, rewrite or delete against any store. | No `WRITE`/`REWRITE`/`DELETE` in the program | Data Selection | Business-derived | — |
| BR-23 | Unhandled failure ends the transaction | An unexpected failure sends a plain-text abend notice naming this program and terminates the transaction with abend code 9999. | `ABEND-ROUTINE` line 916, `EXEC CICS ABEND ABCODE('9999')`, default message `UNEXPECTED ABEND OCCURRED.` | Exception Handling | Hard-coded literal (`9999`) | Hard-coded abend code |

## Exception scenarios (file and I/O)

| Condition | Source treatment | Target treatment |
|---|---|---|
| `DFHRESP(NORMAL)` (file status `'00'`) | continue | repository returns the row |
| `DFHRESP(NOTFND)` (file status `'10'`) | message, no data, screen redisplayed | `RecordNotFoundException` → HTTP 404 with the same message text |
| any other response | file-error message naming operation/file/response codes | `StorageAccessException` → HTTP 500 with the same message text |
| unhandled abend | `EXEC CICS ABEND ABCODE('9999')` | unchecked exception → HTTP 500; the terminal abend has no local equivalent (Unsupported Construct Register UCR-08) |

## Target implementation

`AccountViewService`, `AccountReadService`, `AccountViewController`, `ScreenMessages`,
`AccountScreenMapper`; React `AccountViewScreen.tsx`. Paragraph-level mapping is in
`09-cobol-to-java-mapping.md`; test coverage per rule is in `14-rtm.md`.
