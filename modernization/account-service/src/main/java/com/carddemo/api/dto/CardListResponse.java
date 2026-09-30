package com.carddemo.api.dto;

import java.util.List;

/**
 * One page of the card list map CCRDLIA (program COCRDLIC).
 *
 * <p>The map holds seven rows and the COMMAREA carried the first and last card number of the page
 * plus the page number so that PF7 and PF8 could restart the browse; the same three values are
 * returned to the client, which sends them back on the next request. {@code nextPageAvailable}
 * is CA-NEXT-PAGE-EXISTS, set by the extra READNEXT the source performs once a page is full.
 */
public record CardListResponse(
        List<CardListRow> rows,
        int pageNumber,
        String firstCardNumber,
        String lastCardNumber,
        boolean nextPageAvailable,
        String errorMessage,
        String infoMessage) {

    public record CardListRow(String cardNumber, String accountId, String activeStatus) {
    }
}
