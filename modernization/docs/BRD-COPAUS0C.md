# Business Rules — COPAUS0C (Pending authorization summary, CICS trans CPVS)

Source: `app/app-authorization-ims-db2-mq/cbl/COPAUS0C.cbl` (1032 lines). Map: `COPAU00`
(`COPAU0A`), symbolic map `cpy-bms/COPAU00.cpy`. Copybooks: `CIPAUSMY`, `CIPAUDTY`, `PAUTBPCB`,
`COCOM01Y` (COMMAREA, CPVS section), `CVACT01Y`, `CVACT03Y`, `CVCUS01Y`, `CSMSG01Y`, `COTTL01Y`,
`IMSFUNCS`. Data: IMS HIDAM `DBPAUTP0` (PSB `PSBPAUTL`), VSAM `CARDXREF` account path, `ACCTDAT`,
`CUSTDAT`.

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | An account id is required | The screen cannot be listed without an account id; leaving it empty answers `Please enter Acct Id...`. | `PROCESS-ENTER-KEY` (261) lines 261-270 | Validation | Business-derived | — |
| BR-02 | The account id must be numeric | A non-numeric account id is rejected with `Acct Id must be Numeric ...` and nothing is read. | `PROCESS-ENTER-KEY` lines 272-283 | Validation | Business-derived | — |
| BR-03 | The account id may be supplied by the calling screen | When the screen is entered from another screen with a numeric account id, that id is used and the list is built immediately. | `MAIN-PARA` lines 202-215 (`IF CDEMO-ACCT-ID IS NUMERIC`) | Control Flow | Business-derived | A non-numeric inbound id silently clears the field |
| BR-04 | Cardholder header comes from the account | The screen shows the customer id, the customer's name, two address lines, the first phone number, the account's credit limit and cash credit limit, read through the card cross-reference for the account. | `GATHER-ACCOUNT-DETAILS` (750), `GETCARDXREF-BYACCT` (812), `GETACCTDATA-BYACCT` (865), `GETCUSTDATA-BYCUST` (915) | Data Selection | Business-derived | — |
| BR-05 | Name formatting | The displayed name is the first name, a single space, the initial of the middle name, a space and the last name, with trailing blanks of each name removed. | `GATHER-ACCOUNT-DETAILS` lines 757-765 | Formatting/Conversion | Business-derived | — |
| BR-06 | Address formatting | The first address line is address line 1 and address line 2 separated by a comma; the second is address line 3, the state code and the first five digits of the zip, separated by commas. | `GATHER-ACCOUNT-DETAILS` lines 767-779 | Formatting/Conversion | Business-derived | `DELIMITED BY '  '` truncates at the first double blank |
| BR-07 | Authorization metrics come from the summary | When the account has a pending authorization summary the screen shows its approved count, declined count, credit balance, cash balance, approved amount and declined amount. | `GATHER-ACCOUNT-DETAILS` lines 787-800 | Data Selection | Business-derived | — |
| BR-08 | No summary shows zeros | When the account has no pending authorization summary all six metrics are shown as zero. | `GATHER-ACCOUNT-DETAILS` lines 801-807 | Derivation/Default | Business-derived | — |
| BR-09 | The list is only built when a summary exists | Authorizations are listed only for an account that has a pending authorization summary. | `GATHER-DETAILS` (342) lines 352-355 | Control Flow | Business-derived | — |
| BR-10 | Five authorizations a page | A page shows at most five authorizations. | `PROCESS-PAGE-FORWARD` (415) `PERFORM UNTIL WS-IDX > 5 …`, `POPULATE-AUTH-LIST` `EVALUATE WS-IDX WHEN 1 … WHEN 5` | Data Selection | Hard-coded literal (5) | — |
| BR-11 | Authorizations are listed newest first | Authorizations are listed in the stored order of the authorization key, which is newest first. | `GET-AUTHORIZATIONS` (458) `EXEC DLI GNP SEGMENT(PAUTDTL1)` over the nines-complement key of `CIPAUDTY` | Data Selection | Business-derived | Ordering is a property of the key (BR-31 of COPAUA0C) |
| BR-12 | Row content | Each row shows the transaction id, the original authorization date, the original authorization time, the authorization type, an approval indicator, the match status and the approved amount. | `POPULATE-AUTH-LIST` (522) lines 522-605 | Formatting/Conversion | Business-derived | — |
| BR-13 | Date and time display format | The authorization date is shown as MM/DD/YY and the time as HH:MM:SS, both taken from the original request values. | `POPULATE-AUTH-LIST` lines 526-534 | Formatting/Conversion | Business-derived | Two-digit year (UCR-23) |
| BR-14 | Approval indicator | A row whose response code is `00` is shown as `A`, any other response code as `D`. | `POPULATE-AUTH-LIST` lines 536-540 | Derivation/Default | Business-derived | — |
| BR-15 | Cleared rows before every page | All five rows are blanked and protected before a page is filled, so a short page shows no leftover data. | `INITIALIZE-AUTH-DATA` (608) | Control Flow | Business-derived | — |
| BR-16 | Only a filled row can be selected | The selection field of a row is opened for input only when the row has been filled. | `POPULATE-AUTH-LIST` `MOVE DFHBMUNP TO SEL000nA`, `INITIALIZE-AUTH-DATA` `MOVE DFHBMPRO` | Validation | Business-derived | — |
| BR-17 | The first selected row wins | When more than one row is marked, the first marked row from the top is the one acted on. | `PROCESS-ENTER-KEY` lines 300-322 `EVALUATE TRUE WHEN SEL0001I … WHEN SEL0005I` | Control Flow | Business-derived | — |
| BR-18 | Only `S` selects | A selection of `S` or `s` opens the authorization detail screen for that authorization; any other character answers `Invalid selection. Valid value is S`. | `PROCESS-ENTER-KEY` lines 325-340 | Validation | Business-derived | — |
| BR-19 | Selection carries the authorization key | Opening the detail screen passes the account id and the selected authorization's key. | `PROCESS-ENTER-KEY` lines 305-341, `CDEMO-CPVS-PAU-SELECTED` | Control Flow | Business-derived | — |
| BR-20 | Paging forward remembers the last key | The key of the last authorization shown on a page is remembered, and paging forward continues from it. | `PROCESS-PAGE-FORWARD` lines 439-443, `PROCESS-PF8-KEY` (388) lines 393-400, `REPOSITION-AUTHORIZATIONS` (488) | Control Flow | Business-derived | — |
| BR-21 | Paging back remembers the first key of each page | The key that starts each page is remembered by page number, and paging back re-reads from the start of the previous page. | `PROCESS-PAGE-FORWARD` lines 444-449, `PROCESS-PF7-KEY` (362) | Control Flow | Business-derived | Page-key table is bounded by the COMMAREA array size — REVIEW REQUIRED |
| BR-22 | Top of list message | Paging back from the first page answers `You are already at the top of the page...`. | `PROCESS-PF7-KEY` lines 380-385 | Exception Handling | Business-derived | — |
| BR-23 | Bottom of list message | Paging forward when there is no further authorization answers `You are already at the bottom of the page...`. | `PROCESS-PF8-KEY` lines 405-413 | Exception Handling | Business-derived | — |
| BR-24 | One read ahead decides whether there is a next page | After filling a page one more authorization is read to decide whether a further page exists. | `PROCESS-PAGE-FORWARD` lines 452-457 | Control Flow | Business-derived | — |
| BR-25 | End of the child chain ends the list | Not finding a further authorization, or reaching the end of the database, ends the list normally. | `GET-AUTHORIZATIONS` lines 466-470, `REPOSITION-AUTHORIZATIONS` lines 500-504 | Control Flow | Business-derived | Status treated as business logic |
| BR-26 | Read failures are reported on the screen | Any other failure reading an authorization, repositioning the list or reading the summary is shown as a system error carrying the database status code. | `GET-AUTHORIZATIONS` lines 471-480, `REPOSITION-AUTHORIZATIONS` lines 505-514, `GET-AUTH-SUMMARY` (966) lines 985-996 | Exception Handling | Business-derived | Message text carries a technical code to the user |
| BR-27 | Database work is committed when the screen is sent | The IMS position is released and the unit of work committed whenever the screen is sent. | `SEND-PAULST-SCREEN` (681) lines 683-688 | Control Flow | Business-derived | — |
| BR-28 | Screen header | The screen shows the title, the transaction id, the program name, and the current date and time. | `POPULATE-HEADER-INFO` (726) | Formatting/Conversion | Business-derived | — |
| BR-29 | An unrecognised key is rejected | Any key other than Enter, PF3, PF7 or PF8 answers the common invalid-key message. | `MAIN-PARA` lines 253-258, `CCDA-MSG-INVALID-KEY` of `CSMSG01Y` | Exception Handling | Business-derived | Terminal-specific mechanism, retired per playbook §8.4.5 |
| BR-30 | Leaving the screen returns to the caller | Leaving the screen returns to the calling program, or to sign-on when there is none. | `MAIN-PARA` lines 235-239, `RETURN-TO-PREV-SCREEN` (665) | Control Flow | Business-derived | — |

## Exception scenarios (screen and database)

| Condition | Source treatment | Target treatment |
|---|---|---|
| IMS status blank on `GU`/`GNP` | continue | row returned |
| `GE` (segment not found) | end of list / no summary | empty page / `summaryFound = false` |
| `GB` (end of database) | end of list | empty page |
| any other IMS status | `System error while reading AUTH Details: Code:nn` on the screen | `ApiError` with HTTP 500 and the same message text |
| account not in cross-reference / account master / customer master | field-level message from the read paragraph | `ScreenValidationException` mapped to HTTP 404 with the source message |

## Target implementation

`AuthorizationController.summary` (`GET /api/authorizations?accountId=&afterAuthKey=`),
`AuthorizationInquiryService.summary`, `AuthorizationListRow`, `AuthorizationSummaryResponse`,
`CardholderLookupClient`, `AuthorizationSummaryRepository.findById`,
`AuthorizationDetailRepository.findPage`; React `AuthorizationSummaryScreen.tsx`. Screen-field
mapping is in `06-screen-mapping.md`.
