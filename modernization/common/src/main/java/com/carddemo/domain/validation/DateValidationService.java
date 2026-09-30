package com.carddemo.domain.validation;

import com.carddemo.cobol.CobolText;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import org.springframework.stereotype.Component;

/**
 * Replacement for CSUTLDTC, which calls the Language Environment service CEEDAYS to convert a
 * date into a Lillian day number and reports the CEE feedback code.
 *
 * <p>{@code java.time} with a strict resolver gives the same accept/reject decision, so the
 * conversion is done locally and the feedback codes decoded from the condition tokens in
 * CSUTLDTC are reused for the message text: 2507 insufficient data, 2508 bad date value,
 * 2520 non numeric data. Severity 3 is the CEE severity carried by those tokens.
 */
@Component
public class DateValidationService {

    private static final DateTimeFormatter YYYYMMDD =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    private static final int CEE_SEVERITY_ERROR = 3;
    private static final int CEE_MSG_INSUFFICIENT_DATA = 2507;
    private static final int CEE_MSG_BAD_DATE_VALUE = 2508;
    private static final int CEE_MSG_NON_NUMERIC_DATA = 2520;

    /** CSUTLDTC with a 'YYYYMMDD' mask. */
    public DateValidationResult validateYyyyMmDd(String date) {
        String value = CobolText.trim(date);
        if (value.length() != 8) {
            return new DateValidationResult(CEE_SEVERITY_ERROR, CEE_MSG_INSUFFICIENT_DATA, "Insufficient");
        }
        if (!CobolText.isNumeric(value)) {
            return new DateValidationResult(CEE_SEVERITY_ERROR, CEE_MSG_NON_NUMERIC_DATA, "Nonnumeric data");
        }
        try {
            LocalDate.parse(value, YYYYMMDD);
            return DateValidationResult.valid();
        } catch (DateTimeParseException e) {
            return new DateValidationResult(CEE_SEVERITY_ERROR, CEE_MSG_BAD_DATE_VALUE, "Datevalue error");
        }
    }
}
