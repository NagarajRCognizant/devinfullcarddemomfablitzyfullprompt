# Consolidated Business Rule Report — CardDemo Credit Card Account Management

Cross-program view of the 108 rules extracted from the in-scope closure. The per-program catalogues
hold the full rows (statement, technical basis, classification, origin, confidence, risk flags):

| Program | Role | Rules | Catalogue |
|---|---|---|---|
| `COACTVWC` | CICS Account View (CAVW) | BR-01 … BR-23 (23) | `BRD-COACTVWC.md` |
| `COACTUPC` | CICS Account Update (CAUP) | BR-01 … BR-56 (56) | `BRD-COACTUPC.md` |
| `CBACT01C` | READACCT batch extract | BR-01 … BR-16 (16) | `BRD-CBACT01C.md` |
| `CSUTLDTC` | Date validation service | BR-01 … BR-06 (6) | `BRD-CSUTLDTC.md` |
| `COMEN01C` | Main menu access rules | BR-01 … BR-07 (7) | `BRD-COMEN01C.md` |

Rule IDs are sequential **per program**, so a rule is cited as `COACTUPC BR-30`.

## 1. Business capability → FR → BR

| Capability | FR | Requirement | Rules |
|---|---|---|---|
| Account enquiry | FR-01 | An operator may retrieve one account with its customer and card by account number | COACTVWC BR-01, BR-02, BR-03, BR-05, BR-16, BR-17, BR-19, BR-21, BR-22 |
| Account enquiry | FR-02 | The account number is validated before any store is read | COACTVWC BR-06 … BR-09, BR-20 |
| Account enquiry | FR-03 | The account is resolved cross-reference → account master → customer master, stopping at the first failure | COACTVWC BR-10, BR-11 |
| Account enquiry | FR-04 | Missing records and storage failures are reported with the source wording | COACTVWC BR-12 … BR-15, BR-23 |
| Account enquiry | FR-05 | The social security number is displayed grouped 3-2-4 | COACTVWC BR-18 |
| Account maintenance | FR-06 | The update is a staged conversation: fetch → validate → confirm → write | COACTUPC BR-01 … BR-05, BR-09, BR-10, BR-17, BR-52 |
| Account maintenance | FR-07 | The account number is validated before anything is fetched | COACTUPC BR-06, BR-07, BR-11, BR-12 |
| Account maintenance | FR-08 | Every editable field is validated in source order with the source message and highlight | COACTUPC BR-19 … BR-34, BR-55; also BR-18 (asterisk clears) |
| Account maintenance | FR-09 | Dates are validated part by part and then by the date service | COACTUPC BR-35 … BR-40, BR-45, BR-53; CSUTLDTC BR-01 … BR-06 |
| Account maintenance | FR-10 | A submission identical to what was fetched is reported, not written | COACTUPC BR-13 … BR-16 |
| Account maintenance | FR-11 | Both records are locked, the concurrency baseline is checked, then account is written before customer, atomically | COACTUPC BR-46 … BR-50 |
| Account maintenance | FR-12 | Stored formats are preserved (phone formatting, fixed widths, keys not editable) | COACTUPC BR-51, BR-54 |
| Account maintenance | FR-13 | Cancel discards keyed changes and redisplays stored values | COACTUPC BR-08 |
| Account maintenance | FR-14 | Read failures during the update are reported as in the enquiry, and are fatal beyond not-found | COACTUPC BR-41 … BR-44, BR-56 |
| Account extract | FR-15 | Every account is extracted once, in key order, into three layouts | CBACT01C BR-01 … BR-07, BR-11, BR-13, BR-14 |
| Account extract | FR-16 | Extract content preserves the source's defaults, formats and unpopulated fields | CBACT01C BR-08, BR-09, BR-11, BR-15 |
| Account extract | FR-17 | End of file ends the run normally; any other I/O outcome ends it abnormally | CBACT01C BR-04, BR-05, BR-10, BR-12, BR-16 |
| Access control (business level) | FR-18 | Menu options are offered according to operator type; both account options are open to regular operators | COMEN01C BR-01 … BR-07 |

