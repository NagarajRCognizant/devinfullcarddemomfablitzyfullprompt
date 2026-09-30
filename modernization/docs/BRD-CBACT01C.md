# Business Rules — CBACT01C (Account extract, batch job READACCT)

Source: `app/cbl/CBACT01C.cbl` (430 lines), driven by `app/jcl/READACCT.jcl`. Copybooks: CVACT01Y
(account master record), CODATECN (date-conversion interface). Called program: `COBDATFT`
(assembler date formatter). Input: ACCT key-sequenced data set. Outputs: `…ACCTDATA.PSCOMP`
(LRECL 107, FB), `…ACCTDATA.ARRYPS` (LRECL 110, FB), `…ACCTDATA.VBPS` (LRECL 84, VB).

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | Previous extract files are discarded | Each run starts from empty extract files; the files produced by the previous run are deleted first. | `READACCT.jcl` step `PREDEL` (`PGM=IEFBR14`, `DISP=(MOD,DELETE,DELETE)`) | Control Flow | Business-derived | — |
| BR-02 | All extract files must be available before processing | The account file and all three extract files are opened before the first record is processed; a failed open ends the job abnormally. | `0000-ACCTFILE-OPEN` (317), `2000-OUTFILE-OPEN` (334), `3000-ARRFILE-OPEN` (352), `4000-VBRFILE-OPEN` (370) | Exception Handling | Business-derived | — |
| BR-03 | Every account is processed once, in account-number order | The extract reads the account master sequentially from the first to the last account and processes each record once. | `PROCEDURE DIVISION` lines 147-154 `PERFORM UNTIL END-OF-FILE = 'Y'`, `ACCESS MODE IS SEQUENTIAL` on the indexed file | Data Selection | Business-derived | — |
| BR-04 | End of file ends the run normally | Reaching the end of the account file ends the run normally; the file is closed and the job completes. | `1000-ACCTFILE-GET-NEXT` lines 180-181 (`'10'` → result 16 → `END-OF-FILE = 'Y'`), `9000-ACCTFILE-CLOSE` | Control Flow | Business-derived | File status treated as business logic |
| BR-05 | Any other read failure ends the run abnormally | A read outcome other than success or end-of-file is reported with the file status and the job ends abnormally. | `1000-ACCTFILE-GET-NEXT` lines 182-197 → `9910-DISPLAY-IO-STATUS`, `9999-ABEND-PROGRAM` (`CEE3ABD` with code 999) | Exception Handling | Hard-coded literal (999) | Hard-coded abend code |
| BR-06 | Each account produces four extract records | Every account read produces one fixed-length summary record, one array record, and two variable-length records (a short status record and a longer balance record). | `1000-ACCTFILE-GET-NEXT` lines 169-178 | Control Flow | Business-derived | — |
| BR-07 | Summary record content | The summary record carries account number, status, current balance, credit limit, cash credit limit, open date, expiry date, converted reissue date, current cycle credit, current cycle debit and group id. | `1300-POPUL-ACCT-RECORD` (215) with `OUT-ACCT-REC` layout (lines 57-70) | Formatting/Conversion | Business-derived | — |
| BR-08 | Reissue date is reformatted by the date service | The reissue date is converted through the date formatting service using conversion type 2 for both input and output before being written to the summary record. | `1300-POPUL-ACCT-RECORD` lines 223-233, `CALL 'COBDATFT' USING CODATECN-REC` | Formatting/Conversion | Hard-coded literal (type `'2'`) | Return code of `COBDATFT` is never checked — unchecked call |
| BR-09 | Zero cycle debit is reported as a standard default amount | An account whose current-cycle debit is zero is reported in the summary extract with the standard default amount 2525.00 instead of zero. | `1300-POPUL-ACCT-RECORD` lines 236-238 | Derivation/Default | Hard-coded literal (2525.00) | Hard-coded amount; no `ELSE`, so a non-zero value passes through unchanged |
| BR-10 | Summary record is written to the fixed-length extract | Each summary record is appended to the 107-byte fixed extract; a write failure other than success or end-of-file ends the run abnormally. | `1350-WRITE-ACCT-RECORD` (242) | Exception Handling | Business-derived | Treats file status `'10'` on a write as acceptable |
| BR-11 | Array record content | The array record carries the account number and five balance/debit pairs, of which the first three are populated: the account balance with 1005.00, the account balance with 1525.00, and -1025.00 with -2500.00. | `1400-POPUL-ARRAY-RECORD` (253) with `ARR-ARRAY-REC` layout (lines 72-80) | Derivation/Default | Hard-coded literal (1005.00, 1525.00, -1025.00, -2500.00) | Hard-coded demo amounts; occurrences 4 and 5 are declared but never populated |
| BR-12 | Array record is written to the array extract | Each array record is appended to the 110-byte fixed extract; a write failure other than success or end-of-file ends the run abnormally. | `1450-WRITE-ARRY-RECORD` (263) | Exception Handling | Business-derived | — |
| BR-13 | Variable record content | The short variable record carries the account number and status; the long variable record carries the account number, current balance, credit limit and the reissue year. | `1500-POPUL-VBRC-RECORD` (276), layouts `VBRC-REC1`/`VBRC-REC2` (lines 123-137) | Formatting/Conversion | Business-derived | Reissue year is taken from a work area filled in `1300`, creating an order dependency between paragraphs |
| BR-14 | Variable record lengths | The short variable record is written with a length of 12 bytes and the long variable record with a length of 39 bytes. | `1550-WRITE-VB1-RECORD` line 288 (`MOVE 12`), `1575-WRITE-VB2-RECORD` line 303 (`MOVE 39`) | Formatting/Conversion | Hard-coded literal (12, 39) | Lengths hard-coded, independent of the declared 10-80 range |
| BR-15 | Every account read is logged | Each account read is written to the run log field by field, followed by both variable records. | `1100-DISPLAY-ACCT-RECORD` (200), `1500-POPUL-VBRC-RECORD` lines 283-284 | Control Flow | Business-derived | Operator log only, no business effect |
| BR-16 | Close failure ends the run abnormally | A failure closing the account file is reported with the file status and ends the run abnormally. | `9000-ACCTFILE-CLOSE` (388) | Exception Handling | Business-derived | The three output files are never explicitly closed — REVIEW REQUIRED |

## Exception scenarios (file and I/O)

| Condition | Source treatment | Target treatment |
|---|---|---|
| file status `'00'` | continue | normal reader/writer flow |
| file status `'10'` on read | end of file, run completes | reader returns `null`, step completes |
| any other read status | display status, `CEE3ABD` code 999 | step fails, job status `FAILED`, exception logged |
| write status other than `'00'`/`'10'` | display status, `CEE3ABD` code 999 | `ItemWriterException` → step fails |
| open failure | display status, `CEE3ABD` code 999 | writer open failure → step fails before the first chunk |

## Target implementation

`ReadAcctJobConfig` (job `readAcctJob`, step `readAcctStep`), `AcctCompRecordAggregator`,
`AcctArrayRecordAggregator`, `AcctVbRecordAggregator`, `CobdatftDateFormatter`,
`CobolRecordBuilder`, `ZonedDecimalCodec`, `CobolNumeric`. Layout-level field mapping is in
`07-batch.md`.
