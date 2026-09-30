package com.carddemo.transaction.domain;

import com.carddemo.cobol.CobolText;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * The date handling of the transaction screens.
 *
 * <p>COTRN02C edits the keyed dates positionally as {@code YYYY-MM-DD} and only then calls
 * CSUTLDTC to decide whether they are real dates; COTRN00C shows the first ten characters of the
 * original timestamp as {@code MM/DD/YY}. Both are reproduced here.
 */
public final class TransactionDates {

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private TransactionDates() {
    }

    /** The positional edit: four digits, a hyphen, two digits, a hyphen, two digits. */
    public static boolean isKeyedFormat(String keyed) {
        return keyed != null && keyed.length() >= 10
                && CobolText.isNumeric(keyed.substring(0, 4))
                && keyed.charAt(4) == '-'
                && CobolText.isNumeric(keyed.substring(5, 7))
                && keyed.charAt(7) == '-'
                && CobolText.isNumeric(keyed.substring(8, 10));
    }

    /** The CSUTLDTC call that follows the positional edit. */
    public static boolean isRealDate(String keyed) {
        if (!isKeyedFormat(keyed)) {
            return false;
        }
        try {
            LocalDate.parse(keyed.substring(0, 10), ISO);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** POPULATE-TRAN-DATA of COTRN00C: the timestamp's date shown as {@code MM/DD/YY}. */
    public static String listDate(String timestamp) {
        if (timestamp == null || timestamp.length() < 10) {
            return "";
        }
        return timestamp.substring(5, 7) + "/" + timestamp.substring(8, 10) + "/"
                + timestamp.substring(2, 4);
    }
}
