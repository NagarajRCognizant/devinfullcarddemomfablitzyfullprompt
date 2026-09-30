# Business Rules — COCRDLIC / COCRDSLC / COCRDUPC (credit card list, view and update)

Sources: `app/cbl/COCRDLIC.cbl` (1459 lines), `app/cbl/COCRDSLC.cbl` (887 lines),
`app/cbl/COCRDUPC.cbl` (1560 lines) with maps `COCRDLI.bms`, `COCRDSL.bms`, `COCRDUP.bms`, the card
layout `app/cpy/CVACT02Y.cpy`, the cross-reference `app/cpy/CVACT03Y.cpy` and the shared key work
area `app/cpy/CVCRD01Y.cpy`. Transactions `CCLI`, `CCDL`, `CCUP`; menu options 3, 4 and 5
(`BRD-COMEN01C.md`, BR-01).

## COCRDLIC — credit card list

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | A page shows seven cards | The list presents at most seven cards at a time, in card-number order. | `WS-EACH-CARD(1)` … `WS-EACH-CARD(7)` population and clearing paragraphs | Data Selection | Business-derived | Seven is a literal repeated per row |
| BR-02 | An administrator sees every card | An operator whose user type is "A" browses all cards. | `9000-READ-FORWARD` / `9100-READ-BACKWARDS` with no account restriction when `CDEMO-USRTYP-ADMIN` | Data Selection | Business-derived | — |
| BR-03 | A regular operator sees only their account's cards | An operator who is not an administrator sees only the cards of the account carried in from the previous screen. | account filter forced from `CDEMO-ACCT-ID` for non-admin operators | Data Selection | Business-derived | A regular operator reaching the screen without an account in context sees nothing |
| BR-04 | The account filter is an eleven-digit number | An account filter that is supplied must be an eleven-digit number, otherwise "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER". | `1210-EDIT-ACCOUNT` | Validation | Business-derived | — |
| BR-05 | The card filter is a sixteen-digit number | A card filter that is supplied must be a sixteen-digit number, otherwise "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER". | `1220-EDIT-CARD` | Validation | Business-derived | — |
| BR-06 | An asterisk clears a filter | Keying "*" in a filter is treated as clearing it rather than as a value. | filter paragraphs (`IF ACCTSIDI = '*'`) | Formatting/Conversion | Business-derived | — |
| BR-07 | Rows are marked S to view or U to update | A row marked "S" opens the card view, a row marked "U" opens the card update; any other mark is refused with "INVALID ACTION CODE". | row selection `EVALUATE`, `WS-INVALID-ACTION-CODE` | Validation | Business-derived | — |
| BR-08 | Only one row may be marked | Marking more than one row is refused with "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE". | selection count check, `WS-MORE-THAN-1-ACTION` | Validation | Business-derived | — |
| BR-09 | An empty result is reported | A search that matches no card reports "NO RECORDS FOUND FOR THIS SEARCH CONDITION.". | `WS-NO-RECORDS-FOUND` | Exception Handling | Business-derived | — |
| BR-10 | The operator is told how to act on a row | When rows are shown the screen carries "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD". | `WS-INFORM-REC-ACTIONS` | Formatting/Conversion | Business-derived | — |
| BR-11 | Paging keeps the browse position | Page forward continues after the last card shown and page backward ends before the first one. | `9000-READ-FORWARD` / `9100-READ-BACKWARDS` with the saved record id | Control Flow | Business-derived | Position is by key, so cards added between requests change what the next page shows |
| BR-12 | Selection rows are protected when there is nothing to select | When a row holds no card, its selection field is protected so it cannot be marked. | `FLG-PROTECT-SELECT-ROWS` handling | Control Flow | Business-derived | — |
| BR-13 | Exit reports itself | The exit key leaves the screen with the message "PF03 PRESSED.EXITING". | `WS-EXIT-MESSAGE` | Control Flow | Business-derived | — |
| BR-14 | Any other key is refused | A key other than Enter, exit, page forward or page backward is refused as an invalid key. | `PFK-INVALID` handling | Exception Handling | Business-derived | — |

