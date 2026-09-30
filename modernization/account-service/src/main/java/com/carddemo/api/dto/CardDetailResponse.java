package com.carddemo.api.dto;

/**
 * The card detail map CCRDSLA (program COCRDSLC) and the fetched values the update map starts from.
 *
 * <p>The CVV is shown by both maps, so it is carried here as the source carried it; the expiry
 * date is split into the year and month parts the update map edits separately.
 */
public record CardDetailResponse(
        String accountId,
        String cardNumber,
        String embossedName,
        String activeStatus,
        String expiryYear,
        String expiryMonth,
        String expiryDay,
        String cvvCode,
        String infoMessage) {
}
