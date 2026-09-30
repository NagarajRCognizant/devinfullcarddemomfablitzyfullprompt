package com.carddemo.cobol;

import java.math.BigDecimal;
import java.nio.charset.Charset;

/**
 * Reads the fields of a fixed length COBOL record, the counterpart of {@link CobolRecordBuilder}.
 *
 * <p>Offsets are zero based byte offsets into the record, which is how a COBOL group item is laid
 * out: character fields are decoded with the record charset while COMP-3 and COMP fields are read
 * as raw nibbles and bytes.
 */
public final class CobolRecordReader {

    private final byte[] record;
    private final Charset charset;

    public CobolRecordReader(byte[] record, Charset charset) {
        this.record = record;
        this.charset = charset;
    }

    /** PIC X(n) with the trailing spaces COBOL pads with removed. */
    public String picX(int offset, int length) {
        return new String(record, offset, length, charset).stripTrailing();
    }

    /** PIC 9(n) unsigned display. */
    public long pic9(int offset, int length) {
        String digits = new String(record, offset, length, charset).trim();
        return digits.isEmpty() ? 0L : Long.parseLong(digits);
    }

    /** PIC S9(int)V(scale) DISPLAY with a trailing sign overpunch. */
    public BigDecimal zoned(int offset, int integerDigits, int scale) {
        return ZonedDecimalCodec.decode(new String(record, offset, integerDigits + scale, charset), scale);
    }

    /** PIC S9(int)V(scale) COMP-3 packed decimal. */
    public BigDecimal packed(int offset, int integerDigits, int scale) {
        int digits = integerDigits + scale;
        int width = (digits % 2 == 0 ? digits + 2 : digits + 1) / 2;
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < width; i++) {
            int current = record[offset + i] & 0xFF;
            value.append((char) ('0' + (current >> 4)));
            if (i < width - 1) {
                value.append((char) ('0' + (current & 0x0F)));
            }
        }
        int sign = record[offset + width - 1] & 0x0F;
        BigDecimal unscaled = new BigDecimal(value.toString());
        BigDecimal amount = unscaled.movePointLeft(scale);
        return sign == 0x0D ? amount.negate() : amount;
    }

    /** PIC 9(n) COMP or PIC S9(int)V(scale) COMP big endian binary. */
    public BigDecimal binary(int offset, int integerDigits, int scale) {
        int width = CobolRecordBuilder.binaryWidth(integerDigits + scale);
        long value = 0;
        for (int i = 0; i < width; i++) {
            value = (value << 8) | (record[offset + i] & 0xFF);
        }
        if (width < 8) {
            // Sign extend the two and four byte forms so negative values survive the read.
            int bits = width * 8;
            long signBit = 1L << (bits - 1);
            if ((value & signBit) != 0) {
                value -= 1L << bits;
            }
        }
        return BigDecimal.valueOf(value).movePointLeft(scale);
    }

    /** The COMP field width a given number of digits occupies. */
    public static int binaryWidth(int digits) {
        return CobolRecordBuilder.binaryWidth(digits);
    }
}
