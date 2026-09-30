package com.carddemo.api.dto;

import java.math.BigDecimal;

/**
 * The account fields after the change, so the caller can show what the source screen showed after
 * its own rewrite of the record.
 *
 * @param applied false when the key had already been applied and the stored outcome is returned
 */
public record BalanceAdjustmentResponse(
        long accountId,
        boolean applied,
        BigDecimal currentBalance,
        BigDecimal currentCycleCredit,
        BigDecimal currentCycleDebit) {
}
