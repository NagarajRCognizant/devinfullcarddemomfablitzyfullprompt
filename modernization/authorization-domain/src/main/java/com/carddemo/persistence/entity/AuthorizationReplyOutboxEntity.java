package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Transactional outbox for the authorization reply.
 *
 * <p>COPAUA0C writes the authorization to IMS and puts the reply on
 * AWS.M2.CARDDEMO.PAUTH.REPLY inside one CICS unit of work, so the acquirer can never be told an
 * answer the database does not hold. A Kafka send is not part of the database transaction, so the
 * reply is written to this table in the same transaction as the authorization and published
 * afterwards; if the publish fails the relay retries it from the table. This is the compensating
 * mechanism for CMD-12 in the consistency-model delta register.
 */
@Entity
@Table(name = "authorization_reply_outbox")
public class AuthorizationReplyOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "request_id", length = 64, nullable = false)
    private String requestId;

    /** MQMD-REPLYTOQ of the request becomes the reply topic taken from the record header. */
    @Column(name = "reply_topic", length = 255, nullable = false)
    private String replyTopic;

    /** The card number: the partition key that preserves per card ordering. */
    @Column(name = "message_key", length = 32, nullable = false)
    private String messageKey;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "payload", length = 120, nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getReplyTopic() {
        return replyTopic;
    }

    public void setReplyTopic(String replyTopic) {
        this.replyTopic = replyTopic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }
}
