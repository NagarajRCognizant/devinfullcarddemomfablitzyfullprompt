package com.carddemo.transaction.api.dto;

/**
 * The single message line the screens showed in ERRMSGO, with a code so a REST client can tell the
 * outcomes apart without parsing the text.
 */
public record ApiError(String status, String message) {
}
