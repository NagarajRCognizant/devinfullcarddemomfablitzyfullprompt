package com.carddemo.batch.transaction;

import java.math.BigDecimal;

/**
 * The HTML-LINES structure of CBSTM03A: the fixed markup lines of the 88 levels and the four
 * lines the program builds with STRING.
 *
 * <p>The markup is reproduced character for character, including the inline styles, because the
 * HTML statement is a deliverable of the job rather than a presentation choice of this service.
 */
final class StatementHtmlLines {

    static final String L01 = "<!DOCTYPE html>";
    static final String L02 = "<html lang=\"en\">";
    static final String L03 = "<head>";
    static final String L04 = "<meta charset=\"utf-8\">";
    static final String L05 = "<title>HTML Table Layout</title>";
    static final String L06 = "</head>";
    static final String L07 = "<body style=\"margin:0px;\">";
    static final String L08 =
            "<table  align=\"center\" frame=\"box\" style=\"width:70%; font:12px Segoe UI,sans-serif;\">";
    static final String TRS = "<tr>";
    static final String TRE = "</tr>";
    static final String TDE = "</td>";
    static final String L10 =
            "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#1d1d96b3;\">";
    static final String L15 =
            "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#FFAF33;\">";
    static final String L16 = "<p style=\"font-size:16px\">Bank of XYZ</p>";
    static final String L17 = "<p>410 Terry Ave N</p>";
    static final String L18 = "<p>Seattle WA 99999</p>";
    static final String L22_35 =
            "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#f2f2f2;\">";
    static final String L30_42 = "<td colspan=\"3\" style=\"padding:0px 5px;"
            + "background-color:#33FFD1; text-align:center;\">";
    static final String L31 = "<p style=\"font-size:16px\">Basic Details</p>";
    static final String L43 = "<p style=\"font-size:16px\">Transaction Summary</p>";
    static final String L47 = "<td style=\"width:25%; padding:0px 5px; "
            + "background-color:#33FF5E; text-align:left;\">";
    static final String L48 = "<p style=\"font-size:16px\">Tran ID</p>";
    static final String L50 = "<td style=\"width:55%; padding:0px 5px; "
            + "background-color:#33FF5E; text-align:left;\">";
    static final String L51 = "<p style=\"font-size:16px\">Tran Details</p>";
    static final String L53 = "<td style=\"width:20%; padding:0px 5px; "
            + "background-color:#33FF5E; text-align:right;\">";
    static final String L54 = "<p style=\"font-size:16px\">Amount</p>";
    static final String L58 = "<td style=\"width:25%; padding:0px 5px; "
            + "background-color:#f2f2f2; text-align:left;\">";
    static final String L61 = "<td style=\"width:55%; padding:0px 5px; "
            + "background-color:#f2f2f2; text-align:left;\">";
    static final String L64 = "<td style=\"width:20%; padding:0px 5px; "
            + "background-color:#f2f2f2; text-align:right;\">";
    static final String L75 = "<h3>End of Statement</h3>";
    static final String L78 = "</table>";
    static final String L79 = "</body>";
    static final String L80 = "</html>";

    private StatementHtmlLines() {
    }

    /** HTML-L11: the account heading. */
    static String accountHeading(Long accountId) {
        return "<h3>Statement for Account Number: " + StatementLines.accountIdField(accountId) + "</h3>";
    }

    /** HTML-L23: the name, taken from the eighty column line up to its first double space. */
    static String nameParagraph(String nameField) {
        return "<p style=\"font-size:16px\">"
                + StatementLines.upToDoubleSpace(StatementLines.fixed(nameField, 50)) + "  </p>";
    }

    /** HTML-ADDR-LN: one address line, taken up to its first double space. */
    static String addressParagraph(String addressField) {
        return "<p>" + StatementLines.upToDoubleSpace(addressField) + "  </p>";
    }

    /** HTML-BSIC-LN: the account id, which keeps the trailing spaces of its twenty column field. */
    static String accountIdParagraph(Long accountId) {
        return "<p>Account ID         : " + StatementLines.accountIdField(accountId) + "</p>";
    }

    /** HTML-BSIC-LN: the current balance in its edited form. */
    static String currentBalanceParagraph(BigDecimal balance) {
        return "<p>Current Balance    : " + StatementLines.unsuppressedAmount(balance) + "</p>";
    }

    /** HTML-BSIC-LN: the FICO score, which keeps the trailing spaces of its twenty column field. */
    static String ficoScoreParagraph(Integer ficoScore) {
        return "<p>FICO Score         : " + StatementLines.ficoField(ficoScore) + "</p>";
    }

    /** HTML-TRAN-LN: a transaction identifier, description or amount, in its own cell. */
    static String paragraph(String value) {
        return "<p>" + value + "</p>";
    }
}
