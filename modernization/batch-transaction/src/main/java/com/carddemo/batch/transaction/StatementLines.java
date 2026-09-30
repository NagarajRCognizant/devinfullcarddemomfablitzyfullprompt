package com.carddemo.batch.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The STATEMENT-LINES and HTML-LINES structures of CBSTM03A.
 *
 * <p>STMTFILE has an 80 character record and HTMLFILE a 100 character record, and every line the
 * program writes is one whole record, so each line is produced at its source width - trailing
 * spaces included - and a statement produced here can be compared byte for byte with one produced
 * on the mainframe.
 */
public final class StatementLines {

    /** LRECL of STMTFILE. */
    public static final int TEXT_LINE_LENGTH = 80;
    /** LRECL of HTMLFILE. */
    public static final int HTML_LINE_LENGTH = 100;

    /** ST-LINE0. */
    public static final String START_OF_STATEMENT =
            "*".repeat(31) + "START OF STATEMENT" + "*".repeat(31);
    /** ST-LINE5 and ST-LINE10 and ST-LINE12, which are the same rule of dashes. */
    public static final String RULE = "-".repeat(TEXT_LINE_LENGTH);
    /** ST-LINE6. */
    public static final String BASIC_DETAILS =
            " ".repeat(33) + fixed("Basic Details", 14) + " ".repeat(33);
    /** ST-LINE11. */
    public static final String TRANSACTION_SUMMARY =
            " ".repeat(30) + "TRANSACTION SUMMARY " + " ".repeat(30);
    /** ST-LINE13. */
    public static final String COLUMN_HEADINGS =
            "Tran ID         " + fixed("Tran Details    ", 51) + "  Tran Amount";
    /** ST-LINE15. */
    public static final String END_OF_STATEMENT =
            "*".repeat(32) + "END OF STATEMENT" + "*".repeat(32);

    private StatementLines() {
    }

    /** ST-LINE1: the customer name, each part taken up to its first space as the STRING does. */
    public static String nameLine(String first, String middle, String last) {
        return text(fixed(upToSpace(first) + " " + upToSpace(middle) + " " + upToSpace(last) + " ", 75));
    }

    /** ST-LINE2 and ST-LINE3: the first two address lines, each in a fifty character field. */
    public static String addressLine(String address) {
        return text(fixed(address, 50));
    }

    /** ST-LINE4: city, state, country and postcode, each taken up to its first space. */
    public static String cityStateZipLine(String line3, String state, String country, String zip) {
        return text(upToSpace(line3) + " " + upToSpace(state) + " "
                + upToSpace(country) + " " + upToSpace(zip) + " ");
    }

    /** ST-LINE7. */
    public static String accountIdLine(Long accountId) {
        return text("Account ID         :" + accountIdField(accountId));
    }

    /** ST-LINE8. */
    public static String currentBalanceLine(BigDecimal balance) {
        return text("Current Balance    :" + unsuppressedAmount(balance));
    }

    /** ST-LINE9. */
    public static String ficoScoreLine(Integer ficoScore) {
        return text("FICO Score         :" + ficoField(ficoScore));
    }

    /** ST-LINE14: one transaction of the summary. */
    public static String transactionLine(String tranId, String description, BigDecimal amount) {
        return text(fixed(tranId, 16) + " " + fixed(description, 49) + "$" + suppressedAmount(amount));
    }

    /** ST-LINE14A: the total of the transactions written for this card. */
    public static String totalLine(BigDecimal total) {
        return text("Total EXP:" + " ".repeat(56) + "$" + suppressedAmount(total));
    }

    /** {@code MOVE ACCT-ID TO ST-ACCT-ID}: eleven digits with leading zeros in a X(20) field. */
    static String accountIdField(Long accountId) {
        return fixed(accountId == null ? "" : String.format("%011d", accountId), 20);
    }

    /** {@code MOVE CUST-FICO-CREDIT-SCORE TO ST-FICO-SCORE}: three digits in a X(20) field. */
    static String ficoField(Integer ficoScore) {
        return fixed(ficoScore == null ? "" : String.format("%03d", ficoScore), 20);
    }

    /**
     * {@code PIC 9(9).99-}: nine unsuppressed digits, a decimal point, two decimals and a trailing
     * minus that is a space when the value is not negative. Values beyond nine digits lose their
     * high order digits exactly as the MOVE does.
     */
    static String unsuppressedAmount(BigDecimal amount) {
        BigDecimal value = scaled(amount);
        String digits = value.abs().toBigInteger().toString();
        String whole = digits.length() > 9
                ? digits.substring(digits.length() - 9)
                : "0".repeat(9 - digits.length()) + digits;
        return whole + "." + decimals(value) + (value.signum() < 0 ? "-" : " ");
    }

    /** {@code PIC Z(9).99-}: as above with the leading zeros replaced by spaces. */
    static String suppressedAmount(BigDecimal amount) {
        String unsuppressed = unsuppressedAmount(amount);
        String whole = unsuppressed.substring(0, 9);
        int firstSignificant = 0;
        while (firstSignificant < 8 && whole.charAt(firstSignificant) == '0') {
            firstSignificant++;
        }
        return " ".repeat(firstSignificant) + whole.substring(firstSignificant)
                + unsuppressed.substring(9);
    }

    private static String decimals(BigDecimal value) {
        BigDecimal fraction = value.abs().remainder(BigDecimal.ONE).movePointRight(2);
        return String.format("%02d", fraction.toBigInteger());
    }

    private static BigDecimal scaled(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.DOWN);
    }

    /** A field of a COBOL STRING delimited by a space: the characters before the first space. */
    static String upToSpace(String value) {
        if (value == null) {
            return "";
        }
        int space = value.indexOf(' ');
        return space < 0 ? value : value.substring(0, space);
    }

    /** A field of a COBOL STRING delimited by two spaces. */
    static String upToDoubleSpace(String value) {
        if (value == null) {
            return "";
        }
        int gap = value.indexOf("  ");
        return gap < 0 ? value : value.substring(0, gap);
    }

    /** An 80 character STMTFILE record. */
    static String text(String line) {
        return fixed(line, TEXT_LINE_LENGTH);
    }

    /** A 100 character HTMLFILE record. */
    static String html(String line) {
        return fixed(line, HTML_LINE_LENGTH);
    }

    static String fixed(String value, int width) {
        String content = value == null ? "" : value;
        return content.length() >= width
                ? content.substring(0, width)
                : content + " ".repeat(width - content.length());
    }
}