## COCRDSLC — credit card view

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | Both keys may be supplied | The card is located by account number, card number or both; an asterisk clears either one. | `1000-SEND-MAP` key handling, `IF ACCTSIDI = '*'` / `IF CARDSIDI = '*'` | Data Selection | Business-derived | — |
| BR-02 | The account filter is an eleven-digit number | As COCRDLIC BR-04, with the same message. | `1210-EDIT-ACCOUNT` | Validation | Business-derived | — |
| BR-03 | The card filter is a sixteen-digit number | As COCRDLIC BR-05, with the same message. | `1220-EDIT-CARD` | Validation | Business-derived | — |
| BR-04 | At least one key must be given | Neither key supplied leaves the screen asking for them rather than searching. | `FLG-ACCTFILTER-BLANK` / `FLG-CARDFILTER-BLANK` branches | Validation | Business-derived | — |
| BR-05 | A found card is displayed with its details | When the card is found the screen shows the account, card number, embossed name, status and expiry, with the confirmation that the details of the selected card are shown. | `FOUND-CARDS-FOR-ACCOUNT` and the map population paragraph | Formatting/Conversion | Business-derived | The CVV is held in the work area but is not sent to the screen |
| BR-06 | A card that is not on file is reported | A key combination that matches no card is reported as not found rather than shown empty. | card read `WHEN DFHRESP(NOTFND)` branch | Exception Handling | Business-derived | — |
| BR-07 | Returning to the list keeps the context | Exiting when the caller was the card list returns to the list rather than to the menu. | `IF CDEMO-LAST-MAPSET EQUAL LIT-CCLISTMAPSET` | Control Flow | Business-derived | — |
| BR-08 | An unexpected combination is reported as such | A state the program does not recognise reports "UNEXPECTED DATA SCENARIO". | `EVALUATE … WHEN OTHER` in the main paragraph | Exception Handling | Business-derived | A catch-all whose text tells the operator nothing actionable |

## COCRDUPC — credit card update

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | The card to change is located first | The account and card number identify the card whose details are fetched for change; without them the screen asks "Please enter Account and Card Number". | `PROMPT-FOR-SEARCH-KEYS`, `CCUP-DETAILS-NOT-FETCHED` branch | Control Flow | Business-derived | — |
| BR-02 | The account number must be a non-zero eleven-digit number | An account number that is blank, zero or not eleven digits is refused with "Account number must be a non zero 11 digit number". | `SEARCHED-ACCT-ZEROES` / `SEARCHED-ACCT-NOT-NUMERIC` | Validation | Business-derived | Two distinct causes share one message |
| BR-03 | The card number must be sixteen digits | A card number that is supplied must be sixteen digits, otherwise "Card number if supplied must be a 16 digit number". | `SEARCHED-CARD-NOT-NUMERIC` | Validation | Business-derived | — |
| BR-04 | A card name is required | A blank embossed name is refused with "Card name not provided". | `WS-PROMPT-FOR-NAME` | Validation | Business-derived | — |
| BR-05 | The card name holds only letters and spaces | A name containing anything but letters and spaces is refused with "Card name can only contain alphabets and spaces". | `WS-NAME-MUST-BE-ALPHA`, `FUNCTION LENGTH(FUNCTION TRIM(CARD-NAME-CHECK)) = 0` and the character test | Validation | Business-derived | Digits and punctuation cannot be embossed |
| BR-06 | The active status is Y or N | Any status other than "Y" or "N" is refused with "Card Active Status must be Y or N". | `CARD-STATUS-MUST-BE-YES-NO` | Validation | Business-derived | — |
| BR-07 | The expiry month is 1 to 12 | An expiry month outside 1-12 is refused with "Card expiry month must be between 1 and 12". | `CARD-EXPIRY-MONTH-NOT-VALID` | Validation | Business-derived | — |
| BR-08 | The expiry year must be valid | An expiry year the program does not accept is refused with "Invalid card expiry year". | `CARD-EXPIRY-YEAR-NOT-VALID` | Validation | Business-derived | The accepted range is not stated in the message |
| BR-09 | An asterisk clears a field | Keying "*" in any of the key or detail fields clears it rather than storing an asterisk. | the six `IF … = '*'` tests at the top of the edit paragraph | Formatting/Conversion | Business-derived | Applies to account, card, name, status, expiry month and expiry year |
| BR-10 | Nothing is written when nothing changed | If the keyed values match the fetched ones the update is refused with "No change detected with respect to values fetched.". | `NO-CHANGES-DETECTED`, `IF FUNCTION UPPER-CASE(CCUP-NEW-CARDDATA) EQUAL …` | Validation | Business-derived | The comparison is case-insensitive, so changing only the case of the name is treated as no change |
| BR-11 | Valid changes must be confirmed | Once the changes pass the edits the operator is told "Changes validated.Press F5 to save"; the record is written only after that key. | `PROMPT-FOR-CONFIRMATION`, `CCUP-CHANGES-OK-NOT-CONFIRMED`, `CCARD-AID-PFK05` | Control Flow | Business-derived | — |
| BR-12 | The record is locked for the update | The card is read for update before it is rewritten; a failure to lock reports "Could not lock record for update". | `COULD-NOT-LOCK-FOR-UPDATE` | Exception Handling | Business-derived | — |
| BR-13 | A record changed by someone else is not overwritten | If the stored card differs from the copy the operator was shown, the update is refused with "Record changed by some one else. Please review". | `DATA-WAS-CHANGED-BEFORE-UPDATE` | Validation | Business-derived | The whole fetched image is compared, so any concurrent change blocks the update |
| BR-14 | A successful update is confirmed | A successful rewrite reports "Changes committed to database"; a failed one reports "Changes unsuccessful. Please try again" and "Update of record failed". | `CONFIRM-UPDATE-SUCCESS`, `INFORM-FAILURE`, `LOCKED-BUT-UPDATE-FAILED` | Exception Handling | Business-derived | — |
| BR-15 | A missing cross-reference is reported | An account with no entry in the card cross-reference reports "Did not find this account in cards database", and a key combination with no card reports "Did not find cards for this search condition". | `DID-NOT-FIND-ACCT-IN-CARDXREF`, `DID-NOT-FIND-ACCTCARD-COMBO` | Exception Handling | Business-derived | — |
| BR-16 | A read failure is reported | A failure reading the card file reports "Error reading Card Data File" with the file name, response and reason codes. | `XREF-READ-ERROR`, `ERROR-FILE`/`ERROR-RESP`/`ERROR-RESP2` message | Exception Handling | Business-derived | — |
| BR-17 | Cancel abandons the changes | The cancel key leaves the changes unsaved and returns to the caller, which is the card list when the operator came from it. | `WHEN CCARD-AID-PFK12`, `IF CDEMO-LAST-MAPSET EQUAL LIT-CCLISTMAPSET` | Control Flow | Business-derived | — |

