# Business Rules — COUSR01C / COUSR02C / COUSR03C (user add, update, delete)

The three programs are catalogued together because they maintain one record through one layout and
share the record rules below; rule numbering is sequential across the combined catalogue and each
section names the program a rule belongs to, so a rule is cited as `COUSR02C BR-13`.

Sources: `app/cbl/COUSR01C.cbl` (299 lines, transaction `CU01`), `app/cbl/COUSR02C.cbl` (414 lines,
`CU02`), `app/cbl/COUSR03C.cbl` (359 lines, `CU03`); maps `app/bms/COUSR01.bms`, `COUSR02.bms`,
`COUSR03.bms`; record layout `app/cpy/CSUSR01Y.cpy`. Admin menu options 2, 3 and 4; options 3 and 4
are also reached from the user list (see `BRD-COUSR00C.md`, BR-13).

## Shared record rules

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | A user record is fixed width | A user record holds an eight-character user id, a twenty-character first name, a twenty-character last name, an eight-character password and a one-character user type, padded with blanks to eighty bytes. | `CSUSR01Y` (`SEC-USR-ID PIC X(08)`, `FNAME`/`LNAME PIC X(20)`, `PWD PIC X(08)`, `TYPE PIC X(01)`, `FILLER PIC X(23)`) | Formatting/Conversion | Business-derived | Longer input is truncated silently by the MOVE; there is no length validation on any field |
| BR-02 | The user id is the key | A user is identified by its user id alone. | `RIDFLD(SEC-USR-ID)` on the read, write, rewrite and delete of all three programs | Data Selection | Business-derived | — |

## COUSR01C — add a user

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-03 | All five fields are required | An add is refused unless first name, last name, user id, password and user type are all present, reported in that order with one message each. | `PROCESS-ENTER-KEY` lines 117-151 | Validation | Business-derived | Only the first missing field is reported; the checks are branches of one EVALUATE |
| BR-04 | The keyed values are stored as keyed | The five keyed values are stored without conversion — the user id is not folded to upper case and the user type is not checked against the permitted values. | `PROCESS-ENTER-KEY` lines 153-160 | Formatting/Conversion | Business-derived | A user added with a lower-case id can never sign on, because sign-on upper-cases the id (BR-04 of `BRD-COSGN00C.md`); a user type other than A or U is accepted and behaves as a regular user |
| BR-05 | A duplicate user id is refused | Adding a user id that already exists is refused with "User ID already exist...". | `WRITE-USER-SEC-FILE` lines 260-266 (`DUPKEY`, `DUPREC`) | Exception Handling | Business-derived | — |
| BR-06 | A successful add clears the screen and confirms | A stored user is confirmed with "User <id> has been added ..." and the entry fields are cleared for the next add. | `WRITE-USER-SEC-FILE` lines 250-259, `INITIALIZE-ALL-FIELDS` lines 287-295 | Control Flow | Business-derived | — |
| BR-07 | Any other write failure is reported | A write that fails for any other reason is reported as "Unable to Add User...". | `WRITE-USER-SEC-FILE` lines 267-273 | Exception Handling | Business-derived | Response codes are only written to the log, and that `DISPLAY` is commented out |
| BR-08 | The screen can be cleared without storing anything | The clear key empties all five fields without writing a record. | `MAIN-PARA` lines 96-97, `CLEAR-CURRENT-SCREEN` lines 279-282 | Control Flow | Business-derived | — |

## COUSR02C — update a user

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-09 | The stored user is fetched before editing | Entering a user id fetches that user and fills first name, last name, password and user type from the stored record, then invites the operator to save with "Press PF5 key to save your updates ...". | `PROCESS-ENTER-KEY` lines 157-172, `READ-USER-SEC-FILE` lines 333-339 | Data Selection | Business-derived | The stored password is displayed on the screen in clear text |
| BR-10 | A user id is required to fetch | A fetch or a save without a user id is refused with "User ID can NOT be empty...". | `PROCESS-ENTER-KEY` lines 145-155, `UPDATE-USER-INFO` lines 179-185 | Validation | Business-derived | — |
| BR-11 | An unknown user id is reported | A user id that is not on file is reported as "User ID NOT found...". | `READ-USER-SEC-FILE` lines 340-345 | Exception Handling | Business-derived | — |
| BR-12 | All four editable fields are required to save | A save is refused unless first name, last name, password and user type are all present. | `UPDATE-USER-INFO` lines 186-213 | Validation | Business-derived | Only the first missing field is reported |
| BR-13 | Only a changed field is written | A save compares each editable field with the stored record and only writes when at least one of them differs. | `UPDATE-USER-INFO` lines 219-237 (`USR-MODIFIED-YES`) | Validation | Business-derived | Comparison is fixed-width text, so a change in trailing blanks alone counts as no change |
| BR-14 | An unchanged record is refused | A save in which nothing differs is refused with "Please modify to update ...". | `UPDATE-USER-INFO` lines 238-243 | Validation | Business-derived | — |
| BR-15 | A successful update confirms with the user id | A stored update is confirmed with "User <id> has been updated ...". | `UPDATE-USER-SEC-FILE` lines 369-376 | Control Flow | Business-derived | — |
| BR-16 | A user removed between fetch and save is reported | An update whose record no longer exists is reported as "User ID NOT found...". | `UPDATE-USER-SEC-FILE` lines 377-382 | Exception Handling | Business-derived | The record is read for update, which holds it for the duration of the task only |
| BR-17 | Any other update failure is reported | An update that fails for any other reason is reported as "Unable to Update User...". | `UPDATE-USER-SEC-FILE` lines 383-389 | Exception Handling | Business-derived | — |
| BR-18 | Exit saves first | Pressing the exit key attempts the save before returning to the calling screen. | `MAIN-PARA` lines 111-119 | Control Flow | Business-derived | The exit key both stores data and navigates; a validation failure raised by the save is reported *and* the program still leaves the screen |
| BR-19 | Cancel returns without saving | The cancel key returns to the administration menu without attempting a save. | `MAIN-PARA` lines 124-126 | Control Flow | Business-derived | — |

