package com.carddemo.api.dto;

import com.carddemo.domain.validation.FieldFlag;
import java.util.Map;

/**
 * Result of an Account Update turn: the state, the two message lines of the map, the values to
 * redisplay and the per field highlight flags the BMS attribute bytes carried.
 */
public record AccountUpdateResponse(
        AccountUpdateStatus status,
        String infoMessage,
        String errorMessage,
        AccountUpdateForm original,
        AccountUpdateForm updated,
        AccountDetails details,
        Map<String, FieldFlag> fieldFlags) {
}
