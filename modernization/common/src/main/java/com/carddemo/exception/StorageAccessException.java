package com.carddemo.exception;

/**
 * A file operation returned a response other than NORMAL or NOTFND.
 *
 * <p>The online programs write the WS-FILE-ERROR-MESSAGE text and then abend, so this is the fatal
 * path of the file status handling and not an input error.
 */
public class StorageAccessException extends BusinessRuleException {

    public StorageAccessException(String message) {
        super(message);
    }

    public StorageAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
