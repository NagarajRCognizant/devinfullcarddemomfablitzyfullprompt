package com.carddemo.transaction.client;

import java.math.BigDecimal;

/**
 * The account rewrite of COBIL00C, CBTRN02C and CBACT04C, as the account service accepts it.
 *
 * @param idempotencyKey the key that makes a retried call return the first outcome instead of
 *     subtracting the amount twice, which a rewrite inside one CICS unit of recovery did not need
 * @param kind which of the three source specific rewrites to apply
 * @param amount the amount to apply, with the sign the source field carried
 */
public record BalanceAdjustment(String idempotencyKey, String kind, BigDecimal amount) {

    /** COBIL00C: subtract the payment from the current balance, leaving the cycle amounts alone. */
    public static BalanceAdjustment billPayment(String idempotencyKey, BigDecimal amount) {
        return new BalanceAdjustment(idempotencyKey, "BILL_PAYMENT", amount);
    }

    /**
     * CBTRN02C 2800-UPDATE-ACCOUNT-REC: add the posted amount to the balance and, by its sign, to
     * the cycle credit or the cycle debit.
     */
    public static BalanceAdjustment posting(String idempotencyKey, BigDecimal amount) {
        return new BalanceAdjustment(idempotencyKey, "POSTING", amount);
    }

    /** CBACT04C 1050-UPDATE-ACCOUNT: add the interest total, then zero both cycle amounts. */
    public static BalanceAdjustment interestSettlement(String idempotencyKey, BigDecimal amount) {
        return new BalanceAdjustment(idempotencyKey, "INTEREST_SETTLEMENT", amount);
    }
}
