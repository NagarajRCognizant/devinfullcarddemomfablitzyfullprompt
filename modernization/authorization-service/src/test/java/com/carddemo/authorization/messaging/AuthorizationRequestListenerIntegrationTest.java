package com.carddemo.authorization.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.carddemo.AuthorizationPostgresIntegrationTest;
import com.carddemo.authorization.client.CardholderLookupClient;
import com.carddemo.authorization.client.CardholderLookupException;
import com.carddemo.authorization.client.CardholderView;
import com.carddemo.persistence.repository.AuthorizationDetailRepository;
import com.carddemo.persistence.repository.AuthorizationReplyOutboxRepository;
import com.carddemo.persistence.repository.AuthorizationRequestLogRepository;
import com.carddemo.persistence.repository.AuthorizationSummaryRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

/**
 * The converted MQ trigger path end to end on an embedded broker: a request record on
 * {@code carddemo.authorization.request.v1} is authorized and a CCPAURLY reply appears on the reply
 * topic, keyed by card number and carrying the correlation id the requester sent.
 *
 * <p>This is the proving test for the consistency-model delta register entries that replace the
 * CICS syncpoint around the MQ get: at-least-once delivery compensated by the request log, and the
 * reply published from the transactional outbox rather than inside the authorization transaction.
 */
@EmbeddedKafka(
        partitions = 3,
        topics = {"carddemo.authorization.request.v1", "carddemo.authorization.reply.v1",
                "carddemo.authorization.request.DLT", "acquirer.reply.v1"})
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "carddemo.authorization.outbox.relay-delay-millis=100",
        // A malformed request must reach the dead-letter topic without retrying; a transient
        // failure is retried twice more, which the retry test asserts through the lookup attempts.
        "carddemo.authorization.kafka.retry-attempts=2",
        "carddemo.authorization.kafka.retry-backoff-millis=100"
})
class AuthorizationRequestListenerIntegrationTest extends AuthorizationPostgresIntegrationTest {

    private static final String REQUEST_TOPIC = "carddemo.authorization.request.v1";
    private static final String REPLY_TOPIC = "carddemo.authorization.reply.v1";
    private static final String ACQUIRER_REPLY_TOPIC = "acquirer.reply.v1";
    private static final String DLT_TOPIC = "carddemo.authorization.request.DLT";
    private static final String CARD = "4111111111111111";
    private static final long ACCOUNT = 99L;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @MockBean
    private CardholderLookupClient cardholderLookupClient;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private AuthorizationSummaryRepository summaryRepository;
    @Autowired
    private AuthorizationDetailRepository detailRepository;
    @Autowired
    private AuthorizationRequestLogRepository requestLogRepository;
    @Autowired
    private AuthorizationReplyOutboxRepository outboxRepository;

    private Consumer<String, String> replyConsumer;
    private Consumer<String, String> deadLetterConsumer;

    @BeforeEach
    void subscribe() {
        given(cardholderLookupClient.byCardNumber(anyString())).willReturn(new CardholderView(
                true, true, true, CARD, ACCOUNT, 55L, "Y", new BigDecimal("5000.00"),
                new BigDecimal("500.00"), new BigDecimal("0.00"), "JOHN", "Q", "PUBLIC",
                "1 MAIN ST", null, "DALLAS", "TX", "75001", "2145551212"));
        replyConsumer = consumer(REPLY_TOPIC);
        deadLetterConsumer = consumer(DLT_TOPIC);
    }

    @AfterEach
    void unsubscribe() {
        replyConsumer.close();
        deadLetterConsumer.close();
    }

    /**
     * A consumer in its own group, so it reads the topic from the beginning. The class shares one
     * broker across its tests, so each assertion looks for its own record rather than assuming the
     * topic holds exactly one.
     */
    private Consumer<String, String> consumer(String topic) {
        Map<String, Object> props =
                KafkaTestUtils.consumerProps("probe-" + UUID.randomUUID(), "true", broker);
        Consumer<String, String> consumer =
                new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
        broker.consumeFromAnEmbeddedTopic(consumer, topic);
        return consumer;
    }

    private static String request(String amount, String transactionId) {
        return String.join(",", "240301", "134530", CARD, "01", "1225", "0100", "POS", "000000",
                amount, "5411", "840", "05", "MERCH000000001", "ACME STORE", "DALLAS", "TX",
                "75001", transactionId);
    }

