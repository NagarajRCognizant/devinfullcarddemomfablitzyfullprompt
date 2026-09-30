package com.carddemo.authorization.domain;

import java.math.BigDecimal;

/**
 * The read outcomes and credit positions 6000-MAKE-DECISION sees after paragraphs 5100 to 5500.
 *
 * <p>The source keeps these as six separate working-storage flags plus two record areas. Collecting
 * them into one immutable value keeps the decision a pure function of the reads, which is what
 * makes the decline-reason precedence testable branch by branch.
 */
public record AuthorizationReadState(
        boolean cardFoundInXref,
        boolean accountFoundInMaster,
        boolean customerFoundInMaster,
        boolean summarySegmentFound,
        BigDecimal summaryCreditLimit,
        BigDecimal summaryCreditBalance,
        BigDecimal accountCreditLimit,
        BigDecimal accountCurrentBalance) {

    /**
     * 5100-READ-XREF-RECORD on DFHRESP(NOTFND): the card is unknown, and the paragraph also sets
     * NFOUND-ACCT-IN-MSTR so the account branch of the decision cannot be taken on stale data.
     */
    public static AuthorizationReadState cardNotFound() {
        return new AuthorizationReadState(false, false, false, false, null, null, null, null);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** PA-CREDIT-LIMIT minus PA-CREDIT-BALANCE of the PAUTSUM0 segment. */
    public BigDecimal summaryAvailableAmount() {
        return zeroIfNull(summaryCreditLimit).subtract(zeroIfNull(summaryCreditBalance));
    }

    /** ACCT-CREDIT-LIMIT minus ACCT-CURR-BAL of the account master record. */
    public BigDecimal accountAvailableAmount() {
        return zeroIfNull(accountCreditLimit).subtract(zeroIfNull(accountCurrentBalance));
    }

    /**
     * The record-not-found group of the decline EVALUATE. All three flags map to 3100 and are
     * tested before INSUFFICIENT-FUND, so a request that is both short of credit and missing its
     * customer record is declined with 3100, exactly as the source EVALUATE orders it.
     */
    public boolean anyRecordMissing() {
        return !cardFoundInXref || !accountFoundInMaster || !customerFoundInMaster;
    }
}
