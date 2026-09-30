package com.carddemo.keycloak;

/**
 * The realm could not be reached or refused an admin call of a user administration screen.
 *
 * <p>Reported as the {@code Unable to Update User...} path of the source program: a screen never
 * reports success when only one of the two stores was written.
 */
public class DirectoryAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DirectoryAccessException(String message) {
        super(message);
    }

    public DirectoryAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
