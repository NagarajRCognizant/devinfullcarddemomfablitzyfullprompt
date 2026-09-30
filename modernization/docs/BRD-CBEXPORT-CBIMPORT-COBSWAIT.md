# Business Rules — CBEXPORT, CBIMPORT, COBSWAIT (branch migration and the wait utility)

Source: `app/cbl/CBEXPORT.cbl` (582 lines), `app/cbl/CBIMPORT.cbl` (487 lines),
`app/cbl/COBSWAIT.cbl` (41 lines), driven by `app/jcl/CBEXPORT.jcl`, `app/jcl/CBIMPORT.jcl` and
`app/jcl/WAITSTEP.jcl`. Copybook: CVEXPORT (the 500-byte export record: a 40-byte key area, a
one-byte record type, a 26-byte timestamp, a 9-digit binary sequence number, a 4-byte branch, a
5-byte region and a 460-byte payload redefined per record type).

## 1. CBEXPORT — branch migration export

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | The export is stamped with the moment it started | Every record in an export carries the same timestamp, taken once when the run starts, recorded to the second with a hundredths field of zero. | `1050-GENERATE-TIMESTAMP`: `ACCEPT … FROM DATE YYYYMMDD` / `FROM TIME`, `STRING … '.00' INTO WS-FORMATTED-TIMESTAMP` | Derivation/Default | Hard-coded literal (`'.00'`) | Sub-second precision is discarded and replaced by a constant |
| BR-02 | Every input must be available before anything is exported | The five master files and the export file are all opened before the first record is exported; any failed open ends the run abnormally and produces no export. | `1100-OPEN-FILES` — five `OPEN INPUT`, one `OPEN OUTPUT`, each followed by `PERFORM 9999-ABEND-PROGRAM` | Exception Handling | Business-derived | — |
| BR-03 | Record types are exported in a fixed order | An export file holds the customers first, then the accounts, then the cross-references, then the transactions, then the cards. | `0000-MAIN-PROCESSING`: `2000-EXPORT-CUSTOMERS`, `3000-EXPORT-ACCOUNTS`, `4000-EXPORT-XREFS`, `5000-EXPORT-TRANSACTIONS`, `5500-EXPORT-CARDS` | Control Flow | Business-derived | The importer does not depend on the order, but a receiving branch loading the file sequentially does |
| BR-04 | Every record of every file is exported | Each of the five files is read from its first record to its last and every record read is exported; nothing is filtered. | `2000`/`3000`/`4000`/`5000`/`5500` `PERFORM UNTIL WS-…-EOF` | Data Selection | Business-derived | — |
| BR-05 | Each export record carries its type | Every exported record is marked with the kind of data it holds: `C` customer, `A` account, `X` cross-reference, `T` transaction, `D` card. | `2200`/`3200`/`4200`/`5200`/`5700` `MOVE 'C'…'D' TO EXPORT-REC-TYPE` | Derivation/Default | Hard-coded literal | — |
| BR-06 | Export records are numbered consecutively | Records are numbered from one upwards across the whole export, in the order they are written, regardless of type. | `ADD 1 TO WS-SEQUENCE-COUNTER`, `MOVE WS-SEQUENCE-COUNTER TO EXPORT-SEQUENCE-NUM` in each create paragraph | Derivation/Default | Business-derived | The counter is `S9(9) COMP`; nothing checks for overflow |
| BR-07 | Every export is attributed to one branch and region | Every exported record states that it comes from branch `0001` in region `NORTH`. | `MOVE '0001' TO EXPORT-BRANCH-ID`, `MOVE 'NORTH' TO EXPORT-REGION-CODE` in each create paragraph | Derivation/Default | Hard-coded literal (`0001`, `NORTH`) | Hard-coded branch and region — a second branch cannot use the programme unchanged. REVIEW REQUIRED |
| BR-08 | Export payload content | Each export record carries the fields of the source record laid out for the receiving branch: the customer's identity, addresses, phone numbers, identifiers, date of birth and credit score; the account's status, balances, limits and dates; the cross-reference's card, customer and account; the transaction's classification, amount, merchant and timestamps; and the card's number, account, security code, embossed name, expiry and status. | `2200-CREATE-CUSTOMER-EXP-REC`, `3200-…`, `4200-…`, `5200-…`, `5700-…` field-by-field `MOVE`s into the CVEXPORT redefinitions | Formatting/Conversion | Business-derived | Personal data (social security number, government-issued id, date of birth) and the card security code are written to a flat file in clear — REVIEW REQUIRED |
| BR-09 | A write failure ends the run abnormally | A failure writing an export record is reported with the file status and the run ends abnormally, leaving a partial export file. | each create paragraph: `IF NOT WS-EXPORT-OK … PERFORM 9999-ABEND-PROGRAM` | Exception Handling | Business-derived | A partial export file is left behind and is indistinguishable from a complete one |
| BR-10 | A read failure other than end of file ends the run abnormally | A read outcome that is neither success nor end of file is reported with the file status and the run ends abnormally. | `2100`/`3100`/`4100`/`5100`/`5600` `IF NOT WS-…-OK AND NOT WS-…-EOF` | Exception Handling | Business-derived | File status treated as business logic |
| BR-11 | The run reports what it exported | The run records the export date and time and the number of records exported of each type and in total. | `1000-INITIALIZE` and `6000-FINALIZE` `DISPLAY`s, `ADD 1 TO WS-…-RECORDS-EXPORTED` / `WS-TOTAL-RECORDS-EXPORTED` | Control Flow | Business-derived | Counts are reported, never checked |

