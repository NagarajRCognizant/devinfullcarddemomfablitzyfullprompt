package com.carddemo.authorization.api.dto;

/**
 * The error contract of the authorization APIs: a status token for the client and the WS-MESSAGE
 * text the 3270 map showed, unchanged.
 */
public record ApiError(String status, String message) {
}
