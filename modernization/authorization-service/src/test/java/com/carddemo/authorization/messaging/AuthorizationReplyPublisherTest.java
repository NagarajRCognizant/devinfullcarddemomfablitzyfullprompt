package com.carddemo.authorization.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.carddemo.authorization.config.AuthorizationTopicsProperties;
import com.carddemo.persistence.entity.AuthorizationReplyOutboxEntity;
import com.carddemo.persistence.repository.AuthorizationReplyOutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * The relay that replaces the MQPUT1 of 7100-SEND-RESPONSE. The reply is written to an outbox row
 * inside the same transaction as the decision, so the publish itself must be able to fail without
 * losing the reply: the row stays unpublished and the attempt counter rises, which is the visible
 * compensation recorded in the consistency-model delta register for the lost SYNCPOINT.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationReplyPublisherTest {

    @Mock
    private AuthorizationReplyOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void aReplyWithoutAReplyDestinationOrCorrelationIdGoesToTheDefaultTopic() {
        AuthorizationReplyOutboxEntity reply = reply();
        reply.setReplyTopic("  ");
        reply.setCorrelationId(null);
        when(outboxRepository.findUnpublished(any())).thenReturn(List.of(reply));
        org.mockito.Mockito.doReturn(CompletableFuture.completedFuture(null))
                .when(kafkaTemplate).send(any(ProducerRecord.class));

        publisher().publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> record =
                ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(kafkaTemplate).send(record.capture());
        assertThat(record.getValue().topic()).isEqualTo("carddemo.authorization.reply");
        assertThat(record.getValue().headers()
                .lastHeader(AuthorizationRequestListener.CORRELATION_ID_HEADER)).isNull();
        assertThat(reply.getPublishedAt()).isNotNull();
        assertThat(reply.getAttempts()).isEqualTo(1);
    }

    @Test
    void theNamedReplyDestinationAndCorrelationIdOfTheRequestAreCarriedOnTheReply() {
        AuthorizationReplyOutboxEntity reply = reply();
        when(outboxRepository.findUnpublished(any())).thenReturn(List.of(reply));
        org.mockito.Mockito.doReturn(CompletableFuture.completedFuture(null))
                .when(kafkaTemplate).send(any(ProducerRecord.class));

        publisher().publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> record =
                ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(kafkaTemplate).send(record.capture());
        assertThat(record.getValue().topic()).isEqualTo("carddemo.authorization.reply.alt");
        assertThat(new String(record.getValue().headers()
                .lastHeader(AuthorizationRequestListener.CORRELATION_ID_HEADER).value(),
                StandardCharsets.UTF_8)).isEqualTo("CORREL-1");
    }

    @Test
    void aFailedPublishLeavesTheReplyUnpublishedAndCountsTheAttempt() {
        AuthorizationReplyOutboxEntity reply = reply();
        when(outboxRepository.findUnpublished(any())).thenReturn(List.of(reply));
        org.mockito.Mockito.doReturn(
                CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .when(kafkaTemplate).send(any(ProducerRecord.class));

        publisher().publishPending();

        assertThat(reply.getPublishedAt()).isNull();
        assertThat(reply.getAttempts()).isEqualTo(1);
    }

    private AuthorizationReplyPublisher publisher() {
        return new AuthorizationReplyPublisher(outboxRepository, kafkaTemplate,
                new AuthorizationTopicsProperties("carddemo.authorization.request",
                        "carddemo.authorization.reply", "carddemo.authorization.request.DLT",
                        "carddemo.fraud.marked", 3, (short) 1, 3, 500),
                Clock.fixed(Instant.parse("2025-09-01T12:00:00Z"), ZoneOffset.UTC));
    }

    private static AuthorizationReplyOutboxEntity reply() {
        AuthorizationReplyOutboxEntity reply = new AuthorizationReplyOutboxEntity();
        reply.setRequestId("4111111111111111|250901|120000|TXN0000000000001");
        reply.setMessageKey("4111111111111111");
        reply.setReplyTopic("carddemo.authorization.reply.alt");
        reply.setCorrelationId("CORREL-1");
        reply.setPayload("4111111111111111,TXN0000000000001,120000,00,0000,       100.00,");
        return reply;
    }
}
