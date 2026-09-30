# Business Rules — COMEN01C (main menu) — access rules only, partial scope

Source: `app/cbl/COMEN01C.cbl` (299 lines) with the option table `app/cpy/COMEN02Y.cpy`.
COMEN01C is reachable from both seed programs (`XCTL` to `LIT-MENUPGM = 'COMEN01C'` on exit) and
is the program that decides whether an operator may reach Account View and Account Update.

**Scope statement.** Only the option-selection and access rules that govern entry into the two
in-scope transactions are extracted and implemented. The menu's own screen handling and the other
nine options (card list/detail/update, transactions, reports, bill payment) are outside
the Account Management capability; they are recorded as *reachable but deliberately excluded* in
`02-source-inventory-and-coverage.md` with disposition **Manual Review Required**.

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | An option number must be selected | The operator must key a numeric option that exists on the menu and is not zero; otherwise "Please enter a valid option number...". | `PROCESS-ENTER-KEY` lines 128-134 (`NOT NUMERIC OR > CDEMO-MENU-OPT-COUNT OR = ZEROS`) | Validation | Business-derived | The paragraph continues after the error is set instead of returning — the following rules still evaluate with an out-of-range option |
| BR-02 | Blanks in the option are read as zeros | Blank positions inside the keyed option are treated as zeros before the option is evaluated. | `PROCESS-ENTER-KEY` lines 122-125 (`INSPECT WS-OPTION-X REPLACING ALL ' ' BY '0'`) | Formatting/Conversion | Business-derived | — |
| BR-03 | Administrator-only options are refused to regular users | A regular (non-administrator) operator selecting an option marked as administrator-only is refused with "No access - Admin Only option... ". | `PROCESS-ENTER-KEY` lines 136-145 | Validation | Business-derived | — |
| BR-04 | Account View and Account Update are open to regular users | Option 1 (Account View) and option 2 (Account Update) are marked as available to regular users, so no operator type restriction applies to either in-scope transaction. | `COMEN02Y` lines 20-32: option 1 → `COACTVWC`/`'U'`, option 2 → `COACTUPC`/`'U'` | Data Selection | Business-derived | The administrator-only branch is therefore unreachable for both in-scope options — recorded, not removed |
| BR-05 | Unavailable and placeholder options are reported, not entered | An option whose program is not installed, or whose program name begins with "DUMMY", is reported as unavailable instead of being started. | `PROCESS-ENTER-KEY` lines 147-180 | Exception Handling | Business-derived | Applies only to out-of-scope options in the supplied table |
| BR-06 | The menu remembers who called the transaction | Starting an option records the menu as the calling transaction and program, which is what the in-scope transactions use to decide where the exit action returns to. | `PROCESS-ENTER-KEY` lines 182-187 (`CDEMO-FROM-TRANID`, `CDEMO-FROM-PROGRAM`) | Control Flow | Business-derived | — |
| BR-07 | Exit from the menu returns to sign-on | Leaving the menu returns the operator to the sign-on transaction. | `WHEN DFHPF3` → `MOVE 'COSGN00C'`, `RETURN-TO-SIGNON-SCREEN` | Control Flow | Business-derived | Sign-on is converted in the identity service (`COSGN00C`) |

## Target implementation

`MenuAccessService` holds the in-scope portion of the option table (option number, name, program,
required operator type) and reproduces BR-01 through BR-04 and BR-06. The operator type is derived
from the verified JWT issued by the identity service at sign-on and served by `GET /api/menu`
(`MenuController`); it is never supplied by the caller. See `docs/15-local-execution-guide.md`.
