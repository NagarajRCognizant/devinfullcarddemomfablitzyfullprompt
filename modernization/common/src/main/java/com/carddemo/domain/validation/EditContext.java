package com.carddemo.domain.validation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accumulates the outcome of the COACTUPC edit routines: the INPUT-ERROR switch, the single
 * WS-RETURN-MSG shown on line 24 of the map and the per field edit flags.
 *
 * <p>The legacy code guards every message with {@code IF WS-RETURN-MSG-OFF}, so only the first
 * failing edit produces a message even though all edits still run and set their own flag.
 */
public class EditContext {

    private boolean inputError;
    private String returnMessage;
    private final Map<String, FieldFlag> flags = new LinkedHashMap<>();

    /** Records a failed edit: sets INPUT-ERROR, the field flag and (once) WS-RETURN-MSG. */
    public void reject(String field, FieldFlag flag, String message) {
        inputError = true;
        flags.put(field, flag);
        message(message);
    }

    /** Marks a field as having passed its edits. */
    public void accept(String field) {
        flags.put(field, FieldFlag.VALID);
    }

    /** {@code IF WS-RETURN-MSG-OFF ... STRING ... INTO WS-RETURN-MSG}. */
    public void message(String message) {
        if (returnMessage == null && message != null) {
            this.returnMessage = message;
        }
    }

    /** Sets INPUT-ERROR without touching a field flag (used by the account key edit). */
    public void flagInputError() {
        inputError = true;
    }

    public boolean isInputError() {
        return inputError;
    }

    public String getReturnMessage() {
        return returnMessage;
    }

    public FieldFlag flag(String field) {
        return flags.getOrDefault(field, FieldFlag.VALID);
    }

    public boolean isValid(String field) {
        return flag(field) == FieldFlag.VALID;
    }

    public Map<String, FieldFlag> getFlags() {
        return flags;
    }

    /** Field flags that are not valid, i.e. the fields the screen highlights. */
    public Map<String, FieldFlag> getFailedFields() {
        Map<String, FieldFlag> failed = new LinkedHashMap<>();
        flags.forEach((field, flag) -> {
            if (flag != FieldFlag.VALID) {
                failed.put(field, flag);
            }
        });
        return failed;
    }
}
