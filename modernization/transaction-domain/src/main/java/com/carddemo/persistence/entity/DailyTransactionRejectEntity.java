package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * DALYREJS record of CBTRN02C: the 350 byte daily transaction followed by the 80 byte validation
 * trailer of paragraph 2500-WRITE-REJECT-REC (a four digit reason code and its description).
 *
 * <p>The sequential reject file has no key, so the surrogate identity here only preserves write
 * order; the rejected transaction identifier is kept as a column because a transaction can be
 * rejected on more than one run.
 */
@Entity
@Table(name = "daily_transaction_reject")
public class DailyTransactionRejectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reject_seq")
    private Long rejectSeq;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "dalytran_id", length = 16, nullable = false, columnDefinition = "char(16)")
    private String dalytranId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "reject_tran_data", length = 350, nullable = false,
            columnDefinition = "char(350)")
    private String rejectTranData;

    @Column(name = "validation_fail_reason", nullable = false)
    private Integer validationFailReason;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "validation_fail_reason_desc", length = 76, nullable = false,
            columnDefinition = "char(76)")
    private String validationFailReasonDesc;

    @Column(name = "rejected_at", nullable = false)
    private Instant rejectedAt;

    protected DailyTransactionRejectEntity() {
    }

    public DailyTransactionRejectEntity(String dalytranId, String rejectTranData, int validationFailReason,
            String validationFailReasonDesc, Instant rejectedAt) {
        this.dalytranId = dalytranId;
        this.rejectTranData = rejectTranData;
        this.validationFailReason = validationFailReason;
        this.validationFailReasonDesc = validationFailReasonDesc;
        this.rejectedAt = rejectedAt;
    }

    public Long getRejectSeq() {
        return rejectSeq;
    }

    public String getDalytranId() {
        return dalytranId;
    }

    public String getRejectTranData() {
        return rejectTranData;
    }

    public Integer getValidationFailReason() {
        return validationFailReason;
    }

    public String getValidationFailReasonDesc() {
        return validationFailReasonDesc;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }
}
