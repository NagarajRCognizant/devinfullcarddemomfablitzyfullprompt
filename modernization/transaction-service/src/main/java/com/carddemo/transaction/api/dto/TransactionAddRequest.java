package com.carddemo.transaction.api.dto;

/**
 * The COTRN2A map as it is keyed.
 *
 * <p>Every field is a string because the source edits the keyed characters before it converts
 * them: "Type CD must be Numeric..." can only be reported for a value that reached the program as
 * text. The confirmation field is the CONFIRMI byte, which drives the two-step add.
 */
public record TransactionAddRequest(
        String accountId,
        String cardNumber,
        String typeCode,
        String categoryCode,
        String source,
        String description,
        String amount,
        String originalDate,
        String processedDate,
        String merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip,
        String confirm) {
}