## 2. Rules by classification

| Classification | Count | Examples |
|---|---|---|
| Validation | 33 | COACTUPC BR-19 … BR-40, COACTVWC BR-06 … BR-09, COMEN01C BR-01 |
| Control Flow | 27 | COACTUPC BR-01 … BR-09, BR-46, BR-49, COACTVWC BR-01 … BR-05 |
| Exception Handling | 18 | COACTVWC BR-11 … BR-15, BR-23, CBACT01C BR-04, BR-05, BR-10, BR-12, BR-16, COACTUPC BR-43, BR-50, BR-56 |
| Formatting/Conversion | 15 | COACTVWC BR-18, COACTUPC BR-45, BR-51, CBACT01C BR-08, BR-13, BR-14 |
| Data Selection | 9 | COACTVWC BR-10, BR-16, BR-17, COACTUPC BR-41, BR-44, CBACT01C BR-03, BR-06 |
| Derivation/Default | 6 | CBACT01C BR-09, BR-11, COACTUPC BR-18, BR-53 |

Total 108. No rule is classified `Calculation`: the two amount-bearing behaviours are a precision and
sign edit (`COACTUPC` BR-25) and a record-layout move (`CBACT01C` BR-07), so they are classified
Validation and Formatting/Conversion respectively. The capability performs no arithmetic of its own:
the only `COMPUTE`s in the closure are the five `FUNCTION NUMVAL-C` conversions of keyed amounts in
`COACTUPC 1100-RECEIVE-MAP` (lines 1079-1135), and the only `ADD`/`SUBTRACT`s are the return-code
plumbing of `CBACT01C 9910-DISPLAY-IO-STATUS` (lines 389-394). That is itself a finding — account
balances are maintained by other capabilities, this one only displays, validates and stores them.

## 3. Cross-program rule agreement (corroboration)

Rules corroborated by more than one artifact carry **High** confidence:

| Behaviour | Corroborating artifacts |
|---|---|
| 11-digit non-zero account number | `COACTVWC` BR-09, `COACTUPC` BR-12, `ACCTFILE.jcl` `KEYS(11 0)`, `CVACT01Y` `PIC 9(11)`, `LISTCAT.txt` |
| Cross-reference resolution by account | `COACTVWC` BR-10, `COACTUPC` BR-41, `XREFFILE.jcl` alternate index `KEYS(11,25)`, `CARDDEMO.CSD` `FILE(CXACAIX)` |
| Not-found wording | identical literals in both online programs |
| Signed money with two decimals | `COACTUPC` BR-25, `CVACT01Y` `S9(10)V99`, `CBACT01C` BR-07 output layout |
| Date validation contract | `COACTUPC` BR-39, `CSUTLDTC` BR-02 … BR-06, `CSUTLDPY` edit paragraphs |
| Both account options open to regular operators | `COMEN01C` BR-04, `COMEN02Y` option table, `CARDDEMO.CSD` transactions `CAVW`/`CAUP` |
| Extract record lengths | `CBACT01C` BR-14, `READACCT.jcl` DD `LRECL=107/110/84` |

Rules with **Medium** confidence and their reason:

| Rule | Reason |
|---|---|
| `CBACT01C` BR-08 (reissue date reformatting) | the `COBDATFT` module is missing; behaviour reconstructed from the copybook and caller (UCR-04) |
| `CBACT01C` BR-09 (zero cycle debit → `2525.00`) | a hard-coded literal with no business corroboration (UCR-07) |
| `CBACT01C` BR-11 (array occurrence amounts) | hard-coded literals; occurrences 4 and 5 never populated (UCR-05.4, UCR-07) |
| `COACTUPC` BR-34 (second address line unvalidated) | the validation is commented out; intent unknown (UCR-02) |
| `COACTUPC` BR-48 (concurrency comparison scope) | the date-of-birth offsets differ from those used to build the record (UCR-10.3) |
| `CSUTLDTC` BR-04 (result wording) | four outcomes can only be raised by IBM Language Environment (UCR-06) |

