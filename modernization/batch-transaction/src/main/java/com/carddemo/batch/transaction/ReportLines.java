package com.carddemo.batch.transaction;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * The print lines of the daily transaction report (app/cpy/CVTRA07Y.cpy), each 133 characters.
 *
 * <p>Every line is built at its source width so the report can still be compared column for
 * column with a listing produced on the mainframe.
 */
public final class ReportLines {

    /** LRECL of TRANREPT. */
    public static final int LINE_LENGTH = 133;
    /** WS-PAGE-SIZE: a page total is written every twenty detail lines. */
    public static final int PAGE_SIZE = 20;
    /** TRANSACTION-HEADER-2. */
    public static final String RULE = "-".repeat(LINE_LENGTH);

    private static final DecimalFormat EDITED_AMOUNT = new DecimalFormat("#,##0.00",
            DecimalFormatSymbols.getInstance(Locale.US));

    private ReportLines() {
    }

    /** REPORT-NAME-HEADER. */
    public static String nameHeader(String startDate, String endDate) {
        return pad(fixed("DALYREPT", 38)
                + fixed("Daily Transaction Report", 41)
                + fixed("Date Range: ", 12)
                + fixed(startDate, 10)
                + " to "
                + fixed(endDate, 10));
    }

    /** TRANSACTION-HEADER-1. */
    public static String columnHeader() {
        return pad(fixed("Transaction ID", 17)
                + fixed("Account ID", 12)
                + fixed("Transaction Type", 19)
                + fixed("Tran Category", 35)
                + fixed("Tran Source", 14)
                + " "
                + fixed("        Amount", 16));
    }

    /** A blank print line. */
    public static String blank() {
        return pad("");
    }

    /** TRANSACTION-DETAIL-REPORT. */
    public static String detail(String tranId, Long accountId, String typeCd, String typeDesc,
            Integer catCd, String catDesc, String source, BigDecimal amount) {
        return pad(fixed(tranId, 16)
                + " "
                + fixed(accountId == null ? "" : String.format("%011d", accountId), 11)
                + " "
                + fixed(typeCd, 2)
                + "-"
                + fixed(typeDesc, 15)
                + " "
                + String.format("%04d", catCd == null ? 0 : catCd)
                + "-"
                + fixed(catDesc, 29)
                + " "
                + fixed(source, 10)
                + "    "
                + signedAmount(amount)
                + "  ");
    }

    /** REPORT-PAGE-TOTALS. */
    public static String pageTotal(BigDecimal total) {
        return pad(fixed("Page Total", 11) + ".".repeat(86) + positiveSignedAmount(total));
    }

    /** REPORT-ACCOUNT-TOTALS. */
    public static String accountTotal(BigDecimal total) {
        return pad(fixed("Account Total", 13) + ".".repeat(84) + positiveSignedAmount(total));
    }

    /** REPORT-GRAND-TOTALS. */
    public static String grandTotal(BigDecimal total) {
        return pad(fixed("Grand Total", 11) + ".".repeat(86) + positiveSignedAmount(total));
    }

    /**
     * {@code PIC -ZZZ,ZZZ,ZZZ.ZZ}: a floating minus for negative values, a blank sign position for
     * positive ones and a wholly blank field when the value is zero, because zero suppression
     * leaves nothing to print.
     */
    static String signedAmount(BigDecimal amount) {
        return editedAmount(amount, ' ');
    }

    /** {@code PIC +ZZZ,ZZZ,ZZZ.ZZ}: as above, but a positive value prints its sign. */
    static String positiveSignedAmount(BigDecimal amount) {
        return editedAmount(amount, '+');
    }

    private static String editedAmount(BigDecimal amount, char positiveSign) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        if (value.signum() == 0) {
            return " ".repeat(14);
        }
        char sign = value.signum() < 0 ? '-' : positiveSign;
        String digits = EDITED_AMOUNT.format(value.abs());
        return sign + " ".repeat(Math.max(0, 13 - digits.length())) + digits;
    }

    private static String fixed(String value, int width) {
        String text = value == null ? "" : value;
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        return text + " ".repeat(width - text.length());
    }

    private static String pad(String line) {
        return fixed(line, LINE_LENGTH);
    }
}
