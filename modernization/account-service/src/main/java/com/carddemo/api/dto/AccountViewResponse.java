package com.carddemo.api.dto;

/**
 * Result of the Account View inquiry (COACTVWC): the map output fields plus the WS-INFO-MSG line.
 */
public record AccountViewResponse(AccountDetails details, String infoMessage) {
}
