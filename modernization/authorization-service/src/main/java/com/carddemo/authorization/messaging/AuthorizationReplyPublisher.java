package com.carddemo.authorization.messaging;

import com.carddemo.authorization.config.AuthorizationTopicsProperties;
import com.carddemo.persistence.entity.AuthorizationReplyOutboxEntity;
import com.carddemo.persistence.repository.AuthorizationReplyOutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The relay half of the transactional outbox: it publishes 7100-SEND-RESPONSE replies that were
 * committed with the authorization.
 *
 * <p>Relaying on a short fixed delay rather than only on the commit event means a reply survives the
 * service being killed between the commit and the send, which is exactly the window the CICS
 * two-phase commit did not have. Publishing is at-least-once and the reply carries the correlation
 * id, so a requester that sees a duplicate reply discards it the same way it would have discarded a
 * duplicate MQ reply.
 */
@Component
public class AuthorizationReplyPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationReplyPublisher.class);
    private static final int BATCH_SIZE = 100;

    private final AuthorizationReplyOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AuthorizationTopicsProperties properties;
    private final Clock clock;

    public AuthorizationReplyPublisher(AuthorizationReplyOutboxRepository outboxRepository,
                                       KafkaTemplate<String, String> kafkaTemplate,
                                       AuthorizationTopicsProperties properties,
                                       Clock clock) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${carddemo.authorization.outbox.relay-delay-millis:200}")
    @Transactional
    public void publishPending() {
        List<AuthorizationReplyOutboxEntity> pending =
                outboxRepository.findUnpublished(PageRequest.of(0, BATCH_SIZE));
        for (AuthorizationReplyOutboxEntity reply : pending) {
            publish(reply);
        }
    }

    private void publish(AuthorizationReplyOutboxEntity reply) {
        // MQMD-REPLYTOQ was optional on the request; when the requester named no destination the
        // configured reply topic is used, which is the queue the CSD defines as the default.
        String topic = reply.getReplyTopic() == null || reply.getReplyTopic().isBlank()
                ? properties.replyTopic()
                : reply.getReplyTopic();
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, reply.getMessageKey(), reply.getPayload());
        if (reply.getCorrelationId() != null) {
            record.headers().add(AuthorizationRequestListener.CORRELATION_ID_HEADER,
                    reply.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        }
        reply.setAttempts(reply.getAttempts() + 1);
        try {
            kafkaTemplate.send(record).get();
            reply.setPublishedAt(Instant.now(clock));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted publishing authorization reply {}", reply.getRequestId());
        } catch (Exception e) {
            // The row stays unpublished, so the next relay pass retries it. Attempts are counted so
            // a permanently failing reply is visible to operations instead of silently looping.
            log.error("Failed to publish authorization reply {} (attempt {}): {}",
                    reply.getRequestId(), reply.getAttempts(), e.getMessage());
        }
    }
}
