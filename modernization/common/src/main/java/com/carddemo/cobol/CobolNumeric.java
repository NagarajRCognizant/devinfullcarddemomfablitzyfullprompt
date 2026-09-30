package com.carddemo.cobol;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Port of the COBOL intrinsic functions {@code TEST-NUMVAL-C} / {@code NUMVAL-C} used by
 * COACTUPC to turn the keyed money strings into {@code S9(10)V99} values.
 *
 * <p>All monetary values use {@link BigDecimal} with scale 2 so the packed-decimal
 * precision and sign of the source records are preserved exactly.
 */
public final class CobolNumeric {

    public static final int MONEY_SCALE = 2;
    /** S9(10)V99 - ten integer digits. */
    public static final int MONEY_INTEGER_DIGITS = 10;
    /** Width of {@code +ZZZ,ZZZ,ZZZ.99}. */
    public static final int EDITED_MONEY_WIDTH = 15;

    private CobolNumeric() {
    }

    /**
     * COBOL {@code FUNCTION TEST-NUMVAL-C}: returns 0 when the string is a valid numeric
     * (optionally signed, optionally with currency sign, commas and a decimal point).
     */
    public static boolean isValidNumericWithCurrency(String value) {
        return parseNumericWithCurrency(value) != null;
    }

    /**
     * COBOL {@code FUNCTION NUMVAL-C}: converts a keyed amount into a scaled decimal.
     *
     * @return the value, or {@code null} when the string is not a valid numeric
     */
    public static BigDecimal parseNumericWithCurrency(String value) {
        if (CobolText.isBlank(value)) {
            return null;
        }
        String text = value.trim();
        boolean negative = false;
        if (text.startsWith("-") || text.startsWith("+")) {
            negative = text.charAt(0) == '-';
            text = text.substring(1).trim();
        } else if (text.endsWith("-") || text.endsWith("+")) {
            negative = text.charAt(text.length() - 1) == '-';
            text = text.substring(0, text.length() - 1).trim();
        } else if (text.startsWith("(") && text.endsWith(")")) {
            negative = true;
            text = text.substring(1, text.length() - 1).trim();
        }
        if (text.startsWith("$")) {
            text = text.substring(1).trim();
        }
        text = text.replace(",", "");
        if (text.isEmpty()) {
            return null;
        }
        int dots = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.') {
                dots++;
                if (dots > 1) {
                    return null;
                }
            } else if (!Character.isDigit(c)) {
                return null;
            }
        }
        if (text.equals(".")) {
            return null;
        }
        BigDecimal parsed = new BigDecimal(text).setScale(MONEY_SCALE, RoundingMode.DOWN);
        if (parsed.precision() - parsed.scale() > MONEY_INTEGER_DIGITS) {
            return null;
        }
        return negative ? parsed.negate() : parsed;
    }

    /** Screen presentation of an {@code S9(10)V99} field: plain signed decimal, two places. */
    public static String format(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY).toPlainString();
    }

    /**
     * Edited presentation of an amount as the BMS maps declare it,
     * {@code PIC +ZZZ,ZZZ,ZZZ.99}: a leading sign, zero suppressed grouped digits and two
     * decimal places, right justified in fifteen characters.
     */
    public static String formatEdited(BigDecimal value) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        String sign = amount.signum() < 0 ? "-" : "+";
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        String digits = format.format(amount.abs().setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
        int width = EDITED_MONEY_WIDTH - 1;
        if (digits.length() > width) {
            // A MOVE into the edit field truncates the high order digits the mask cannot hold.
            digits = digits.substring(digits.length() - width);
            if (digits.startsWith(",")) {
                digits = ' ' + digits.substring(1);
            }
        }
        return sign + CobolText.padLeft(digits, width);
    }

    /** Normalises a stored amount to scale 2 so comparisons match COBOL numeric equality. */
    public static BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    /** COBOL numeric comparison: 0.00 equals 0, unlike {@link BigDecimal#equals}. */
    public static boolean equalsNumeric(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }
}