## 4. Mandatory risk flags — consolidated

| Flag | Occurrences |
|---|---|
| Hard-coded literal amounts / ranges / bounds | `CBACT01C` BR-09 (`2525.00`), BR-11 (`1005.00`, `1525.00`, `-1025.00`, `-2500.00`), BR-14 (`12`, `39`); `COACTUPC` BR-32 (`300`/`850`), BR-35 (century bounds), BR-56 (`9999`); `COACTVWC` BR-23 (`9999`, `0001`) → UCR-07 |
| Unchecked return code from a CALLed module | `CBACT01C` BR-08 — the `COBDATFT` status is never tested → UCR-05.1 |
| Declared but never populated / one-branch fields | `CBACT01C` BR-11 (occurrences 4 and 5 never populated), BR-09 (cycle debit written on one branch only) → UCR-05.3, UCR-05.4 |
| Duplicate / contradictory conditions, unreachable branches | `COACTVWC` BR-21 (duplicate `WHEN`), BR-16 (stale account fields retained), `COACTUPC` BR-23 (the alphanumeric edit paragraphs are never performed), BR-48 → UCR-10, UCR-05.2 |
| Missing ELSE / WHEN OTHER | `COACTVWC` BR-02, BR-05; `COACTUPC` BR-02, BR-05 → UCR-11 |
| Dead / commented-out logic | `COACTUPC` BR-34 (address line 2 edit), BR-53 (reissue date moved twice), the commented long-message send in both online programs → UCR-02, UCR-13 |

## 5. Exception scenarios (file and I/O behaviour as business logic)

File status is treated as business logic throughout: `'00'` success, `'10'` normal end-of-file /
not-found, anything else fatal.

| Scenario | Source behaviour | Rules | Target behaviour |
|---|---|---|---|
| Open failure (batch) | the run ends abnormally with the file status displayed, abend `999` | `CBACT01C` BR-02 | the step fails; the job is `FAILED` and the extract is incomplete |
| Read success | the record is processed | `CBACT01C` BR-03 | reader emits the item |
| Read end-of-file | the run ends normally, files closed | `CBACT01C` BR-04 | the reader returns `null`, the step completes |
| Read other status (batch) | status displayed, abend `999` | `CBACT01C` BR-05 | the step fails with the exception |
| Write failure | status displayed, abend `999` | `CBACT01C` BR-10, BR-12 | the step fails |
| Close failure | status displayed, abend `999` | `CBACT01C` BR-16 | the writer close exception fails the step |
| Read not-found (online) | the source message is displayed and the screen re-sent | `COACTVWC` BR-12 … BR-14, `COACTUPC` BR-42 | `RecordNotFoundException` → HTTP 404 carrying the same text |
| Read other response (online) | the file-error message is built and the transaction abends | `COACTVWC` BR-15, BR-23; `COACTUPC` BR-43, BR-56 | `StorageAccessException` → HTTP 500 carrying the same text; the transaction rolls back |
| Lock unavailable | the update reports the failure and writes nothing | `COACTUPC` BR-46 | `LOCK_ERROR`, nothing written (CMD-03) |
| Rewrite failure | the unit of work is backed out and the save reported failed | `COACTUPC` BR-50 | transaction rollback, `UPDATE_FAILED` (CMD-01, CMD-02) |

## 6. Where each rule is implemented and proved

`14-rtm.md` carries every rule row by row: ticket, rule, source location, target class and method,
React component, API operation, PostgreSQL object, batch job/step, validation test, expected and
actual result, status.
