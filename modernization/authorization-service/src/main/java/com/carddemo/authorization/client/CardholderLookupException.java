package com.carddemo.authorization.client;

/**
 * The account service could not be read.
 *
 * <p>This failure mode does not exist in the source, where the same data was a local VSAM read;
 * it is a consequence of the bounded-context split and is recorded in the consistency-model delta
 * register. It is always retried and never treated as "record not found", because an authorization
 * must not be declined for a card whose data merely could not be reached.
 */
public class CardholderLookupException extends RuntimeException {

    public CardholderLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
