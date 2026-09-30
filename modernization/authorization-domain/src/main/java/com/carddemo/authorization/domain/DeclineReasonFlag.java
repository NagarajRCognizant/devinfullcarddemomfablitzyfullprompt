package com.carddemo.authorization.domain;

/**
 * The WS-DECLINE-REASON-FLG condition names of COPAUA0C, plus the read outcomes that reach the
 * same EVALUATE in 6000-MAKE-DECISION.
 *
 * <p>Modelled as an enum carrying the four character reason code so the decision path and the
 * response reason cannot drift apart. NONE is the state of the flag before any branch sets it,
 * which the source's {@code WHEN OTHER} maps to 9000.
 */
public enum DeclineReasonFlag {

    /** No specific flag was set; WHEN OTHER of the EVALUATE. */
    NONE("9000"),

    /** CARD-NFOUND-XREF, NFOUND-ACCT-IN-MSTR and NFOUND-CUST-IN-MSTR share one reason. */
    RECORD_NOT_FOUND("3100"),

    /** INSUFFICIENT-FUND: the transaction amount exceeds the available credit. */
    INSUFFICIENT_FUND("4100"),

    /** CARD-NOT-ACTIVE. */
    CARD_NOT_ACTIVE("4200"),

    /** ACCOUNT-CLOSED. */
    ACCOUNT_CLOSED("4300"),

    /** CARD-FRAUD. */
    CARD_FRAUD("5100"),

    /** MERCHANT-FRAUD. */
    MERCHANT_FRAUD("5200");

    private final String reasonCode;

    DeclineReasonFlag(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    /** The value 6000-MAKE-DECISION moves to PA-RL-AUTH-RESP-REASON for this flag. */
    public String reasonCode() {
        return reasonCode;
    }
}
