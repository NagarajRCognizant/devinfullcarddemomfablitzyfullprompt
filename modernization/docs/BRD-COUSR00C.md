# Business Rules — COUSR00C (user list)

Source: `app/cbl/COUSR00C.cbl` (695 lines), map `app/bms/COUSR00.bms`, record layout
`app/cpy/CSUSR01Y.cpy`. Transaction `CU00`, admin menu option 1.
The program browses the USRSEC file and lets the administrator pick a user for update or delete.

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | Ten users are listed at a time | The list shows at most ten users per page. | `WS-USER-DATA` `OCCURS 10`, the loops of `PROCESS-PAGE-FORWARD` line 300 (`UNTIL WS-IDX >= 11`) and `PROCESS-PAGE-BACKWARD` line 354 | Data Selection | Business-derived | Ten is a hard-coded literal repeated in the loops, the map and the field names |
| BR-02 | The list is ordered by user id | Users appear in ascending user id order, because the file is browsed on its key. | `STARTBR-USER-SEC-FILE` lines 588-595 with `READNEXT`/`READPREV` | Data Selection | Business-derived | — |
| BR-03 | A keyed user id positions the list | Entering a user id starts the list at that id; leaving it blank starts at the first user on file. | `PROCESS-ENTER-KEY` lines 218-222 (`MOVE LOW-VALUES TO SEC-USR-ID` when blank) | Data Selection | Business-derived | The keyed value is not upper-cased here, unlike sign-on, so a lower-case id positions the browse differently |
| BR-04 | Paging is by key, not by row count | Forward paging restarts the browse at the last id shown and backward paging at the first id shown, so the page follows the key and not a row offset. | `PROCESS-PF8-KEY` lines 262-266 (`CDEMO-CU00-USRID-LAST`), `PROCESS-PF7-KEY` lines 239-243 (`CDEMO-CU00-USRID-FIRST`) | Data Selection | Business-derived | Records inserted or deleted between two pages therefore change what the next page shows |
| BR-05 | The page number is kept across pages | The displayed page number increases by one when moving forward and decreases by one when moving back, and never falls below one. | `PROCESS-PAGE-FORWARD` lines 309-322, `PROCESS-PAGE-BACKWARD` lines 362-372 | Calculation | Business-derived | The counter lives in the COMMAREA; it is a display value only and is not used to read data |
| BR-06 | The forward read looks one record ahead | After filling a page the program reads one further record only to decide whether a next page exists. | `PROCESS-PAGE-FORWARD` lines 308-316 (`SET NEXT-PAGE-YES`) | Derivation/Default | Business-derived | — |
| BR-07 | Paging past the first page is refused | Requesting the previous page while on page one leaves the page displayed with "You are already at the top of the page...". | `PROCESS-PF7-KEY` lines 248-255 | Exception Handling | Business-derived | — |
| BR-08 | Paging past the last page is refused | Requesting the next page when no further record was found leaves the page displayed with "You are already at the bottom of the page...". | `PROCESS-PF8-KEY` lines 270-277 | Exception Handling | Business-derived | — |
| BR-09 | Reaching the end of the file during a page is reported | A page that ends because the file ran out reports "You have reached the bottom of the page..." and shows the rows that were read. | `READNEXT-USER-SEC-FILE` lines 634-640 | Exception Handling | Business-derived | The same paragraph reports "You have reached the top of the page..." for backwards reads (`READPREV`, lines 668-674) |
| BR-10 | A browse that cannot be positioned is reported as the top of the file | A browse that cannot start because no record matches the key reports "You are at the top of the page..." and shows no rows. | `STARTBR-USER-SEC-FILE` lines 600-606 | Exception Handling | Business-derived | Message wording differs from BR-07 by one word ("are at" versus "are already at") — preserved |
| BR-11 | Any other file failure is reported as a lookup failure | A browse, forward read or backward read that fails for any other reason reports "Unable to lookup User..." and stops the page. | `STARTBR`/`READNEXT`/`READPREV` `WHEN OTHER` (lines 607-613, 641-647, 675-681) | Exception Handling | Business-derived | The response codes are only written to the log with `DISPLAY`, never shown or stored |
| BR-12 | One row may be selected per interaction | The first row carrying a selection value is the row that is acted on; values on later rows are ignored. | `PROCESS-ENTER-KEY` lines 151-185 (EVALUATE over `SEL0001I`-`SEL0010I`, first match wins) | Data Selection | Business-derived | Silently ignoring the other selections is preserved |
| BR-13 | "U" opens user update and "D" opens user delete | A row selected with U or u continues to the user update screen and a row selected with D or d to the user delete screen, carrying the selected user id. | `PROCESS-ENTER-KEY` lines 189-209 | Control Flow | Business-derived | — |
| BR-14 | Any other selection value is refused | A selection value other than U, u, D or d is refused with "Invalid selection. Valid values are U and D". | `PROCESS-ENTER-KEY` lines 210-215 | Validation | Business-derived | The message is not terminated with the ellipsis used elsewhere; the program continues to re-list the page after reporting it |
| BR-15 | A selection is only acted on with a user id | A selection value is acted on only when the row also carries a user id, so a selection typed against an empty row does nothing. | `PROCESS-ENTER-KEY` lines 187-188 | Validation | Business-derived | — |
| BR-16 | Exit returns to the administration menu | Pressing the exit key returns the operator to the administration menu. | `MAIN-PARA` lines 125-127 | Control Flow | Business-derived | On first entry with no conversation state the program returns to sign-on instead (lines 110-112) |
| BR-17 | Any other key is refused | A key other than Enter, exit, previous page or next page redisplays the list with the standard invalid-key message. | `MAIN-PARA` lines 132-136 | Exception Handling | Business-derived | — |

## Exception scenarios (file status)

| Response | Operation | Behaviour |
|---|---|---|
| NORMAL | STARTBR / READNEXT / READPREV | Processing continues |
| NOTFND | STARTBR | BR-10: "You are at the top of the page...", no rows |
| ENDFILE | READNEXT | BR-09: "You have reached the bottom of the page..." |
| ENDFILE | READPREV | BR-09: "You have reached the top of the page..." |
| any other | all three | BR-11: "Unable to lookup User...", error flag set, page abandoned |

## Target implementation

`UserListService` (identity service) implements BR-01 through BR-11 with the three browse entry
points modelled as `Direction.FIRST`, `NEXT` and `PREVIOUS`, each a keyed query
(`>= key`, `> key`, `< key` ordered descending) with `Limit` of eleven or ten records — the
one-record look-ahead of BR-06. `GET /api/users?direction=&userId=&pageNumber=` returns the ten
rows, the first and last id of the page (the two COMMAREA fields of BR-04), the page number of BR-05
and the message of BR-07 through BR-10 where one applies.

BR-12 through BR-15 are screen rules: the React `UserListScreen` accepts one selection at a time,
routes U and D to the update and delete screens with the selected id, and shows the verbatim message
of BR-14 for anything else. BR-16 and BR-17 are AID rules with no HTTP counterpart and are recorded
in `docs/11-unsupported-construct-register.md`.
