# Consolidated Business Rule Report — CardDemo Sign-on and User Administration

Cross-program view of the 65 rules extracted from the identity closure. The per-program catalogues
hold the full rows (statement, technical basis, classification, origin, risk flags):

| Program | Role | Rules | Catalogue |
|---|---|---|---|
| `COSGN00C` | CICS sign-on (CC00) | BR-01 … BR-12 (12) | `BRD-COSGN00C.md` |
| `COADM01C` | CICS administration menu (CA00) | BR-01 … BR-09 (9) | `BRD-COADM01C.md` |
| `COUSR00C` | CICS user list (CU00) | BR-01 … BR-17 (17) | `BRD-COUSR00C.md` |
| `COUSR01C`/`COUSR02C`/`COUSR03C` | CICS user add (CU01), update (CU02), delete (CU03) | BR-01 … BR-27 (27) | `BRD-COUSR01C-02C-03C.md` |

Rule IDs are sequential per catalogue, so a rule is cited as `COUSR02C BR-13`. The account-management
closure is reported separately in `BRE-Report-CARDDEMO-ACCOUNT.md`; the two meet at `COSGN00C` BR-10,
which decides whether the operator reaches the administration menu or the main menu.

## 1. Business capability → FR → BR

| Capability | FR | Requirement | Rules |
|---|---|---|---|
| Sign-on | FR-19 | An operator identifies himself with a user id and a password, both required | COSGN00C BR-01, BR-02, BR-03 |
| Sign-on | FR-20 | Credentials are folded to upper case and the user is retrieved by user id | COSGN00C BR-04, BR-05 |
| Sign-on | FR-21 | The password must match the stored password, and a mismatch is reported distinctly from an unknown user | COSGN00C BR-06, BR-07 |
| Sign-on | FR-22 | The signed-on user id and user type are carried into the screens that follow | COSGN00C BR-09 |
| Sign-on | FR-23 | Administrators continue to the administration menu, all other operators to the main menu | COSGN00C BR-10; COMEN01C BR-03 |
| Sign-on | FR-24 | A verification failure other than an unknown user prevents sign-on | COSGN00C BR-08 |
| Administration menu | FR-25 | The menu offers the six administration options and only to administrators | COADM01C BR-01, BR-02 |
| Administration menu | FR-26 | The keyed option is normalised, then validated against the option table with one message | COADM01C BR-03, BR-04, BR-05 |
| Administration menu | FR-27 | A valid option starts its program; a placeholder option reports that it is not installed | COADM01C BR-06, BR-07 |
| User browsing | FR-28 | Users are listed ten at a time in user id order | COUSR00C BR-01, BR-02 |
| User browsing | FR-29 | A keyed user id positions the list and paging follows the key of the page shown | COUSR00C BR-03, BR-04, BR-06 |
| User browsing | FR-30 | The page number is maintained across pages and never falls below one | COUSR00C BR-05 |
| User browsing | FR-31 | Paging beyond either end of the file is refused with the position message | COUSR00C BR-07, BR-08, BR-09, BR-10 |
| User browsing | FR-32 | One listed user may be selected for update or delete; any other selection value is refused | COUSR00C BR-12, BR-13, BR-14, BR-15 |
| User maintenance | FR-33 | A user record is a fixed-width record keyed by user id | COUSR01C BR-01, BR-02 |
| User maintenance | FR-34 | Adding a user requires all five fields and a user id that is not already used | COUSR01C BR-03, BR-04, BR-05, BR-06 |
| User maintenance | FR-35 | Updating a user fetches the stored record first and requires the four editable fields | COUSR02C BR-09, BR-10, BR-11, BR-12 |
| User maintenance | FR-36 | An update that changes nothing is refused and nothing is written | COUSR02C BR-13, BR-14 |
| User maintenance | FR-37 | A stored update or delete is confirmed naming the user id | COUSR02C BR-15, COUSR03C BR-25 |
| User maintenance | FR-38 | Deleting a user shows the user first and requires an explicit confirmation | COUSR03C BR-20, BR-21, BR-22, BR-23 |
| User maintenance | FR-39 | Missing users and storage failures are reported with the source wording | COUSR01C BR-07, COUSR02C BR-11, BR-16, BR-17, COUSR03C BR-24, BR-26 |