    private void send(String payload, String correlationId, String replyTo) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(REQUEST_TOPIC, CARD, payload);
        if (correlationId != null) {
            record.headers().add(AuthorizationRequestListener.CORRELATION_ID_HEADER,
                    correlationId.getBytes(StandardCharsets.UTF_8));
        }
        if (replyTo != null) {
            record.headers().add(AuthorizationRequestListener.REPLY_TO_HEADER,
                    replyTo.getBytes(StandardCharsets.UTF_8));
        }
        kafkaTemplate.send(record);
        kafkaTemplate.flush();
    }

    private static ConsumerRecord<String, String> awaitRecord(Consumer<String, String> consumer,
                                                              Predicate<String> matches) {
        long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (matches.test(record.value())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No matching record arrived within " + TIMEOUT);
    }

    private static ConsumerRecord<String, String> awaitReply(Consumer<String, String> consumer,
                                                             String transactionId) {
        return awaitRecord(consumer, value -> value.contains(transactionId));
    }

    private static String headerValue(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /** 1000-MAIN through 7100-SEND-RESPONSE: one request in, one reply out, one authorization stored. */
    @Test
    void anAuthorizationRequestIsAnsweredOnTheReplyTopic() {
        send(request("250.75", "TRAN0000000001"), "CORRELID-1", null);

        ConsumerRecord<String, String> reply = awaitReply(replyConsumer, "TRAN0000000001");

        AuthorizationReplyMessage parsed = AuthorizationReplyMessage.parse(reply.value());
        assertThat(parsed.cardNum()).isEqualTo(CARD);
        assertThat(parsed.authRespCode()).isEqualTo("00");
        assertThat(parsed.authRespReason()).isEqualTo("0000");
        assertThat(parsed.approvedAmt()).isEqualByComparingTo("250.75");
        // The card number is the partition key, so the authorizations of one card stay ordered.
        assertThat(reply.key()).isEqualTo(CARD);
        // MQMD-CORRELID travels back on the reply, as 7000-BUILD-REPLY-MQMD set it.
        assertThat(headerValue(reply, AuthorizationRequestListener.CORRELATION_ID_HEADER))
                .isEqualTo("CORRELID-1");

        assertThat(summaryRepository.findById(ACCOUNT)).isPresent();
        assertThat(detailRepository.countByIdAcctId(ACCOUNT)).isEqualTo(1);
    }

    /** MQMD-REPLYTOQ: a requester that names its own destination is answered there. */
    @Test
    void theReplyGoesToTheDestinationTheRequesterNamed() {
        Consumer<String, String> acquirerConsumer = consumer(ACQUIRER_REPLY_TOPIC);
        try {
            send(request("10.00", "TRAN0000000002"), "CORRELID-2", ACQUIRER_REPLY_TOPIC);

            ConsumerRecord<String, String> reply = awaitReply(acquirerConsumer, "TRAN0000000002");

            assertThat(AuthorizationReplyMessage.parse(reply.value()).authRespCode()).isEqualTo("00");
            assertThat(headerValue(reply, AuthorizationRequestListener.CORRELATION_ID_HEADER))
                    .isEqualTo("CORRELID-2");
        } finally {
            acquirerConsumer.close();
        }
    }

    /**
     * At-least-once delivery: a request redelivered because the offset commit was lost is decided
     * once. The reply of the first delivery was committed to the outbox with the authorization, so
     * it is published exactly once and the redelivery replays the stored answer instead of
     * authorizing again. This is the compensation for the once-only MQ get inside the syncpoint.
     */
    @Test
    void aRedeliveredRequestIsDecidedOnceAndReplaysTheStoredReply() {
        String payload = request("100.00", "TRAN0000000003");

        send(payload, "CORRELID-3", null);
        ConsumerRecord<String, String> first = awaitReply(replyConsumer, "TRAN0000000003");
        assertThat(AuthorizationReplyMessage.parse(first.value()).approvedAmt())
                .isEqualByComparingTo("100.00");

        send(payload, "CORRELID-3", null);

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(requestLogRepository.count()).isEqualTo(1);
            assertThat(outboxRepository.count()).isEqualTo(1);
        });
        assertThat(detailRepository.countByIdAcctId(ACCOUNT)).isEqualTo(1);
        assertThat(summaryRepository.findById(ACCOUNT).orElseThrow().getApprovedAuthCnt())
                .isEqualTo((short) 1);
        assertThat(requestLogRepository.count()).isEqualTo(1);
    }

    /**
     * A buffer that is not a CCPAURQY request can never succeed, so it is not retried: it goes to
     * the dead-letter topic and the stream keeps moving.
     */
    @Test
    void aMalformedRequestIsDeadLetteredWithoutRetrying() {
        String malformed = "this is not an authorization request";

        send(malformed, "CORRELID-4", null);

        ConsumerRecord<String, String> dead =
                awaitRecord(deadLetterConsumer, malformed::equals);
        assertThat(dead.value()).isEqualTo(malformed);
        assertThat(summaryRepository.count()).isZero();
        assertThat(requestLogRepository.count()).isZero();

        // The next valid request is still processed, so one bad message does not stop the stream.
        send(request("5.00", "TRAN0000000005"), "CORRELID-5", null);
        ConsumerRecord<String, String> reply = awaitReply(replyConsumer, "TRAN0000000005");
        assertThat(AuthorizationReplyMessage.parse(reply.value()).authRespCode()).isEqualTo("00");
    }

    /**
     * A failure of the remote account read is transient, so it is retried the configured number of
     * times with backoff before the record is dead-lettered. The source could not fail this way:
     * the cross reference, account and customer reads were local VSAM reads in the same unit of
     * work as the authorization.
     */
    @Test
    void anAccountServiceFailureIsRetriedAndThenDeadLettered() {
        given(cardholderLookupClient.byCardNumber(anyString()))
                .willThrow(new CardholderLookupException("account service unavailable", null));

        send(request("20.00", "TRAN0000000006"), "CORRELID-6", null);

        ConsumerRecord<String, String> dead =
                awaitRecord(deadLetterConsumer, value -> value.contains("TRAN0000000006"));
        assertThat(AuthorizationRequestMessage.parse(dead.value()).transactionId())
                .isEqualTo("TRAN0000000006");

        // Two retries after the first attempt, and nothing decided on unreadable account data.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                Mockito.verify(cardholderLookupClient, Mockito.times(3)).byCardNumber(CARD));
        assertThat(summaryRepository.count()).isZero();
    }
}
