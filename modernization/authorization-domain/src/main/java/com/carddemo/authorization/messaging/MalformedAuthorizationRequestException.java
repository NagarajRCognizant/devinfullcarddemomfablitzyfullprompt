package com.carddemo.authorization.messaging;

/**
 * A request message that CCPAURQY cannot describe.
 *
 * <p>COPAUA0C had no such path: an UNSTRING of a short message left the remaining fields at their
 * previous contents and the authorization was decided on them. A Kafka consumer can route the
 * record to a dead letter topic instead, which is the compensating behaviour recorded in the
 * unsupported construct register; the exception is what triggers it and is deliberately not
 * retried.
 */
public class MalformedAuthorizationRequestException extends RuntimeException {

    public MalformedAuthorizationRequestException(String message) {
        super(message);
    }
}
