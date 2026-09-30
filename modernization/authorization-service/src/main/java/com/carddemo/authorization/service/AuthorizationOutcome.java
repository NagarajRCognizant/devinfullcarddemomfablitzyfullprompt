package com.carddemo.authorization.service;

import com.carddemo.authorization.messaging.AuthorizationReplyMessage;

/**
 * The result of processing one authorization request.
 *
 * <p>{@code replayed} is true when the request had already been decided and the stored reply was
 * returned instead of a second decision. The source had no such state, because MQ removed the
 * message inside the syncpoint; it exists here to make duplicate Kafka delivery observable in
 * metrics and tests rather than invisible.
 */
public record AuthorizationOutcome(AuthorizationReplyMessage reply, boolean replayed) {
}
