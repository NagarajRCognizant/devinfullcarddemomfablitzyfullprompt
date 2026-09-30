package com.carddemo.transaction.domain;

import java.math.BigInteger;

/**
 * The transaction identifier arithmetic of COTRN02C and COBIL00C.
 *
 * <p>Both programs position the TRANSACT browse at HIGH-VALUES, read the previous record, move the
 * {@code PIC X(16)} key into a {@code PIC 9(16)} counter, add one and move it back. Reproducing
 * that as a 16 digit zero padded string keeps the generated keys collating in the same order as
 * the source, which is what the paging of COTRN00C depends on.
 */
public final class TransactionIdentifiers {

    /** TRAN-ID is PIC X(16). */
    public static final int LENGTH = 16;

    private TransactionIdentifiers() {
    }

    /**
     * The next identifier after the highest one on file.
     *
     * @param highestOnFile the current highest key, or {@code null} when the file is empty, which
     *     is the state in which the source counter stays at its initial value of zero
     */
    public static String next(String highestOnFile) {
        BigInteger current = highestOnFile == null || highestOnFile.isBlank()
                ? BigInteger.ZERO
                : new BigInteger(highestOnFile.trim());
        return format(current.add(BigInteger.ONE));
    }

    /** A keyed or stored identifier padded to the stored width, as a COBOL MOVE into PIC 9(16). */
    public static String normalise(String tranId) {
        return format(new BigInteger(tranId.trim()));
    }

    private static String format(BigInteger value) {
        String digits = value.toString();
        if (digits.length() >= LENGTH) {
            return digits.substring(digits.length() - LENGTH);
        }
        return "0".repeat(LENGTH - digits.length()) + digits;
    }
}
