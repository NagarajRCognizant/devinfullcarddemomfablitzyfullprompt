package com.carddemo.transaction.client;

import java.math.BigDecimal;

/**
 * The account state after a balance adjustment.
 *
 * @param applied false when the key had already been applied, so the balance is the one the first
 *     call produced
 */
public record BalanceAdjustmentResult(
        long accountId,
        boolean applied,
        BigDecimal currentBalance,
        BigDecimal currentCycleCredit,
        BigDecimal currentCycleDebit) {
}
