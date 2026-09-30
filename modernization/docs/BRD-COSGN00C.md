# Business Rules — COSGN00C (sign-on)

Source: `app/cbl/COSGN00C.cbl` (260 lines), map `app/bms/COSGN00.bms`, record layout
`app/cpy/CSUSR01Y.cpy`, COMMAREA `app/cpy/COCOM01Y.cpy`.
COSGN00C is transaction `CC00` and is the entry point of the whole application: it verifies the
operator against the USRSEC file and transfers to the administrator menu or the main menu.

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | The screen opens empty on first entry | Started with no conversation state, the transaction shows an empty sign-on screen with the cursor on the user id. | `MAIN-PARA` lines 80-83 (`IF EIBCALEN = 0`) | Control Flow | Business-derived | — |
| BR-02 | A user id must be keyed | A sign-on without a user id is refused with "Please enter User ID ..." and the cursor returns to the user id. | `PROCESS-ENTER-KEY` lines 118-122 | Validation | Business-derived | — |
| BR-03 | A password must be keyed | A sign-on with a user id but no password is refused with "Please enter Password ..." and the cursor returns to the password. | `PROCESS-ENTER-KEY` lines 123-127 | Validation | Business-derived | Only the first failing field is reported; the two checks are mutually exclusive branches of one EVALUATE |
| BR-04 | The credentials are folded to upper case | The keyed user id and password are converted to upper case before the security file is read and before they are compared, so sign-on is case-insensitive in both fields. | `PROCESS-ENTER-KEY` lines 132-136 (`FUNCTION UPPER-CASE`) | Formatting/Conversion | Business-derived | The stored password is therefore effectively upper case only — a stored lower-case password could never be matched |
| BR-05 | The user id is the key of the security file | The security record is retrieved by an exact read on the eight-character user id; no other field participates in the lookup. | `READ-USER-SEC-FILE` lines 211-219 (`RIDFLD(WS-USER-ID)`) | Data Selection | Business-derived | Ids shorter than eight characters are space-padded by the fixed-width layout |
| BR-06 | The password must equal the stored password | Sign-on succeeds only when the keyed password equals the password held on the security record, compared as fixed-width text. | `READ-USER-SEC-FILE` line 223 (`IF SEC-USR-PWD = WS-USER-PWD`) | Validation | Business-derived | The password is stored and compared in clear text, with no hashing, expiry, history or lock-out — carried forward deliberately, flagged for business sign-off |
| BR-07 | A wrong password is reported distinctly from an unknown user | A password mismatch is reported as "Wrong Password. Try again ..." while a user id that is not on file is reported as "User not found. Try again ...". | `READ-USER-SEC-FILE` lines 242-243 and 247-251 | Exception Handling | Business-derived | The two different messages disclose whether a user id exists; preserved as observed behaviour |
| BR-08 | Any other file failure is reported as a verification failure | A read that fails for any reason other than "record not found" is reported as "Unable to verify the User ..." and sign-on does not proceed. | `READ-USER-SEC-FILE` lines 252-256 (`WHEN OTHER`) | Exception Handling | Business-derived | Response code 13 (NOTFND) is the only code distinguished; every other code shares one message |
| BR-09 | The signed-on identity is carried forward | A successful sign-on records the operator's user id and user type, and the calling transaction and program, for the screens that follow. | `READ-USER-SEC-FILE` lines 224-228 | Derivation/Default | Business-derived | — |
| BR-10 | Administrators go to the admin menu, everyone else to the main menu | An operator whose user type is "A" continues to the administration menu; any other user type continues to the main menu. | `READ-USER-SEC-FILE` lines 230-240 with `CDEMO-USRTYP-ADMIN` (`88 ... VALUE 'A'` in `COCOM01Y`) | Control Flow | Business-derived | Only 'A' is treated as administrator; every other value, including an unexpected one, is treated as a regular user (no WHEN OTHER validation of the stored type) |
| BR-11 | Leaving the sign-on screen ends the session politely | Pressing the exit key ends the transaction with the standard thank-you text instead of returning a screen. | `MAIN-PARA` lines 88-90 with `CCDA-MSG-THANK-YOU` of `app/cpy/CSMSG01Y.cpy` | Control Flow | Business-derived | — |
| BR-12 | Any other key is refused | A key other than Enter or the exit key redisplays the screen with the standard invalid-key message. | `MAIN-PARA` lines 91-94 with `CCDA-MSG-INVALID-KEY` | Exception Handling | Business-derived | — |

## Exception scenarios (file status)

| Response | Meaning | Behaviour |
|---|---|---|
| `0` (NORMAL) | Record found | BR-06 decides between transfer and "Wrong Password. Try again ..." |
| `13` (NOTFND) | No record for the keyed user id | "User not found. Try again ...", cursor on the user id |
| any other | Any other read failure | "Unable to verify the User ...", cursor on the user id; no transfer |

## Target implementation

`SignOnService` (identity service) performs BR-02 through BR-10: it upper-cases both fields, reads
`SecurityUserRepository` by the padded eight-character id, compares the stored password as
fixed-width text and reports the three distinct messages. Instead of `XCTL`, the response carries
`nextScreen` (`COADM01C` or `COMEN01C`) and a signed JWT whose subject is the user id and whose
`userType` claim is `SEC-USR-TYPE`; the token replaces the `CDEMO-USER-ID` and `CDEMO-USER-TYPE`
fields of the COMMAREA (BR-09). Authority `ROLE_ADMIN` is granted for user type `A` only, which is
how BR-10 reaches the other services.

BR-01, BR-11 and BR-12 are terminal-conversation and AID-handling rules with no HTTP counterpart:
an unauthenticated request simply receives 401, and the client decides when to show the screen. They
are recorded in `docs/11-unsupported-construct-register.md`.
