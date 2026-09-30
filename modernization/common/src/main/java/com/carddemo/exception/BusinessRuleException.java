package com.carddemo.exception;

/**
 * Base class for the outcomes the legacy screens report through WS-RETURN-MSG.
 *
 * <p>The COBOL programs redisplay the map with a single message and a set of highlighted fields
 * instead of raising exceptions; in the target these outcomes become typed exceptions that the
 * REST layer maps onto status codes, while the message text stays unchanged.
 */
public abstract class BusinessRuleException extends RuntimeException {

    protected BusinessRuleException(String message) {
        super(message);
    }

    protected BusinessRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
