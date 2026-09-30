package com.carddemo.exception;

/**
 * A regular user selected a menu option reserved for administrators
 * (COMEN01C paragraph PROCESS-ENTER-KEY).
 */
public class AdminOnlyOptionException extends BusinessRuleException {

    public AdminOnlyOptionException(String message) {
        super(message);
    }
}
