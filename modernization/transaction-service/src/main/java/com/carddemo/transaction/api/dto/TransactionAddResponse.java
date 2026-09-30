package com.carddemo.transaction.api.dto;

/**
 * The outcome of the add screen: the identifier the source computed and the message it showed.
 *
 * <p>The echoed key fields are the ones VALIDATE-INPUT-KEY-FIELDS fills in from the cross
 * reference, so keying an account number returns the card number it belongs to and the other way
 * round, exactly as the redisplayed map did.
 */
public record TransactionAddResponse(
        String tranId,
        String accountId,
        String cardNumber,
        String message) {
}
