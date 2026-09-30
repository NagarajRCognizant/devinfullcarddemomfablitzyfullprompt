# Business Rules — CORPT00C (transaction report request)

Source: `app/cbl/CORPT00C.cbl` (649 lines) with map `app/bms/CORPT00.bms`, the internal-reader
skeletons `app/jcl/INTRDRJ1.JCL` and `app/jcl/INTRDRJ2.JCL` and the report job
`app/jcl/TRANREPT.jcl` (`CBTRN03C`, see `BRD-CBTRN01C-02C-03C.md`). Transaction `CR00`, menu
option 9 (`BRD-COMEN01C.md`, BR-01).

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | Exactly one report type must be chosen | The operator marks monthly, yearly or custom; marking none is refused with "Select a report type to print report...". | `PROCESS-ENTER-KEY` `EVALUATE TRUE … WHEN OTHER` | Validation | Business-derived | The branches are tested in the order monthly, yearly, custom, so marking several silently uses the first |
| BR-02 | A monthly report covers the current calendar month | The monthly report runs from the first day of the current month to its last day. | `PROCESS-ENTER-KEY` monthly branch (day "01"; next month's first day minus one) | Calculation | Business-derived | Derived from the system date, so the report the operator gets depends on when it is requested |
| BR-03 | The end of the month is the day before the first of the next month | The last day of the month is computed by moving to the first of the next month and stepping back one day, rolling the year over in December. | monthly branch (`ADD 1 TO WS-CURDATE-MONTH`, `IF > 12` roll year, `DATE-OF-INTEGER(INTEGER-OF-DATE(...) - 1)`) | Calculation | Business-derived | Leap years are handled by the date intrinsics, not by a table |
| BR-04 | A yearly report covers the whole current year | The yearly report runs from 1 January to 31 December of the current year. | `PROCESS-ENTER-KEY` yearly branch (literals "01"/"01" and "12"/"31") | Calculation | Hard-coded literal | The year end is the literal 12/31, so a non-calendar financial year is not supported |
| BR-05 | A custom report needs all six date parts | Start month, start day, start year, end month, end day and end year must each be entered; each missing part has its own message in that order. | custom branch first `EVALUATE TRUE` (six `WHEN` branches) | Validation | Business-derived | — |
| BR-06 | Month and day must be in range | The start and end month must be a number from 1 to 12 and the day a number from 1 to 31, otherwise "… Not a valid Month..." or "… Not a valid Day...". | custom branch `IF … IS NOT NUMERIC OR …` on each part | Validation | Business-derived | 31 is accepted for every month at this point; BR-08 catches the impossible combinations |
| BR-07 | Years must be numeric | A non-numeric start or end year is refused with "Start Date - Not a valid Year..." or "End Date - Not a valid Year...". | custom branch year checks | Validation | Business-derived | Any numeric year is accepted, including one far in the past or future |
| BR-08 | Both dates must be real dates | The assembled start and end dates are checked by the common date routine; a date it rejects is refused with "Start Date - Not a valid date..." or "End Date - Not a valid date...". | custom branch `CALL 'CSUTLDTC'`, `IF CSUTLDTC-RESULT-SEV-CD = '0000'` and message number test | Validation | Business-derived | Severity `0000` with message `2513` is accepted as valid, mirroring COTRN02C BR-09 |
| BR-09 | Printing is confirmed before the job is submitted | Nothing is submitted until the operator confirms: a blank confirmation asks "Please confirm to print the <type> report...", "N" abandons the request, and anything else is refused with the keyed value quoted back as not valid. | `SUBMIT-JOB-TO-INTRDR` (`IF CONFIRMI = SPACES OR LOW-VALUES`, then `EVALUATE TRUE`) | Control Flow | Business-derived | — |
| BR-10 | The chosen dates are passed to the report job | The start and end dates are placed in the job's parameter cards, so the printed report covers exactly the requested range. | `PARM-START-DATE-1`/`-2` and `PARM-END-DATE-1`/`-2` in each branch | Derivation/Default | Business-derived | Each date is written into two parameter cards, one per report step |
| BR-11 | The report is run as a batch job | The request is submitted by writing the job stream, line by line, to the job submission queue until the end-of-file card or a blank line. | `SUBMIT-JOB-TO-INTRDR` `PERFORM VARYING WS-IDX … UNTIL WS-IDX > 1000`, `WIRTE-JOBSUB-TDQ` (`WRITEQ TD QUEUE('JOBS')`) | Control Flow | Business-derived | The loop is capped at 1000 lines; a longer job stream would be silently truncated |
| BR-12 | The operator is told the report was submitted, not printed | A successful submission clears the screen and reports "<type> report submitted for printing ..." in green; the operator is not told whether the job ran. | `PROCESS-ENTER-KEY` final `IF NOT ERR-FLG-ON` (`STRING … INTO WS-MESSAGE`, `DFHGREEN`) | Formatting/Conversion | Business-derived | Submission failures of the job itself never reach this screen |
| BR-13 | Exit returns to the main menu | The exit key returns to the main menu regardless of which program called this one. | `MAIN-PARA` `WHEN DFHPF3` (`MOVE 'COMEN01C'`) | Control Flow | Business-derived | Unlike the other transaction screens, the caller is ignored |
| BR-14 | Any other key is refused | A key other than Enter or exit redisplays the screen with the standard invalid-key message. | `MAIN-PARA` `WHEN OTHER` | Exception Handling | Business-derived | — |

## Exception scenarios

The only I/O is the transient-data write of the job stream. A failed `WRITEQ TD` reports the
response on the console and refuses the request; no path abends and no report state is kept, so a
resubmission is always safe from the screen's point of view.

## Target implementation

`ReportRequestService` + `POST /api/transaction-reports` + React `ReportRequestScreen`. BR-01 to
BR-10 and BR-12 to BR-14 are implemented unchanged, including the date-routine outcome of BR-08 and
every message text, and are asserted by `ReportRequestServiceTest`.

BR-11 has no target counterpart: there is no internal reader and no job queue. The confirmed
request is persisted as a `report_request` row holding the report type and the resolved start and
end dates, and `transactionReportJob` (the conversion of `CBTRN03C`) consumes the pending requests
and marks them complete. The screen still reports "submitted for printing", which remains accurate:
the request is recorded, not the printed output. The delta is registered as CMD-17 in
`12-consistency-model-delta-register.md`, and the 1000-line cap of BR-11 is recorded in
`11-unsupported-construct-register.md` as having no target equivalent.