## 2. Rules by classification

| Classification | Count | Examples |
|---|---|---|
| Exception Handling | 18 | COSGN00C BR-07, BR-08; COUSR00C BR-07 … BR-11; COUSR01C BR-05, BR-07; COUSR03C BR-26 |
| Control Flow | 16 | COSGN00C BR-01, BR-10; COADM01C BR-06; COUSR02C BR-18, BR-19; COUSR03C BR-22 |
| Validation | 13 | COSGN00C BR-02, BR-03, BR-06; COADM01C BR-05; COUSR00C BR-14; COUSR01C BR-03; COUSR02C BR-12 … BR-14 |
| Data Selection | 10 | COSGN00C BR-05; COADM01C BR-01; COUSR00C BR-01 … BR-04, BR-12; COUSR02C BR-09 |
| Formatting/Conversion | 5 | COSGN00C BR-04; COADM01C BR-03, BR-04; COUSR01C BR-01, BR-04 |
| Derivation/Default | 2 | COSGN00C BR-09; COUSR00C BR-06 |
| Calculation | 1 | COUSR00C BR-05 (the page counter) |

Total 65. The single `Calculation` rule is the page counter of the user list — this capability
performs no business arithmetic at all: it authenticates, lists and maintains one fixed-width record.
Every `COMPUTE` in the closure is either that counter or a row index (`COUSR00C`
`PROCESS-PAGE-FORWARD`/`PROCESS-PAGE-BACKWARD`).

## 3. Cross-program rule agreement (corroboration)

Rules corroborated by more than one artifact carry **High** confidence:

| Behaviour | Corroborating artifacts |
|---|---|
| Eight-character user id as the key | `COSGN00C` BR-05, `COUSR01C` BR-02, `CSUSR01Y` `PIC X(08)`, `app/jcl/DUSRSECJ.jcl` `KEYS(8,0)` on `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS`, `app/csd/CARDDEMO.CSD` line 88 `DEFINE FILE(USRSEC)` |
| Eighty-byte fixed-width record | `COUSR01C` BR-01, `CSUSR01Y` (8+20+20+8+1+23), `DUSRSECJ.jcl` `RECORDSIZE(80,80)`, the seed records of `app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS` |
| User type "A" means administrator | `COSGN00C` BR-10, `COCOM01Y` `88 CDEMO-USRTYP-ADMIN VALUE 'A'`, `COMEN01C` admin-only check, the seed records |
| Six administration options | `COADM01C` BR-01, `COADM02Y` table and `CDEMO-ADMIN-OPT-COUNT`, `CARDDEMO.CSD` transactions `CU00`-`CU03` |
| Ten rows per page | `COUSR00C` BR-01, `COUSR00.bms` map fields `USRID01`-`USRID10`, the `OCCURS 10` array |
| Keyed paging fields | `COUSR00C` BR-04, `CDEMO-CU00-USRID-FIRST`/`-LAST` in the COMMAREA extension |
| Selection values U and D | `COUSR00C` BR-13, BR-14, the two `XCTL` targets `COUSR02C`/`COUSR03C`, `COUSR00.bms` selection field |

Rules with **Medium** confidence and their reason:

| Rule | Reason |
|---|---|
| `COADM01C` BR-07 (placeholder options) | unreachable with the shipped option table; behaviour known only from the code |
| `COUSR02C` BR-18 (exit saves first) | the paragraph both stores and navigates, and continues navigating after a validation failure — intent unclear |
| `COUSR03C` BR-23 (read result not tested before delete) | the delete runs whatever the read reported; whether this is intended cannot be determined from the source |
| `COUSR00C` BR-03 (filter not folded to upper case) | inconsistent with sign-on (BR-04); no comment or corroborating artifact explains the difference |

## 4. Mandatory risk flags — consolidated

