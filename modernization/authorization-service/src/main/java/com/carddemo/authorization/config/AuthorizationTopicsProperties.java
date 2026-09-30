package com.carddemo.authorization.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The target topic design that replaces MQ queues AWS.M2.CARDDEMO.PAUTH.REQUEST and
 * AWS.M2.CARDDEMO.PAUTH.REPLY.
 *
 * <p>The queue names are not reused: a Kafka topic is a durable partitioned log, not a queue, so the
 * names carry the domain event and a version instead. The request topic is partitioned by card
 * number so all requests for one card are ordered relative to each other, which is the only ordering
 * the source relied on; there is no global order across cards, and none was available over MQ
 * either. The dead-letter topic replaces the CICS trigger-monitor retry and the error log of
 * 9500-LOG-ERROR for messages that cannot be parsed or processed.
 */
@ConfigurationProperties(prefix = "carddemo.authorization.kafka")
public record AuthorizationTopicsProperties(
        String requestTopic,
        String replyTopic,
        String deadLetterTopic,
        String fraudMarkedTopic,
        int partitions,
        short replicationFactor,
        int retryAttempts,
        long retryBackoffMillis) {

    public AuthorizationTopicsProperties {
        requestTopic = requestTopic == null ? "carddemo.authorization.request.v1" : requestTopic;
        replyTopic = replyTopic == null ? "carddemo.authorization.reply.v1" : replyTopic;
        deadLetterTopic = deadLetterTopic == null
                ? "carddemo.authorization.request.DLT" : deadLetterTopic;
        fraudMarkedTopic = fraudMarkedTopic == null ? "carddemo.fraud.marked.v1" : fraudMarkedTopic;
        partitions = partitions <= 0 ? 3 : partitions;
        replicationFactor = replicationFactor <= 0 ? (short) 1 : replicationFactor;
        // Bounded, never infinite: the source processed at most WS-REQSTS-PROCESS-LIMIT messages
        // per trigger and abandoned a failing one to the error log rather than spinning on it.
        retryAttempts = retryAttempts <= 0 ? 3 : retryAttempts;
        retryBackoffMillis = retryBackoffMillis <= 0 ? 500 : retryBackoffMillis;
    }
}
