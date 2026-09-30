package com.carddemo.api.dto;

/**
 * The two input fields of the sign-on map (app/bms/COSGN00.bms USERIDI and PASSWDI).
 *
 * <p>No bean validation annotations are used: COSGN00C checks the fields itself, in order, and
 * reports one message at a time, so the checks live in the service to keep that behaviour.
 */
public record SignOnRequest(String userId, String password) {
}
