package com.carddemo.cobol;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;

/**
 * Builds the fixed-format records written by the converted READACCT batch job.
 *
 * <p>CBACT01C writes DISPLAY, COMP-3 and PIC X fields into fixed length records, so the
 * converted writer assembles raw bytes rather than text lines: character fields are encoded
 * with the configured record charset (IBM037 reproduces the mainframe EBCDIC extract
 * byte-for-byte) while COMP-3 fields are emitted as packed nibbles that are charset
 * independent.
 */
public final class CobolRecordBuilder {

    private static final char[] POSITIVE_OVERPUNCH = {'{', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I'};
    private static final char[] NEGATIVE_OVERPUNCH = {'}', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R'};

    private final Charset charset;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public CobolRecordBuilder(Charset charset) {
        this.charset = charset;
    }

    /** PIC X(n) - left justified, space filled. */
    public CobolRecordBuilder picX(String value, int length) {
        return text(CobolText.padRight(value, length));
    }

    /** PIC 9(n) - unsigned display, right justified, zero filled. */
    public CobolRecordBuilder pic9(long value, int length) {
        String digits = Long.toString(Math.abs(value));
        return text(CobolText.padLeftZero(digits, length));
    }

    /**
     * PIC S9(int)V(scale) DISPLAY - zoned decimal with a trailing sign overpunch, which is
     * how the legacy extract and the supplied sample data represent signed display amounts.
     */
    public CobolRecordBuilder zoned(BigDecimal value, int integerDigits, int scale) {
        String digits = unscaledDigits(value, integerDigits, scale);
        boolean negative = value != null && value.signum() < 0;
        int last = digits.charAt(digits.length() - 1) - '0';
        char overpunch = negative ? NEGATIVE_OVERPUNCH[last] : POSITIVE_OVERPUNCH[last];
        return text(digits.substring(0, digits.length() - 1) + overpunch);
    }

    /** PIC S9(int)V(scale) COMP-3 - packed decimal, sign in the low nibble of the last byte. */
    public CobolRecordBuilder packed(BigDecimal value, int integerDigits, int scale) {
        String digits = unscaledDigits(value, integerDigits, scale);
        boolean negative = value != null && value.signum() < 0;
        // Packed decimal always holds an odd number of digit nibbles plus the sign nibble.
        if (digits.length() % 2 == 0) {
            digits = "0" + digits;
        }
        byte[] packed = new byte[(digits.length() + 1) / 2];
        int index = 0;
        for (int i = 0; i < digits.length() - 1; i += 2) {
            packed[index++] = (byte) (((digits.charAt(i) - '0') << 4) | (digits.charAt(i + 1) - '0'));
        }
        int sign = negative ? 0x0D : 0x0C;
        packed[index] = (byte) (((digits.charAt(digits.length() - 1) - '0') << 4) | sign);
        buffer.write(packed, 0, packed.length);
        return this;
    }

    /**
     * PIC 9(n) COMP or PIC S9(int)V(scale) COMP - a big endian binary integer holding the
     * unscaled value, in the two, four or eight bytes the digit count implies.
     */
    public CobolRecordBuilder binary(BigDecimal value, int integerDigits, int scale) {
        long unscaled = Long.parseLong(unscaledDigits(value, integerDigits, scale));
        if (value != null && value.signum() < 0) {
            unscaled = -unscaled;
        }
        int width = binaryWidth(integerDigits + scale);
        byte[] encoded = new byte[width];
        for (int i = width - 1; i >= 0; i--) {
            encoded[i] = (byte) (unscaled & 0xFF);
            unscaled >>= 8;
        }
        buffer.write(encoded, 0, width);
        return this;
    }

    /** The COMP field width the COBOL compiler allocates for a given number of digits. */
    public static int binaryWidth(int digits) {
        if (digits <= 4) {
            return 2;
        }
        return digits <= 9 ? 4 : 8;
    }

    /** Appends bytes that are already encoded, e.g. a field carried over from a previous record. */
    public CobolRecordBuilder raw(byte[] value) {
        buffer.write(value, 0, value.length);
        return this;
    }

    public CobolRecordBuilder text(String value) {
        byte[] encoded = value.getBytes(charset);
        buffer.write(encoded, 0, encoded.length);
        return this;
    }

    public byte[] build() {
        return buffer.toByteArray();
    }

    private static String unscaledDigits(BigDecimal value, int integerDigits, int scale) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        BigDecimal normalised = amount.setScale(scale, RoundingMode.DOWN).abs();
        String digits = normalised.unscaledValue().toString();
        int total = integerDigits + scale;
        if (digits.length() > total) {
            // COBOL MOVE truncates high order digits.
            return digits.substring(digits.length() - total);
        }
        return "0".repeat(total - digits.length()) + digits;
    }
}
