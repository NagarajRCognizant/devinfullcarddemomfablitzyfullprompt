package com.carddemo.api.dto;

/**
 * The result of an add, update or delete, carrying the WS-MESSAGE text the screen would show.
 *
 * <p>{@code user} is the record as it stands after the operation, or null after a delete.
 */
public record UserActionResponse(String message, UserDetail user) {
}
