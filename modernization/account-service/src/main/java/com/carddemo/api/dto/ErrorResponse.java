package com.carddemo.api.dto;

import com.carddemo.domain.validation.FieldFlag;
import java.util.Map;

/**
 * Error contract shared by the validation, not-found, conflict and internal-error paths.
 *
 * <p>{@code message} is the WS-RETURN-MSG text of the source program and {@code fieldFlags} are
 * the fields the screen would highlight.
 */
public record ErrorResponse(String status, String message, Map<String, FieldFlag> fieldFlags) {
}
