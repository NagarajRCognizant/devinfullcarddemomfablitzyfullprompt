package com.carddemo.authorization.domain;

/**
 * The PA-MATCH-STATUS condition values of CIPAUDTY.
 *
 * <p>Kept as the single character the source stores rather than an enum column, because the purge
 * batch, the detail screen and the unloaded EBCDIC data all exchange this exact value.
 */
public final class MatchStatus {

    /** PA-MATCH-PENDING: an approved authorization awaiting a matching transaction. */
    public static final String PENDING = "P";

    /** PA-MATCH-AUTH-DECLINED. */
    public static final String AUTH_DECLINED = "D";

    /** PA-MATCH-PENDING-EXPIRED. */
    public static final String PENDING_EXPIRED = "E";

    /** PA-MATCHED-WITH-TRAN. */
    public static final String MATCHED_WITH_TRAN = "M";

    private MatchStatus() {
    }
}
