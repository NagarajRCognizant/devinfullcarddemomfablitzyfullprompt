package com.carddemo.authorization.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.carddemo.authorization.config.AuthorizationTopicsProperties;
import com.carddemo.authorization.messaging.AuthorizationReplyMessage;
import com.carddemo.authorization.service.AuthorizationOutcome;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The header handling of the consumer that replaces the MQGET of COPAUA0C. MQMD-CORRELID could be
 * MQCI-NONE and MQMD-REPLYTOQ could be blank; both are represented here by an absent, empty or
 * value-less header, and both must fall back to the values the source used - the request's own id
 * and the default reply destination - rather than replying nowhere.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationRequestListenerTest {

    private static final String REQUEST =
            "250901,120000,4111111111111111,01,1230,0100,POS,000000,0000000100.00,5411,840,05,"
                    + "M001,MERCHANT,SEATTLE,WA,98101,TXN0000000000001";

    @Mock
    private com.carddemo.authorization.service.AuthorizationRequestProcessor processor;

    @Test
    void aRequestWithoutHeadersRepliesOnTheDefaultTopicCorrelatedByItsOwnRequestId() {
        AuthorizationRequestListener listener = listener();
        ConsumerRecord<String, String> record = record();
        when(processor.process(any(), any(), any())).thenReturn(outcome());

        listener.onRequest(record);

        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> replyTopic = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(processor)
                .process(any(), correlationId.capture(), replyTopic.capture());
        assertThat(correlationId.getValue()).isNotBlank();
        assertThat(replyTopic.getValue()).isEqualTo("carddemo.authorization.reply");
    }

    @Test
    void blankAndValueLessHeadersAreTreatedAsAbsent() {
        AuthorizationRequestListener listener = listener();
        ConsumerRecord<String, String> record = record();
        record.headers().add(AuthorizationRequestListener.CORRELATION_ID_HEADER, null);
        record.headers().add(AuthorizationRequestListener.REPLY_TO_HEADER,
                "   ".getBytes(StandardCharsets.UTF_8));
        when(processor.process(any(), any(), any())).thenReturn(outcome());

        listener.onRequest(record);

        ArgumentCaptor<String> correlationId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> replyTopic = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(processor)
                .process(any(), correlationId.capture(), replyTopic.capture());
        assertThat(correlationId.getValue()).isNotBlank();
        assertThat(replyTopic.getValue()).isEqualTo("carddemo.authorization.reply");
    }

    @Test
    void thePresentedCorrelationIdAndReplyDestinationAreUsedWhenTheRequesterSuppliesThem() {
        AuthorizationRequestListener listener = listener();
        ConsumerRecord<String, String> record = record();
        record.headers().add(AuthorizationRequestListener.CORRELATION_ID_HEADER,
                "CORREL-1".getBytes(StandardCharsets.UTF_8));
        record.headers().add(AuthorizationRequestListener.REPLY_TO_HEADER,
                "carddemo.authorization.reply.alt".getBytes(StandardCharsets.UTF_8));
        when(processor.process(any(), any(), any())).thenReturn(outcome());

        listener.onRequest(record);

        org.mockito.Mockito.verify(processor)
                .process(any(), org.mockito.ArgumentMatchers.eq("CORREL-1"),
                        org.mockito.ArgumentMatchers.eq("carddemo.authorization.reply.alt"));
    }

    private AuthorizationRequestListener listener() {
        return new AuthorizationRequestListener(processor, new AuthorizationTopicsProperties(
                "carddemo.authorization.request", "carddemo.authorization.reply",
                "carddemo.authorization.request.DLT", "carddemo.fraud.marked",
                3, (short) 1, 3, 500));
    }

    private static ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>("carddemo.authorization.request", 0, 0L,
                "4111111111111111", REQUEST);
    }

    private static AuthorizationOutcome outcome() {
        AuthorizationReplyMessage reply = new AuthorizationReplyMessage("4111111111111111",
                "TXN0000000000001", "120000", "00", "0000", new java.math.BigDecimal("100.00"));
        return new AuthorizationOutcome(reply, false);
    }
}