## 2. CBIMPORT — branch migration import

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | The import is stamped with the moment it started | The run records the date and time it started. | `1000-INITIALIZE` reference modification of `FUNCTION CURRENT-DATE` into `WS-IMPORT-DATE` / `WS-IMPORT-TIME` | Derivation/Default | Business-derived | Recorded in the log only; not carried into any imported record |
| BR-02 | The export file and all six outputs must be available | The export file and the five master files plus the error file are opened before the first record is processed; any failed open ends the run abnormally. | `1100-OPEN-FILES` — one `OPEN INPUT`, six `OPEN OUTPUT`, each followed by `9999-ABEND-PROGRAM` | Exception Handling | Business-derived | The outputs are opened as new files, so an import replaces the receiving branch's master files rather than merging into them |
| BR-03 | Every record in the export file is processed | The export file is read from the first record to the last and every record is counted and dispatched. | `2000-PROCESS-EXPORT-FILE` `PERFORM UNTIL WS-EXPORT-EOF`, `ADD 1 TO WS-TOTAL-RECORDS-READ` | Data Selection | Business-derived | — |
| BR-04 | Records are dispatched on their type | A record is written to the customer, account, cross-reference, transaction or card file according to the type it carries. | `2200-PROCESS-RECORD-BY-TYPE` `EVALUATE EXPORT-REC-TYPE WHEN 'C'/'A'/'X'/'T'/'D'` | Control Flow | Business-derived | — |
| BR-05 | An unknown record type is rejected, not fatal | A record whose type is not recognised is counted, written to the error file with the time, the type, the record's sequence number and the reason "Unknown record type encountered", and the import continues with the next record. | `2200 … WHEN OTHER` → `2700-PROCESS-UNKNOWN-RECORD`, `2750-WRITE-ERROR` | Exception Handling | Hard-coded literal (the message text) | The only rejection reason the programme can produce |
| BR-06 | Import payload content | Each recognised record is written to the receiving master file with the fields the export carried, field for field. | `2300`/`2400`/`2500`/`2600`/`2650` `MOVE EXP-… TO …` and the subsequent `WRITE` | Formatting/Conversion | Business-derived | No field is validated, reformatted or defaulted on the way in |
| BR-07 | A failure writing a master record ends the run abnormally | A failure writing an imported record is reported with the file status and the run ends abnormally. | the `IF NOT WS-…-OK … PERFORM 9999-ABEND-PROGRAM` after each `WRITE` | Exception Handling | Business-derived | — |
| BR-08 | A failure writing an error record does not end the run | A failure writing to the error file is reported but the import continues, and the record is still counted as an error written. | `2750-WRITE-ERROR`: `IF NOT WS-ERROR-OK DISPLAY …` with no abend, then `ADD 1 TO WS-ERROR-RECORDS-WRITTEN` | Exception Handling | Business-derived | The count is incremented even when the write failed — the run reports errors it did not record |
| BR-09 | Import validation is declared but not performed | The run states that validation has completed and that no validation errors were detected, without examining anything. | `3000-VALIDATE-IMPORT`: two `DISPLAY` statements only | Control Flow | Business-derived | **Empty validation step that always reports success** — REVIEW REQUIRED |
| BR-10 | The run reports what it imported | The run records the totals read, imported per type, errors written and unknown record types. | `4000-FINALIZE` `DISPLAY`s | Control Flow | Business-derived | Counts are reported, never reconciled against the export's counts |

