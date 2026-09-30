package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One accepted bill payment of COBIL00C, which the source did not record separately.
 *
 * <p>The source wrote the transaction and rewrote the account in one unit of recovery, and a
 * second Enter on the confirmed screen simply paid again. Here the account rewrite is a call to
 * the account service, so the payment is written with the caller's key: a resubmission of the same
 * key returns the first outcome instead of paying twice, and a failed account call leaves the row
 * marked as compensated.
 */
@Entity
@Table(name = "bill_payment")
public class BillPaymentEntity {

    /** The account rewrite succeeded and the transaction stands. */
    public static final String POSTED = "POSTED";
    /** The account rewrite failed, so the transaction written first was removed again. */
    public static final String COMPENSATED = "COMPENSATED";

    @Id
    @Column(name = "idempotency_key", length = 64, nullable = false)
    private String idempotencyKey;

    @Column(name = "acct_id", nullable = false)
    private Long acctId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_id", length = 16, nullable = false, columnDefinition = "char(16)")
    private String tranId;

    @Column(name = "amount", precision = 11, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    protected BillPaymentEntity() {
    }

    public BillPaymentEntity(String idempotencyKey, Long acctId, String tranId, BigDecimal amount,
            String status, Instant requestedAt) {
        this.idempotencyKey = idempotencyKey;
        this.acctId = acctId;
        this.tranId = tranId;
        this.amount = amount;
        this.status = status;
        this.requestedAt = requestedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getAcctId() {
        return acctId;
    }

    public String getTranId() {
        return tranId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }
}
