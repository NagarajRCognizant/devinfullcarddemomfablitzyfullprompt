package com.carddemo.api.dto;

/**
 * The keyed CCRDUPA map of program COCRDUPC.
 *
 * <p>Every field is a string because the edits of 1220-EDIT-CARD through 1260-EDIT-EXPIRY-YEAR are
 * defined on the keyed characters. {@code fetched} carries the record as the previous turn
 * displayed it, which is what the COBOL held in the COMMAREA and compared against the record it
 * re-read under a lock, so that a record changed in the meantime is reported rather than
 * overwritten.
 */
public record CardUpdateRequest(
        String accountId,
        String cardNumber,
        String embossedName,
        String activeStatus,
        String expiryYear,
        String expiryMonth,
        CardDetailResponse fetched) {
}
