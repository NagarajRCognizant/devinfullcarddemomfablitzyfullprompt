package com.carddemo.transaction.client;

/**
 * The account service could not be reached or refused the call.
 *
 * <p>The source read ACCTDAT and CXACAIX as local VSAM files, so this failure mode is a
 * consequence of the bounded context split and is recorded in the consistency-model delta
 * register. It is reported with the source's own "Unable to lookup Account..." family of messages
 * rather than being treated as a missing record.
 */
public class AccountServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccountServiceException(String message) {
        super(message);
    }

    public AccountServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
