package com.carddemo.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Paragraph 6000-MAKE-DECISION of COPAUA0C, branch by branch.
 *
 * <p>The three credit sources of the source IF (summary segment, account master, neither) and the
 * EVALUATE that picks the decline reason are each driven directly, because they are the rules the
 * acquirer sees in the reply.
 */
class AuthorizationDecisionEngineTest {

    private static AuthorizationReadState withSummary(String limit, String balance) {
        return new AuthorizationReadState(true, true, true, true,
                new BigDecimal(limit), new BigDecimal(balance), new BigDecimal("1.00"),
                new BigDecimal("0.00"));
    }

    private static AuthorizationReadState withoutSummary(String limit, String balance) {
        return new AuthorizationReadState(true, true, true, false, null, null,
                new BigDecimal(limit), new BigDecimal(balance));
    }

    @Test
    void anAmountWithinTheSummaryAvailableCreditIsApproved() {
        AuthorizationDecision decision = AuthorizationDecisionEngine.decide(
                withSummary("5000.00", "1000.00"), new BigDecimal("4000.00"));

        assertThat(decision.approved()).isTrue();
        assertThat(decision.responseCode()).isEqualTo("00");
        assertThat(decision.responseReason()).isEqualTo("0000");
        assertThat(decision.approvedAmount()).isEqualByComparingTo("4000.00");
        assertThat(decision.matchStatus()).isEqualTo("P");
    }

    /** The comparison is strictly greater than, so spending the last cent of credit is approved. */
    @Test
    void anAmountEqualToTheAvailableCreditIsApproved() {
        assertThat(AuthorizationDecisionEngine
                .decide(withSummary("5000.00", "1000.00"), new BigDecimal("4000.00")).approved())
                .isTrue();
    }

    @Test
    void anAmountOverTheSummaryAvailableCreditIsDeclinedForInsufficientFunds() {
        AuthorizationDecision decision = AuthorizationDecisionEngine.decide(
                withSummary("5000.00", "1000.00"), new BigDecimal("4000.01"));

        assertThat(decision.approved()).isFalse();
        assertThat(decision.responseCode()).isEqualTo("05");
        assertThat(decision.responseReason()).isEqualTo("4100");
        assertThat(decision.approvedAmount()).isEqualByComparingTo("0.00");
        assertThat(decision.matchStatus()).isEqualTo("D");
    }

    /** Without a summary segment the credit position comes from the account master. */
    @Test
    void theFirstAuthorizationOfAnAccountIsDecidedFromTheAccountMaster() {
        assertThat(AuthorizationDecisionEngine
                .decide(withoutSummary("2000.00", "500.00"), new BigDecimal("1500.00")).approved())
                .isTrue();
        assertThat(AuthorizationDecisionEngine
                .decide(withoutSummary("2000.00", "500.00"), new BigDecimal("1500.01"))
                .responseReason()).isEqualTo("4100");
    }

    @Test
    void anUnknownCardIsDeclinedWithTheRecordNotFoundReason() {
        AuthorizationDecision decision = AuthorizationDecisionEngine
                .decide(AuthorizationReadState.cardNotFound(), new BigDecimal("1.00"));

        assertThat(decision.approved()).isFalse();
        assertThat(decision.responseReason()).isEqualTo("3100");
    }

    /** The record-not-found group is tested before insufficient funds in the source EVALUATE. */
    @Test
    void aMissingCustomerRecordOutranksInsufficientFunds() {
        AuthorizationReadState state = new AuthorizationReadState(true, true, false, true,
                new BigDecimal("100.00"), new BigDecimal("100.00"), null, null);

        assertThat(AuthorizationDecisionEngine.decide(state, new BigDecimal("500.00"))
                .responseReason()).isEqualTo("3100");
    }

    /**
     * An account whose master row was read but holds no credit position declines for insufficient
     * funds, because the available amount computes to zero rather than leaving the flag unset.
     */
    @Test
    void anAccountWithNoCreditPositionDeclinesForInsufficientFunds() {
        AuthorizationReadState state = new AuthorizationReadState(true, true, true, false,
                null, null, null, null);

        AuthorizationDecision decision =
                AuthorizationDecisionEngine.decide(state, new BigDecimal("10.00"));

        assertThat(decision.approved()).isFalse();
        assertThat(decision.responseReason()).isEqualTo("4100");
    }

    /**
     * WHEN OTHER of the decline EVALUATE is unreachable in the source: a decline is only set when
     * a record is missing (3100) or the amount exceeds the available credit (4100), so no execution
     * path leaves every reason flag unset. The 9000 mapping is kept, and asserted here directly,
     * because the reply layout and the CPVD reason table both define it; it is recorded as dead
     * code in the business rule catalog rather than removed.
     */
    @Test
    void theUnknownReasonRemainsDefinedEvenThoughNoDecisionPathReachesIt() {
        assertThat(DeclineReasonFlag.NONE.reasonCode()).isEqualTo("9000");
        assertThat(AuthorizationDecision.declined(DeclineReasonFlag.NONE).responseReason())
                .isEqualTo("9000");
    }

    /** A missing amount is treated as zero, as a blank NUMVAL field would have been. */
    @Test
    void aNullAmountIsTreatedAsZero() {
        assertThat(AuthorizationDecisionEngine.decide(withSummary("10.00", "0.00"), null)
                .approvedAmount()).isEqualByComparingTo("0.00");
    }
}
