package com.carddemo.transaction.api.dto;

/** The COTRN01C view screen: every field of the TRANSACT record as the map shows it. */
public record TransactionDetailResponse(
        String tranId,
        String cardNumber,
        String typeCode,
        String categoryCode,
        String source,
        String description,
        MoneyValue amount,
        String originalTimestamp,
        String processedTimestamp,
        String merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip) {
}
