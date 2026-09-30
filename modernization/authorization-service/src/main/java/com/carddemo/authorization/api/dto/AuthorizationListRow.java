package com.carddemo.authorization.api.dto;


/**
 * One of the five detail lines of map COPAU0A, as POPULATE-AUTH-LIST fills it.
 *
 * <p>{@code authKey} replaces CDEMO-CPVS-AUTH-KEYS: the COMMAREA array that let the terminal turn a
 * selection character into a segment key becomes the identifier carried in the row, so selecting a
 * row is a request for that authorization rather than a position on the last screen sent.
 */
public record AuthorizationListRow(
        String authKey,
        String transactionId,
        String authorizationDate,
        String authorizationTime,
        String authorizationType,
        String approvalStatus,
        String matchStatus,
        MoneyValue approvedAmount) {
}
