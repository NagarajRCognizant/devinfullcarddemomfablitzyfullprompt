package com.carddemo.authorization.domain;

/**
 * The PA-AUTH-FRAUD condition values of CIPAUDTY.
 *
 * <p>A new detail is written with blanks, which is neither confirmed nor removed; the CPVD toggle
 * of COPAUS1C moves between 'F' and 'R' and never back to blanks.
 */
public final class FraudStatus {

    /** The value 4000-WRITE-AUTH-DETAIL leaves in the field. */
    public static final String NONE = " ";

    /** PA-FRAUD-CONFIRMED. */
    public static final String CONFIRMED = "F";

    /** PA-FRAUD-REMOVED. */
    public static final String REMOVED = "R";

    private FraudStatus() {
    }

    /** PF5 on CPVD: a confirmed authorization becomes removed, anything else becomes confirmed. */
    public static String toggle(String current) {
        return CONFIRMED.equals(current == null ? null : current.trim()) ? REMOVED : CONFIRMED;
    }
}