| Flag | Occurrences |
|---|---|
| Hard-coded literal counts and bounds | `COUSR00C` BR-01 (ten rows, repeated in the loops, the map and the field names), `COADM01C` BR-01 (`CDEMO-ADMIN-OPT-COUNT`) → UCR-07 |
| Clear-text credentials | `COSGN00C` BR-06 (password stored and compared in clear text, no hashing, expiry, history or lock-out), `COUSR02C` BR-09 (the stored password is displayed on the update screen) → UCR-14 |
| Unchecked return code / result | `COUSR03C` BR-23 (the read-for-update result is not tested before the delete is attempted); `COUSR00C` BR-11 and `COUSR01C` BR-07 (response codes only reach `DISPLAY`, and in `COUSR01C` that `DISPLAY` is commented out) → UCR-15 |
| Wrong or incomplete message text | `COUSR03C` BR-26 (a failed delete reports "Unable to Update User..."), `COADM01C` BR-07 (the option name was commented out of the "not installed" message), `COUSR00C` BR-14 (the only message in the closure without a trailing ellipsis) → UCR-16 |
| Duplicate / near-duplicate conditions | `COUSR00C` BR-07 versus BR-10 ("You are already at the top of the page..." versus "You are at the top of the page..." for two different causes) → UCR-16 |
| Missing ELSE / WHEN OTHER | `COSGN00C` BR-10 (no validation of a stored user type other than A or U — anything unexpected silently becomes a regular operator); `COUSR01C` BR-04 (no validation of the keyed user type) → UCR-11 |
| Information disclosure as observable behaviour | `COSGN00C` BR-07 (distinct messages reveal whether a user id exists) → UCR-14 |
| Dead / commented-out logic | `COADM01C` BR-07 (`CDEMO-ADMIN-OPT-NAME` in the message), `COUSR01C` BR-07 (`DISPLAY` of the response codes), the commented `GTEQ` of `STARTBR-USER-SEC-FILE` (`COUSR00C` line 592) → UCR-13 |
| Selections silently ignored | `COUSR00C` BR-12 (only the first selected row is acted on, the rest are discarded without a message) → UCR-16 |

## 5. Exception scenarios (file and I/O behaviour as business logic)

File response codes are treated as business logic: NORMAL success, NOTFND/ENDFILE the normal
"nothing there" outcome, anything else an error path.

| Scenario | Source behaviour | Rules | Target behaviour |
|---|---|---|---|
| Read of the security file, NORMAL | sign-on continues; maintenance screens show the record | COSGN00C BR-06, COUSR02C BR-09, COUSR03C BR-20 | 200 with the record, or the sign-on token |
| Read, NOTFND (sign-on) | "User not found. Try again ..." | COSGN00C BR-07 | 401 carrying the same text |
| Password mismatch | "Wrong Password. Try again ..." | COSGN00C BR-06, BR-07 | 401 carrying the same text |
| Read, any other response (sign-on) | "Unable to verify the User ..." | COSGN00C BR-08 | 500 carrying the same text |
| Read, NOTFND (maintenance) | "User ID NOT found..." | COUSR02C BR-11, COUSR03C BR-24 | 404 carrying the same text |
| Read, any other response (maintenance) | "Unable to lookup User..." | COUSR02C BR-11 note, COUSR00C BR-11 | 500 carrying the same text |
| STARTBR, NOTFND | "You are at the top of the page...", no rows | COUSR00C BR-10 | 200 with an empty list and the same message |
| READNEXT, ENDFILE | "You have reached the bottom of the page..." | COUSR00C BR-09 | 200 with the partial page and the same message |
| READPREV, ENDFILE | "You have reached the top of the page..." | COUSR00C BR-09 | 200 with the partial page, page number forced to one |
| Browse, any other response | "Unable to lookup User...", page abandoned | COUSR00C BR-11 | 500 carrying the same text |
| WRITE, DUPKEY/DUPREC | "User ID already exist..." | COUSR01C BR-05 | 409 carrying the same text (primary key violation) |
| WRITE, any other response | "Unable to Add User..." | COUSR01C BR-07 | 500 carrying the same text |
| REWRITE, NORMAL | "User <id> has been updated ..." | COUSR02C BR-15 | 200 carrying the same text |
| REWRITE/DELETE, any other response | "Unable to Update User..." | COUSR02C BR-17, COUSR03C BR-26 | 500 carrying the same text, including the wrong wording for delete |
| DELETE, NORMAL | "User <id> has been deleted ...", screen cleared | COUSR03C BR-25 | 200 carrying the same text |

## 6. Where each rule is implemented and proved

`14-rtm.md` carries every rule row by row: ticket, rule, source location, target class and method,
React component, API operation, PostgreSQL object, validation test and status.
