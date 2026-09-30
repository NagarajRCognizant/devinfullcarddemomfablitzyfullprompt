package com.carddemo.batch.transaction;

import com.carddemo.cobol.ZonedDecimalCodec;
import java.math.BigDecimal;

/**
 * One DALYTRAN-RECORD (app/cpy/CVTRA06Y.cpy, LRECL 350 FB), as read from the sequential daily
 * transaction file.
 *
 * <p>The raw line is kept because the reject record of CBTRN02C paragraph 2500-WRITE-REJECT-REC is
 * the unaltered 350 bytes followed by an 80 byte validation trailer, so a rejected transaction can
 * be corrected and resubmitted byte for byte.
 */
public record DailyTransaction(
        String id,
        String typeCd,
        Integer catCd,
        String source,
        String description,
        BigDecimal amount,
        Long merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip,
        String cardNumber,
        String originalTimestamp,
        String processingTimestamp,
        String rawRecord) {

    /** LRECL of the DALYTRAN file, which the reject record repeats unchanged. */
    public static final int RECORD_LENGTH = 350;

    /**
     * Splits one fixed length record into its fields.
     *
     * <p>The amount is a signed display field, so its sign travels in the last character as a
     * zoned decimal overpunch; the two count fields are unsigned display.
     */
    public static DailyTransaction parse(String line) {
        String record = pad(line);
        return new DailyTransaction(
                record.substring(0, 16),
                record.substring(16, 18),
                number(record.substring(18, 22)).intValue(),
                trailing(record.substring(22, 32)),
                trailing(record.substring(32, 132)),
                ZonedDecimalCodec.decode(record.substring(132, 143), 2),
                number(record.substring(143, 152)),
                trailing(record.substring(152, 202)),
                trailing(record.substring(202, 252)),
                trailing(record.substring(252, 262)),
                record.substring(262, 278),
                record.substring(278, 304),
                record.substring(304, 330),
                record);
    }

    /** The first ten characters of the original timestamp, which the expiry edit compares. */
    public String originalDate() {
        return originalTimestamp.length() >= 10 ? originalTimestamp.substring(0, 10) : originalTimestamp;
    }

    private static String pad(String line) {
        if (line.length() >= RECORD_LENGTH) {
            return line;
        }
        return line + " ".repeat(RECORD_LENGTH - line.length());
    }

    private static Long number(String field) {
        String digits = field.trim();
        return digits.isEmpty() ? 0L : Long.parseLong(digits);
    }

    private static String trailing(String field) {
        return field.stripTrailing();
    }
}
