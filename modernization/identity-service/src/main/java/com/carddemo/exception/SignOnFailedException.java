package com.carddemo.exception;

/**
 * The sign-on could not be completed: READ-USER-SEC-FILE of COSGN00C either did not find the user
 * id or found it with a different password.
 *
 * <p>COSGN00C redisplays the map with a different message for each case, so the message is carried
 * unchanged; both cases become one unauthenticated outcome in the API.
 */
public class SignOnFailedException extends BusinessRuleException {

    public SignOnFailedException(String message) {
        super(message);
    }
}