## Exception scenarios

All three programs test CICS responses rather than file status: `NORMAL` is success, `NOTFND`
produces the not-found message of the paragraph, `ENDFILE` ends a browse, and any other response is
formatted into the "<file> returned RESP <n>,RESP2 <n>" long message. `COCRDUPC` additionally
treats a failed `READ UPDATE` (BR-12) and a changed record (BR-13) as business outcomes rather than
errors. No path abends.

## Target implementation

| Source | Target |
|---|---|
| COCRDLIC | `CardListService`, `GET /api/cards`, React `CardListScreen` |
| COCRDSLC | `CardDetailService`, `GET /api/cards/{cardNumber}`, React `CardViewScreen` |
| COCRDUPC | `CardUpdateService`, `GET /api/cards/{cardNumber}/update` for the fetch, `POST /api/cards/validate` for the ENTER turn and `POST /api/cards` for the F5 turn, React `CardUpdateScreen` |

The seven-row page, the two filters with their asterisk handling, the S/U selection rules, the
alphabetic name rule, the status and expiry edits and every message text are preserved and asserted
by `CardListServiceTest`, `CardDetailServiceTest`, `CardUpdateServiceTest` and the React screen
tests. BR-02/BR-03 of the list keep the source's shape: the account filter is a request parameter and
`MenuAccessService.requireAccess` performs the user-type check that the source read from the
COMMAREA, so an operator who may not reach `COCRDLIC` is refused before any card is read.
BR-11 becomes the `firstCardNumber`, `lastCardNumber`, `direction` and `pageNumber` parameters,
which carry the browse position the source held in its COMMAREA, and BR-11 of the update becomes
the two-call `validate` then `save` sequence.
BR-12 and BR-13 of the update become the JPA row lock plus a comparison of the whole fetched image
inside one database transaction, which is the same observable behaviour with the source's lock
failure message retained for the timeout case.