## COUSR03C — delete a user

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-20 | The user is shown before deletion | Entering a user id shows that user's first name, last name and user type and invites confirmation with "Press PF5 key to delete this user ...". | `PROCESS-ENTER-KEY` lines 156-169, `READ-USER-SEC-FILE` lines 280-286 | Data Selection | Business-derived | The password is not shown on the delete screen |
| BR-21 | A user id is required | A fetch or a delete without a user id is refused with "User ID can NOT be empty...". | `PROCESS-ENTER-KEY` lines 144-154, `DELETE-USER-INFO` lines 176-186 | Validation | Business-derived | — |
| BR-22 | Deletion requires explicit confirmation | The record is only deleted when the confirmation key is pressed; Enter only fetches and displays. | `MAIN-PARA` lines 121-122 (`WHEN DFHPF5 PERFORM DELETE-USER-INFO`) | Control Flow | Business-derived | — |
| BR-23 | The record is re-read before deletion | The delete re-reads the record for update immediately before deleting it. | `DELETE-USER-INFO` lines 188-192 | Control Flow | Business-derived | The read's outcome is not tested before the delete is attempted: a "User ID NOT found..." from the read is reported and the delete still runs |
| BR-24 | An unknown user id is reported | A user id that is not on file is reported as "User ID NOT found...", both on fetch and on delete. | `READ-USER-SEC-FILE` lines 287-292, `DELETE-USER-SEC-FILE` lines 323-328 | Exception Handling | Business-derived | — |
| BR-25 | A successful delete clears the screen and confirms | A deleted user is confirmed with "User <id> has been deleted ..." and the screen is cleared. | `DELETE-USER-SEC-FILE` lines 314-322 | Control Flow | Business-derived | — |
| BR-26 | Any other delete failure is reported as an update failure | A delete that fails for any other reason is reported as "Unable to Update User..." — the update program's message, not a delete-specific one. | `DELETE-USER-SEC-FILE` lines 329-335 | Exception Handling | Business-derived | Wrong message for the operation; preserved verbatim and raised for business sign-off |
| BR-27 | Exit returns to the calling screen | Pressing the exit key returns to the screen that called the program, or to the administration menu when there was none. | `MAIN-PARA` lines 111-118 | Control Flow | Business-derived | Unlike update, exit does not delete anything |

## Exception scenarios (file status)

| Response | Operation | Behaviour |
|---|---|---|
| NORMAL | WRITE (add) | BR-06 confirmation, fields cleared |
| DUPKEY / DUPREC | WRITE (add) | BR-05 "User ID already exist..." |
| NORMAL | READ UPDATE (update, delete) | BR-09 / BR-20 prompt to press the confirmation key |
| NOTFND | READ UPDATE, REWRITE, DELETE | "User ID NOT found..." |
| NORMAL | REWRITE / DELETE | BR-15 / BR-25 confirmation |
| any other | WRITE | BR-07 "Unable to Add User..." |
| any other | READ UPDATE | "Unable to lookup User..." |
| any other | REWRITE / DELETE | BR-17 / BR-26 "Unable to Update User..." |

## Target implementation

`UserAdminService` (identity service) implements BR-01 through BR-17 and BR-20 through BR-26 behind
`POST /api/users`, `GET /api/users/{userId}`, `PUT /api/users/{userId}` and
`DELETE /api/users/{userId}`, all requiring `ROLE_ADMIN`. Field presence is validated in the order of
the COBOL EVALUATE so that the same single message is returned; BR-13's change detection compares the
space-padded values, and BR-14 returns "Please modify to update ..." with no write. The duplicate-key
condition of BR-05 is detected as a primary-key violation, so the message is the legacy one rather
than a database error. BR-26's misleading literal is reproduced verbatim.

BR-08, BR-18, BR-19, BR-22 and BR-27 are key-handling rules. The React screens keep the observable
behaviour that matters — delete requires a separate confirmation (BR-22) and the clear action wipes
the form without a call (BR-08) — but the update screen deliberately does **not** reproduce BR-18's
save-on-exit, because a navigation action that also stores data has no safe HTTP equivalent; that
deviation is recorded in `docs/12-consistency-model-delta-register.md`. BR-23's unchecked read before
delete cannot be reproduced either, because the service reads and deletes in one transaction and a
missing record raises before the delete; the difference is recorded in the same register.
