package com.carddemo.authorization.domain;

import java.math.BigDecimal;

/**
 * The outcome of 6000-MAKE-DECISION: the response code, the reason and the approved amount.
 *
 * <p>Immutable value object so the decision can be unit tested without a database, a queue or the
 * account service, which is how the parity tests for the approval rules are written.
 */
public record AuthorizationDecision(
        boolean approved,
        String responseCode,
        String responseReason,
        BigDecimal approvedAmount) {

    /** {@code MOVE '00'} plus the requested amount. */
    public static final String APPROVED_CODE = "00";

    /** {@code MOVE '05'} plus a zero approved amount. */
    public static final String DECLINED_CODE = "05";

    /** {@code MOVE '0000' TO PA-RL-AUTH-RESP-REASON} before the decline EVALUATE. */
    public static final String APPROVED_REASON = "0000";

    public static AuthorizationDecision approved(BigDecimal transactionAmount) {
        return new AuthorizationDecision(true, APPROVED_CODE, APPROVED_REASON, transactionAmount);
    }

    public static AuthorizationDecision declined(DeclineReasonFlag flag) {
        return new AuthorizationDecision(false, DECLINED_CODE, flag.reasonCode(), BigDecimal.ZERO);
    }

    /** PA-MATCH-PENDING for an approval, PA-MATCH-AUTH-DECLINED otherwise. */
    public String matchStatus() {
        return approved ? MatchStatus.PENDING : MatchStatus.AUTH_DECLINED;
    }
}
