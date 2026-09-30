package com.carddemo.exception;

/**
 * WRITE-USER-SEC-FILE of COUSR01C received DFHRESP(DUPKEY) or DFHRESP(DUPREC): the user id is
 * already on the file.
 */
public class DuplicateUserException extends BusinessRuleException {

    public DuplicateUserException(String message) {
        super(message);
    }
}
