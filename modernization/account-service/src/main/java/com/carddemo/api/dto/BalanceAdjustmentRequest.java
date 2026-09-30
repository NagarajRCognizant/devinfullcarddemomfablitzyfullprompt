package com.carddemo.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * One balance change of the transaction context, as the account context is asked to apply it.
 *
 * <p>{@code kind} names which source paragraph the caller is reproducing, because the three of
 * them move different fields:
 *
 * <ul>
 *   <li>{@code POSTING} - CBTRN02C 2800-UPDATE-ACCOUNT-REC: the amount is added to the balance and,
 *       by its sign, to the cycle credit or the cycle debit.
 *   <li>{@code BILL_PAYMENT} - COBIL00C: the payment is subtracted from the balance, leaving zero,
 *       and the cycle amounts are untouched, as in the source.
 *   <li>{@code INTEREST_SETTLEMENT} - CBACT04C 1050-UPDATE-ACCOUNT: the interest total is added to
 *       the balance and both cycle amounts are reset to zero.
 * </ul>
 *
 * @param idempotencyKey the caller's key for this change; a repeat returns the first outcome
 * @param kind which source paragraph is being reproduced
 * @param amount the signed amount, with the sign the source field carried
 */
public record BalanceAdjustmentRequest(
        @NotBlank @Size(max = 64) String idempotencyKey,
        @NotBlank @Pattern(regexp = "POSTING|BILL_PAYMENT|INTEREST_SETTLEMENT") String kind,
        @NotNull BigDecimal amount) {
}