## 3. COBSWAIT — the wait utility

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | The job waits for the time the operator supplies | The step pauses for the number of hundredths of a second supplied to it, then ends. | `ACCEPT PARM-VALUE FROM SYSIN`, `MOVE PARM-VALUE TO MVSWAIT-TIME`, `CALL 'MVSWAIT' USING MVSWAIT-TIME`, `STOP RUN` | Control Flow | Business-derived | The supplied value is never validated, and the return of `MVSWAIT` is never checked — a non-numeric parameter fails at the `MOVE` |

`COBSWAIT` carries no business logic: it exists so that a JCL step can pause. It is **Retired** in
`13-artifact-disposition.md` — a scheduled job does not need a wait step, and where the target
genuinely has to wait for another job it uses the job's completion, not elapsed time.

## 4. Exception scenarios (file and I/O)

| Condition | Source treatment | Target treatment |
|---|---|---|
| any `OPEN` failure (either programme) | display the status, `CEE3ABD` | the step fails before the first chunk |
| read status `'00'` | continue | the reader returns the next record |
| read status `'10'` | end of that file, move on to the next | the reader returns `null`, the step completes |
| any other read status | display the status, `CEE3ABD` | the step fails, job status `FAILED` |
| master-file write failure | display the status, `CEE3ABD` | `ItemWriterException` → the step fails |
| error-file write failure (CBIMPORT) | display the status, continue, still counted | the same: the failure is logged and the run continues |
| unknown record type (CBIMPORT) | error record written, run continues | classified to the error writer, run continues |

## 5. Target implementation

**Export.** `MigrationExportJobConfig` (job `exportBranchMigrationJob`) exports the four record
types the account context owns — `C`, `A`, `X`, `D` — and
`MigrationExportTransactionsJobConfig` in `batch-transaction` (job
`exportBranchMigrationTransactionsJob`) exports the `T` records the transaction context owns. The
record image is built by `MigrationExportAggregator` on the `MigrationExportRecord`,
`MigrationExportHeader` and `MigrationExportData` codecs in `common`, which preserve the 500-byte
length, the 40-byte key area, the big-endian `COMP` sequence number and the packed `COMP-3` fields;
the file is written through ISO-8859-1 with no line separator so the binary fields survive.

**Import.** `MigrationImportJobConfig` (job `importBranchMigrationJob`) reads the export file with
`FixedLengthRecordReader`, dispatches on the record type with a
`ClassifierCompositeItemWriter` over the six output files, and writes the same error record for an
unrecognised type. `MigrationImportCountListener` reproduces the counts of BR-10.

## 6. Deliberate deviations and risk register

- **The split of BR-06's sequence numbering.** One programme numbered all five record types in one
  sequence; two jobs in two contexts cannot, so each export file is numbered from one within itself
  and the import accepts both. The import does not rely on the sequence number for anything but the
  error record, so no business outcome changes. Registered in
  `12-consistency-model-delta-register.md`.
- **Hard-coded branch `0001` and region `NORTH` (export BR-07)** are preserved, not parameterised,
  and carried as REVIEW REQUIRED in `11-unsupported-construct-register.md` UCR-37.
- **The empty validation of import BR-09** is preserved: the target logs the same two messages and
  validates nothing, because inventing validation rules here would reject records the mainframe
  accepted. REVIEW REQUIRED in `17-limitations-and-review-required.md`.
- **Clear personal data and card security codes in the export file (export BR-08)** are preserved
  so that an export can be read by the receiving branch's existing programmes. REVIEW REQUIRED as
  UCR-36.
- **The count incremented after a failed error write (import BR-08)** is preserved as-is.
