package com.carddemo.cobol;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Decodes the signed zoned decimal fields of the supplied sample data files.
 *
 * <p>{@code app/data/ASCII/*.txt} are single byte translations of the EBCDIC datasets, so a
 * {@code S9(10)V99} amount arrives as eleven digits plus a trailing overpunch character
 * ('{' = +0 ... 'I' = +9, '}' = -0 ... 'R' = -9).
 */
public final class ZonedDecimalCodec {

    private ZonedDecimalCodec() {
    }

    public static BigDecimal decode(String field, int scale) {
        if (field == null || field.isBlank()) {
            return BigDecimal.ZERO.setScale(scale);
        }
        String text = field.trim();
        char last = text.charAt(text.length() - 1);
        String digits = text.substring(0, text.length() - 1);
        boolean negative = false;
        int lastDigit;
        if (Character.isDigit(last)) {
            lastDigit = last - '0';
        } else {
            int positive = "{ABCDEFGHI".indexOf(last);
            int minus = "}JKLMNOPQR".indexOf(last);
            if (positive >= 0) {
                lastDigit = positive;
            } else if (minus >= 0) {
                lastDigit = minus;
                negative = true;
            } else {
                throw new IllegalArgumentException("Unsupported zoned decimal field: " + field);
            }
        }
        BigInteger unscaled = new BigInteger(digits + lastDigit);
        BigDecimal value = new BigDecimal(negative ? unscaled.negate() : unscaled, scale);
        return value;
    }

    /** The inverse of {@link #decode}: {@code width} digits with the sign in the last of them. */
    public static String encode(BigDecimal value, int width, int scale) {
        BigDecimal scaled = value.setScale(scale, RoundingMode.DOWN);
        String digits = scaled.abs().unscaledValue().toString();
        if (digits.length() > width) {
            throw new IllegalArgumentException(
                    "Value " + value + " does not fit in " + width + " digits");
        }
        digits = "0".repeat(width - digits.length()) + digits;
        int lastDigit = digits.charAt(width - 1) - '0';
        char overpunch = (scaled.signum() < 0 ? "}JKLMNOPQR" : "{ABCDEFGHI").charAt(lastDigit);
        return digits.substring(0, width - 1) + overpunch;
    }
}
