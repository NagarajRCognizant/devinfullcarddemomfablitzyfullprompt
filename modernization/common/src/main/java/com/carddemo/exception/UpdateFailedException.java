package com.carddemo.exception;

/**
 * The records were locked but a rewrite failed, which in COACTUPC paragraph 9600 rolls the unit of
 * work back and reports "Update of record failed".
 */
public class UpdateFailedException extends BusinessRuleException {

    public UpdateFailedException(String message) {
        super(message);
    }

    public UpdateFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
