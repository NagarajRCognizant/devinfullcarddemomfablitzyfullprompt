package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Duplicate detection for the authorization request stream, which has no counterpart in the source.
 *
 * <p>MQ with a CICS syncpoint gave COPAUA0C once-only delivery: the get, the IMS updates and the
 * reply put committed together, so a request was never applied twice. Kafka gives at-least-once
 * delivery, and the authorization is a state-changing financial update, so the consumer stores the
 * business identity of every request it has applied together with the reply it produced, inside the
 * same transaction as the authorization itself. A redelivery finds the row, skips the decision and
 * re-sends the stored reply, which keeps both the counters and the acquirer's answer stable. This is
 * the compensating mechanism for CMD-10 in the consistency-model delta register.
 */
@Entity
@Table(name = "authorization_request_log")
public class AuthorizationRequestLogEntity {

    /**
     * The business identity of a request: card number, acquirer supplied authorization date and
     * time and transaction id - the four fields of CCPAURQY that identify the acquirer's attempt.
     */
    @Id
    @Column(name = "request_id", length = 64, nullable = false)
    private String requestId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_num", length = 16, nullable = false, columnDefinition = "char(16)")
    private String cardNum;

    /** MQMD-CORRELID of the request message, carried on the Kafka record header. */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "acct_id")
    private Long acctId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_key", length = 14, columnDefinition = "char(14)")
    private String authKey;

    /** The reply exactly as it was published, so a redelivery replays it byte for byte. */
    @Column(name = "reply_payload", length = 120, nullable = false)
    private String replyPayload;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Long getAcctId() {
        return acctId;
    }

    public void setAcctId(Long acctId) {
        this.acctId = acctId;
    }

    public String getAuthKey() {
        return authKey;
    }

    public void setAuthKey(String authKey) {
        this.authKey = authKey;
    }

    public String getReplyPayload() {
        return replyPayload;
    }

    public void setReplyPayload(String replyPayload) {
        this.replyPayload = replyPayload;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
