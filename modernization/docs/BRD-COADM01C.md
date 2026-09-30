# Business Rules — COADM01C (administration menu)

Source: `app/cbl/COADM01C.cbl` (288 lines) with the option table `app/cpy/COADM02Y.cpy` and map
`app/bms/COADM01.bms`. Transaction `CA00`. Reached from sign-on when the operator's user type is
"A" (see `BRD-COSGN00C.md`, BR-10).

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | The menu offers six administration options | The menu presents exactly the options held in the option table: user list, user add, user update, user delete, transaction type list/update and transaction type maintenance. | `COADM02Y` (`CDEMO-ADMIN-OPT-COUNT` = 6, options 1-4 → `COUSR00C`-`COUSR03C`, 5 → `COTRTLIC`, 6 → `COTRTUPC`) | Data Selection | Business-derived | The count is a hard-coded literal in the copybook and must be changed with the table |
| BR-02 | Reaching the menu requires an administrator | Only an operator who signed on with user type "A" reaches this menu; the menu itself performs no further check on the operator. | `COSGN00C` `READ-USER-SEC-FILE` lines 230-234; no operator-type test exists in `COADM01C` | Validation | Business-derived | The menu trusts the COMMAREA: a program transferring to `COADM01C` with a non-administrator COMMAREA would not be refused |
| BR-03 | Trailing blanks in the keyed option are ignored | The keyed option is trimmed of trailing blanks before it is evaluated. | `PROCESS-ENTER-KEY` lines 121-126 (backwards `PERFORM VARYING` over `OPTIONI`) | Formatting/Conversion | Business-derived | — |
| BR-04 | Blanks inside the option are read as zeros | Remaining blank positions of the option are replaced by zeros before it is treated as a number. | `PROCESS-ENTER-KEY` line 127 (`INSPECT ... REPLACING ALL ' ' BY '0'`) | Formatting/Conversion | Business-derived | A fully blank option therefore becomes zero and is refused by BR-05 |
| BR-05 | The option must be a number on the menu | An option that is not numeric, is zero, or is greater than the number of options is refused with "Please enter a valid option number...". | `PROCESS-ENTER-KEY` lines 131-138 | Validation | Business-derived | One message covers all three causes |
| BR-06 | A valid option starts its program | A valid option starts the program named for that option, recording this menu as the calling transaction and program. | `PROCESS-ENTER-KEY` lines 140-149 | Control Flow | Business-derived | — |
| BR-07 | Placeholder options report that they are not installed | An option whose program name begins with "DUMMY" is not started; the menu reports "This option is not installed ...". | `PROCESS-ENTER-KEY` lines 141, 150-157 | Exception Handling | Business-derived | No option in the supplied table starts with DUMMY, so the branch is unreachable with the shipped copybook; the option name was commented out of the message, so the text never says *which* option — both preserved, not repaired |
| BR-08 | Exit returns to sign-on | Pressing the exit key returns the operator to the sign-on transaction. | `MAIN-PARA` lines 100-102, `RETURN-TO-SIGNON-SCREEN` lines 163-170 | Control Flow | Business-derived | The transfer omits the COMMAREA, so the signed-on identity is dropped on the way back |
| BR-09 | Any other key is refused | A key other than Enter or the exit key redisplays the menu with the standard invalid-key message. | `MAIN-PARA` lines 103-106 | Exception Handling | Business-derived | — |

## Target implementation

`AdminMenuService` (identity service) holds the six options of `COADM02Y` and implements BR-01 and
BR-03 through BR-06: `GET /api/admin-menu` returns the option list and
`GET /api/admin-menu/selection?option=` returns the program to run or the single validation message.
BR-02 becomes an authorisation rule instead of an inherited COMMAREA state: both endpoints require
`ROLE_ADMIN`, which is granted only for user type "A", so a non-administrator token receives 403
rather than a menu.

BR-07's placeholder branch has no counterpart because the shipped table contains no DUMMY entry; it
is recorded in `docs/11-unsupported-construct-register.md` together with the suppressed option name.
Options 5 and 6 address the Db2 transaction-type programs, which are outside this capability: the
React admin menu reports them as not part of the converted scope instead of silently doing nothing.
BR-08 and BR-09 are terminal navigation rules; the React client returns to sign-on and drops the
token, which is the observable equivalent of the identity being dropped.
