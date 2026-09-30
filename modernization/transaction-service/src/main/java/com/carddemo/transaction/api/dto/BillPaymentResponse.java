package com.carddemo.transaction.api.dto;

/**
 * The bill payment screen after the request: the balance it displays, the identifier of the
 * transaction it wrote when the payment was confirmed, and the message line.
 */
public record BillPaymentResponse(
        String accountId,
        MoneyValue currentBalance,
        String tranId,
        boolean paid,
        String message) {
}
