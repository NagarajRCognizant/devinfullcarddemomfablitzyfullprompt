package com.carddemo.api.dto;

import com.carddemo.domain.validation.FieldFlag;
import java.util.Map;

/**
 * Error contract of the identity service, identical in shape to the account service one.
 *
 * <p>{@code message} is the WS-MESSAGE text of the source program and {@code fieldFlags} are the
 * fields the screen would highlight through the -1 cursor moves.
 */
public record ErrorResponse(String status, String message, Map<String, FieldFlag> fieldFlags) {
}
