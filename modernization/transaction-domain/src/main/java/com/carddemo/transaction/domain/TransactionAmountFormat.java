package com.carddemo.transaction.domain;

import com.carddemo.cobol.CobolNumeric;
import com.carddemo.cobol.CobolText;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The {@code PIC +99999999.99} amount field of the transaction screens.
 *
 * <p>COTRN00C and COTRN02C both edit the amount through that picture, so the value always carries
 * its sign and is zero filled to eight integer digits, and the keyed value is accepted only in
 * exactly that shape. COTRN02C checks it character by character - sign, eight digits, a point, two
 * digits - and reports "Amount should be in format -99999999.99" for anything else, so the check
 * here is the same positional one rather than a lenient parse.
 */
public final class TransactionAmountFormat {

    /** Width of {@code +99999999.99}. */
    public static final int WIDTH = 12;

    private TransactionAmountFormat() {
    }

    /** The edited presentation: a sign, eight integer digits, a point and two decimals. */
    public static String format(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        BigDecimal scaled = amount.setScale(CobolNumeric.MONEY_SCALE, RoundingMode.HALF_UP);
        String sign = scaled.signum() < 0 ? "-" : "+";
        String digits = scaled.abs().toPlainString().replace(".", "");
        if (digits.length() < 10) {
            digits = "0".repeat(10 - digits.length()) + digits;
        }
        return sign + digits.substring(0, digits.length() - 2) + "."
                + digits.substring(digits.length() - 2);
    }

    /** The positional edit of COTRN02C VALIDATE-INPUT-DATA-FIELDS. */
    public static boolean isKeyedFormat(String keyed) {
        if (keyed == null || keyed.length() != WIDTH) {
            return false;
        }
        char sign = keyed.charAt(0);
        return (sign == '-' || sign == '+')
                && CobolText.isNumeric(keyed.substring(1, 9))
                && keyed.charAt(9) == '.'
                && CobolText.isNumeric(keyed.substring(10, 12));
    }

    /** {@code FUNCTION NUMVAL-C} of the keyed amount, which has already passed {@link #isKeyedFormat}. */
    public static BigDecimal parse(String keyed) {
        return CobolNumeric.parseNumericWithCurrency(keyed);
    }
}
