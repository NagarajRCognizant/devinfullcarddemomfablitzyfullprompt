package com.carddemo.batch.auth;

import java.math.BigDecimal;
import java.util.List;

/**
 * What one pass of the MAIN-PARA loop decided for a single PAUTSUM0 root: which of its PAUTDTL1
 * children have expired, the counter reversals those children cause, and whether the root itself
 * then qualifies for deletion.
 *
 * <p>The decision is separated from the database writes so the expiry arithmetic of paragraph
 * 4000-CHECK-IF-EXPIRED can be tested without a database, and so the writes of paragraphs
 * 5000-DELETE-AUTH-DTL and 6000-DELETE-AUTH-SUMMARY all happen inside the chunk transaction that
 * the checkpoint commits.
 */
public record SummaryPurgePlan(
        long acctId,
        int detailsRead,
        List<String> expiredAuthKeys,
        short approvedAuthCnt,
        BigDecimal approvedAuthAmt,
        short declinedAuthCnt,
        BigDecimal declinedAuthAmt,
        boolean deleteSummary) {

    public boolean hasExpiredDetails() {
        return !expiredAuthKeys.isEmpty();
    }
}
