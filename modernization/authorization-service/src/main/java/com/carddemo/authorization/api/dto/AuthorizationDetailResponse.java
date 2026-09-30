package com.carddemo.authorization.api.dto;


/**
 * Map COPAU1A of transaction CPVD, as POPULATE-AUTH-DETAILS fills it.
 *
 * <p>The edited display fields of the map are kept as strings next to the raw values, because the
 * screen showed them in the source's own edited form: the response code as A or D, the reason as
 * {@code nnnn-DESCRIPTION} from the decline reason table, the card expiry date as {@code MM/YY} and
 * the fraud field as {@code F-MM/DD/YY} or a single hyphen. {@code nextAuthKey} carries what PF8
 * did: the key of the following authorization of the same account, or null at the end of the chain.
 */
public record AuthorizationDetailResponse(
        long accountId,
        String authKey,
        String cardNumber,
        String authorizationDate,
        String authorizationTime,
        MoneyValue approvedAmount,
        MoneyValue transactionAmount,
        String approvalStatus,
        String responseCode,
        String responseReason,
        String processingCode,
        String posEntryMode,
        String messageSource,
        String merchantCategoryCode,
        String cardExpiryDate,
        String authorizationType,
        String transactionId,
        String matchStatus,
        String fraudStatus,
        String merchantName,
        String merchantId,
        String merchantCity,
        String merchantState,
        String merchantZip,
        String nextAuthKey,
        String message) {
}
