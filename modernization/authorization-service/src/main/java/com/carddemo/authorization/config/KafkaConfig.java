package com.carddemo.authorization.config;

import com.carddemo.authorization.client.CardholderLookupException;
import com.carddemo.authorization.messaging.MalformedAuthorizationRequestException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * The messaging wiring of the converted MQ trigger path.
 *
 * <p>A transient failure - the account service being unreachable, a lock wait - is retried a bounded
 * number of times with a fixed backoff, which is the equivalent of the trigger monitor presenting
 * the message again. A message that can never succeed, because it is not a valid CCPAURQY buffer,
 * is not retried at all: it goes straight to the dead-letter topic, where the source would have
 * written an error record and moved on. The recoverer keeps the failed record on the same partition
 * number, so per-card ordering is preserved in the dead-letter topic too.
 */
@Configuration
public class KafkaConfig {

    @Bean
    NewTopic authorizationRequestTopic(AuthorizationTopicsProperties properties) {
        return TopicBuilder.name(properties.requestTopic())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }

    @Bean
    NewTopic authorizationReplyTopic(AuthorizationTopicsProperties properties) {
        return TopicBuilder.name(properties.replyTopic())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }

    @Bean
    NewTopic authorizationRequestDeadLetterTopic(AuthorizationTopicsProperties properties) {
        return TopicBuilder.name(properties.deadLetterTopic())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }

    @Bean
    DefaultErrorHandler authorizationErrorHandler(KafkaOperations<String, String> template,
                                                  AuthorizationTopicsProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) ->
                        new TopicPartition(properties.deadLetterTopic(), record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(properties.retryBackoffMillis(), properties.retryAttempts()));
        handler.addNotRetryableExceptions(MalformedAuthorizationRequestException.class);
        handler.addRetryableExceptions(CardholderLookupException.class);
        return handler;
    }
}
