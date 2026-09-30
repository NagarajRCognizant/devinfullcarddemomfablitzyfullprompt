package com.carddemo.authorization.messaging;

import com.carddemo.authorization.config.AuthorizationTopicsProperties;
import com.carddemo.authorization.service.AuthorizationOutcome;
import com.carddemo.authorization.service.AuthorizationRequestProcessor;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The Kafka replacement for the CICS MQ trigger that started transaction CP00.
 *
 * <p>COPAUA0C was started by a trigger message, then looped MQGET/process/SYNCPOINT up to
 * WS-REQSTS-PROCESS-LIMIT times. The container owns that loop now: one record is one unit of work,
 * and the offset advances only after the transaction commits, so the "at most 500 then end the
 * transaction" throttle is expressed as consumer concurrency and max-poll settings instead of a
 * counter in the program. The reply destination and correlation id travel as headers, replacing
 * MQMD-REPLYTOQ and MQMD-CORRELID.
 */
@Component
public class AuthorizationRequestListener {

    /** The header that carries MQMD-CORRELID. */
    public static final String CORRELATION_ID_HEADER = "carddemo-correlation-id";

    /** The header that carries MQMD-REPLYTOQ, resolved to a topic. */
    public static final String REPLY_TO_HEADER = "carddemo-reply-to";

    private static final Logger log = LoggerFactory.getLogger(AuthorizationRequestListener.class);

    private final AuthorizationRequestProcessor processor;
    private final AuthorizationTopicsProperties properties;

    public AuthorizationRequestListener(AuthorizationRequestProcessor processor,
                                        AuthorizationTopicsProperties properties) {
        this.processor = processor;
        this.properties = properties;
    }

    @KafkaListener(
            id = "authorization-request",
            topics = "${carddemo.authorization.kafka.request-topic}",
            groupId = "${carddemo.authorization.kafka.group-id:carddemo-authorization}")
    public void onRequest(ConsumerRecord<String, String> record) {
        AuthorizationRequestMessage request = AuthorizationRequestMessage.parse(record.value());
        String correlationId = header(record, CORRELATION_ID_HEADER)
                // MQCI-NONE was a valid correlation id; the request id stands in for it so a reply
                // is always correlatable by the requester.
                .orElse(request.requestId());
        // MQMD-REPLYTOQ was frequently left blank, in which case the CSD default reply queue was
        // used; the configured reply topic plays that role.
        String replyTopic = header(record, REPLY_TO_HEADER).orElseGet(properties::replyTopic);

        AuthorizationOutcome outcome = processor.process(request, correlationId, replyTopic);
        log.info("Authorization {} for card {} responded {}/{}{}",
                request.transactionId(), request.maskedCardNumber(),
                outcome.reply().authRespCode(), outcome.reply().authRespReason(),
                outcome.replayed() ? " (replayed)" : "");
    }

    private static Optional<String> header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return Optional.empty();
        }
        String value = new String(header.value(), StandardCharsets.UTF_8).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
}
