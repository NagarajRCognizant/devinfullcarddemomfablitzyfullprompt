package com.carddemo.transaction.api.dto;

/**
 * The COBIL0A map as it is keyed, plus the key that makes the payment idempotent.
 *
 * @param idempotencyKey supplied by the screen for the confirmed submission. The source had one
 *     unit of recovery for the transaction write and the account rewrite; here they are two
 *     services, so the key is what prevents a resubmission from paying the balance twice.
 */
public record BillPaymentRequest(String accountId, String confirm, String idempotencyKey) {
}
