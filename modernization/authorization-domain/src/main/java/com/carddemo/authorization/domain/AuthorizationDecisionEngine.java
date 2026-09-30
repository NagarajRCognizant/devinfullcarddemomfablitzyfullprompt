package com.carddemo.authorization.domain;

import java.math.BigDecimal;

/**
 * 6000-MAKE-DECISION of COPAUA0C, extracted as a dependency free domain component.
 *
 * <p>The source interleaves the decision with IMS calls, MQ handling and error logging. Isolating
 * the rules here lets the parity tests drive every branch of the source IF/EVALUATE directly, and
 * keeps the decision identical whether it is reached from a Kafka record or from a replay of one.
 */
public final class AuthorizationDecisionEngine {

    private AuthorizationDecisionEngine() {
    }

    /**
     * Decides an authorization from the read outcomes and the requested amount.
     *
     * <p>The credit position comes from the authorization summary when the segment exists, because
     * it already includes authorizations not yet posted to the account; otherwise from the account
     * master; and when neither could be read the authorization is declined outright.
     */
    public static AuthorizationDecision decide(AuthorizationReadState state, BigDecimal requestAmount) {
        BigDecimal amount = requestAmount == null ? BigDecimal.ZERO : requestAmount;
        boolean insufficientFund = false;
        boolean decline = false;

        if (state.summarySegmentFound()) {
            insufficientFund = amount.compareTo(state.summaryAvailableAmount()) > 0;
            decline = insufficientFund;
        } else if (state.accountFoundInMaster()) {
            insufficientFund = amount.compareTo(state.accountAvailableAmount()) > 0;
            decline = insufficientFund;
        } else {
            // ELSE SET DECLINE-AUTH with no specific reason flag of its own; the reason comes from
            // whichever read flag paragraphs 5100 to 5300 set.
            decline = true;
        }

        if (!decline) {
            return AuthorizationDecision.approved(amount);
        }
        return AuthorizationDecision.declined(reasonFor(state, insufficientFund));
    }

    /**
     * The EVALUATE that follows {@code IF AUTH-RESP-DECLINED}, in source order: the three
     * record-not-found flags share 3100 and are tested first, then insufficient funds, and
     * WHEN OTHER gives 9000 when no flag was set at all.
     */
    private static DeclineReasonFlag reasonFor(AuthorizationReadState state, boolean insufficientFund) {
        if (state.anyRecordMissing()) {
            return DeclineReasonFlag.RECORD_NOT_FOUND;
        }
        if (insufficientFund) {
            return DeclineReasonFlag.INSUFFICIENT_FUND;
        }
        return DeclineReasonFlag.NONE;
    }
}
