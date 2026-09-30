package com.carddemo.exception;

import com.carddemo.domain.validation.FieldFlag;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The edit routines found at least one problem, so the screen is redisplayed with the first
 * message and the failing fields highlighted.
 */
public class ScreenValidationException extends BusinessRuleException {

    private final String status;
    private final Map<String, FieldFlag> fieldFlags;

    public ScreenValidationException(String status, String message, Map<String, FieldFlag> fieldFlags) {
        super(message);
        this.status = status;
        this.fieldFlags = new LinkedHashMap<>(fieldFlags);
    }

    public String getStatus() {
        return status;
    }

    public Map<String, FieldFlag> getFieldFlags() {
        return fieldFlags;
    }
}
