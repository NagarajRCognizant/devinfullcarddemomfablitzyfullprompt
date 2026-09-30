# Business Rules — CSUTLDTC (date validation service)

Source: `app/cbl/CSUTLDTC.cbl` (156 lines). Called by `COACTUPC` through `CSUTLDPY`
(`EDIT-DATE-LE`) with the date, the mask and an 80-byte result area. Wraps the language
environment service `CEEDAYS`.

| Rule ID | Rule name | Business statement | Technical basis | Class | Origin | Risk flags |
|---|---|---|---|---|---|---|
| BR-01 | The result area is cleared before every check | Each date check starts from an empty result, so no outcome of a previous check can be mistaken for the current one. | `PROCEDURE DIVISION` line 90: `INITIALIZE WS-MESSAGE`, `MOVE SPACES TO WS-DATE` | Control Flow | Business-derived | — |
| BR-02 | The date is checked against the supplied mask | The supplied date is validated against the supplied format mask by the platform date service; the caller always supplies `YYYYMMDD`. | `A000-MAIN` lines 104-120 `CALL "CEEDAYS"`; mask set by `EDIT-DATE-LE` in CSUTLDPY | Validation | Business-derived | — |
| BR-03 | Severity decides validity | The severity reported by the date service is returned to the caller, and the caller treats a severity of zero as a valid date and anything else as invalid. | `A000-MAIN` line 122, `MOVE SEVERITY OF FEEDBACK-CODE TO WS-SEVERITY-N`; `MOVE WS-SEVERITY-N TO RETURN-CODE` (line 98); `IF WS-SEVERITY-N = 0` in CSUTLDPY | Validation | Business-derived | — |
| BR-04 | The failure reason is reported in words | The outcome is reported as one of: date is valid, insufficient data, date value error, invalid era, unsupported range, invalid month, bad picture string, non-numeric data, year in era is zero, or date is invalid. | `A000-MAIN` `EVALUATE TRUE` lines 127-149 | Formatting/Conversion | Business-derived | Condition names are compared as raw feedback tokens; "valid" is the all-zero token |
| BR-05 | Anything unrecognised is invalid | A feedback code the service does not recognise is reported as "Date is invalid". | `A000-MAIN` `WHEN OTHER` lines 147-148 | Exception Handling | Business-derived | `WHEN OTHER` present — no missing default |
| BR-06 | The result carries the checked date and mask | The result returned to the caller states the severity, the message code, the outcome text, the date that was checked and the mask that was used. | `WS-MESSAGE` layout lines 41-57, `MOVE WS-MESSAGE TO LS-RESULT` line 97 | Formatting/Conversion | Business-derived | — |

## Exception scenarios

`CEEDAYS` reports through the feedback code only; there is no file or database access in this
program. The caller (`CSUTLDPY` `EDIT-DATE-LE`) checks the severity but only echoes the message
code into the operator message, which is recorded as an unchecked-detail risk in
`11-unsupported-construct-register.md` (UCR-05).

## Target implementation

`DateValidationService` reproduces the severity/message-code contract and the ten outcome texts;
`DateEditor` reproduces the `CSUTLDPY` edits that call it. `CEEDAYS` itself is replaced by
`java.time.LocalDate` parsing with a strict resolver, which is the semantic equivalent for the
`YYYYMMDD` mask the application uses (Consistency/behaviour note in
`11-unsupported-construct-register.md`, UCR-06).
