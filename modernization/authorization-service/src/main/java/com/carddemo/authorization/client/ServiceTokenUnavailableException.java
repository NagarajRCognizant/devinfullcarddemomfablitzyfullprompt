package com.carddemo.authorization.client;

/**
 * The realm refused or could not be reached for the service token of the Kafka path.
 *
 * <p>Treated like a failed cardholder lookup: the listener retries within its bounds and the request
 * ends in the dead letter topic rather than being decided on absent data.
 */
public class ServiceTokenUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ServiceTokenUnavailableException(String message) {
        super(message);
    }
}
