# Business Rules — CBACT02C, CBACT03C, CBCUS01C (master file print programs)

Source: `app/cbl/CBACT02C.cbl` (178 lines, card master), `app/cbl/CBACT03C.cbl` (178 lines, card
cross-reference), `app/cbl/CBCUS01C.cbl` (178 lines, customer master). Driven by
`app/jcl/READCARD.jcl`, `app/jcl/READXREF.jcl`, `app/jcl/READCUST.jcl`. Copybooks: CVACT02Y
(card record), CVACT03Y (cross-reference record), CVCUS01Y (customer record).

The three programs are the same program with a different file: a sequential read of one
key-sequenced data set that prints every record to the job log. The rules are therefore catalogued
once and the per-program differences are listed in §2. Line numbers below are those of `CBACT02C`;
the two others differ only in the copybook and the data-set name.

## 1. Business rules

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | The file must be available before printing | The master file is opened before the first record is printed; if it cannot be opened the run ends abnormally and nothing is printed. | `0000-CARDFILE-OPEN` (`OPEN INPUT`, status not `'00'` → result 12 → abend) | Exception Handling | Business-derived | — |
| BR-02 | Every record is printed once, in key order | The file is read from the first record to the last and each record is printed once, in key order. | `PROCEDURE DIVISION` `PERFORM UNTIL END-OF-FILE = 'Y'`, `ACCESS MODE IS SEQUENTIAL` on the indexed file | Data Selection | Business-derived | — |
| BR-03 | The whole record is printed as one line | Each record is printed as the complete record image, not as separate fields. | `DISPLAY CARD-RECORD` in the main loop | Formatting/Conversion | Business-derived | Sensitive data is printed in full — see §4 |
| BR-04 | End of file ends the run normally | Reaching the end of the file ends the run normally; the file is closed and the job completes. | `1000-CARDFILE-GET-NEXT`: status `'10'` → result 16 → `APPL-EOF` → `END-OF-FILE = 'Y'`; `9000-CARDFILE-CLOSE` | Control Flow | Business-derived | File status treated as business logic |
| BR-05 | Any other read failure ends the run abnormally | A read outcome other than success or end of file is reported with the file status and the run ends abnormally. | `1000-CARDFILE-GET-NEXT`: else → result 12 → `DISPLAY 'ERROR READING CARDFILE'`, `9910-DISPLAY-IO-STATUS`, `9999-ABEND-PROGRAM` (`CEE3ABD`, code 999) | Exception Handling | Hard-coded literal (999) | Hard-coded abend code |
| BR-06 | A close failure ends the run abnormally | A failure closing the file is reported with the file status and ends the run abnormally, even though every record has already been printed. | `9000-CARDFILE-CLOSE` | Exception Handling | Business-derived | The records are already printed, so the abend reports a condition that cannot change the output |
| BR-07 | Start and end of the run are logged | The start and the end of the run are recorded in the job log. | `DISPLAY 'START OF EXECUTION OF PROGRAM CBACT02C'`, `DISPLAY 'END OF EXECUTION OF PROGRAM CBACT02C'` | Control Flow | Business-derived | Operator log only, no business effect |
| BR-08 | The file status is reported in a readable form | When a failure is reported, a numeric file status is shown as a two-digit value and a status with a binary second byte is shown as its numeric value. | `9910-DISPLAY-IO-STATUS` with `IO-STATUS-04`/`TWO-BYTES-BINARY` redefinition | Formatting/Conversion | Business-derived | — |

## 2. Per-program differences

| Program | Data set | Record | Key | Record image printed |
|---|---|---|---|---|
| `CBACT02C` | `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` (`READCARD.jcl`) | `CARD-RECORD` (CVACT02Y) | `FD-CARD-NUM` X(16) | 150 bytes: card number + 134 bytes of card data |
| `CBACT03C` | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` (`READXREF.jcl`) | `CARD-XREF-RECORD` (CVACT03Y) | `FD-XREF-CARD-NUM` X(16) | 50 bytes: card number + 34 bytes of cross-reference data |
| `CBCUS01C` | `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` (`READCUST.jcl`) | `CUSTOMER-RECORD` (CVCUS01Y) | `FD-CUST-ID` X(09) | 500 bytes: customer id + 491 bytes of customer data |

`CBACT03C`'s error text for a read failure is `'ERROR READING XREFFILE'` and `CBCUS01C`'s is
`'ERROR READING CUSTFILE'`; the open-failure text of `CBCUS01C` is
`'ERROR OPENING CUSTOMER FILE'`. No other wording or logic differs.

## 3. Exception scenarios (file and I/O)

| Condition | Source treatment | Target treatment |
|---|---|---|
| file status `'00'` | continue | reader returns the next row |
| file status `'10'` | end of file, run completes normally | reader returns `null`, step completes |
| any other read status | display the status, `CEE3ABD` code 999 | step fails, job status `FAILED`, the exception is logged |
| open failure | display the status, `CEE3ABD` code 999 | the step fails before the first chunk |
| close failure | display the status, `CEE3ABD` code 999 | no equivalent: the reader is closed by the framework and a failure there fails the step |

## 4. Risk register

- **Dead code (all three programs):** the commented-out `DISPLAY` inside
  `1000-…-GET-NEXT` duplicates BR-03. Recorded, not deleted.
- **Sensitive data in the job log:** `CBACT02C` prints the card record, which contains the full card
  number, the expiry date and the CVV; `CBCUS01C` prints the customer record, which contains the
  social security number, the government-issued id and the date of birth. The target logs the same
  record images, so the exposure is carried over rather than removed; it is registered in
  `17-limitations-and-review-required.md` as REVIEW REQUIRED, because masking the log would change
  what the operator compares against the mainframe output.
- **Hard-coded abend code 999** (BR-05, BR-06) — the same literal as every other batch program in
  the application.
- **No `ELSE` on the open check of BR-01** is not a defect: the `IF APPL-AOK` that follows covers
  both outcomes.

## 5. Target implementation

`MasterFilePrintJobConfig` in `batch-account-extract` — jobs `printCardFileJob`,
`printCardXrefFileJob` and `printCustomerFileJob`, one step each, a JPA paging reader in key order
and a writer that logs the fixed-width record image rebuilt by `MasterFileRecords`. The record
images are byte-for-byte the ones the source printed, which is what makes the job log comparable
between the two systems. Layout-level field mapping is in `07-batch.md`; the job-to-JCL mapping is
in `13-artifact-disposition.md`.
