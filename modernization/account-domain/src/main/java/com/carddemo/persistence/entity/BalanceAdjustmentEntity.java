package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * The record of one applied balance change, which the source did not need.
 *
 * <p>On the mainframe the account rewrite and the transaction write of COBIL00C, CBTRN02C and
 * CBACT04C were one unit of recovery. Here the transaction context owns TRANSACT and this context
 * owns ACCTDAT, so the balance change arrives as a call that can be retried. The key of the caller
 * is stored with the outcome, which makes the change idempotent: a repeat of the same key returns
 * the first result instead of moving the balance twice.
 */
@Entity
@Table(name = "balance_adjustment")
public class BalanceAdjustmentEntity {

    @Id
    @Column(name = "idempotency_key", length = 64, nullable = false)
    private String idempotencyKey;

    @Column(name = "acct_id", nullable = false)
    private Long acctId;

    @Column(name = "adjustment_kind", length = 24, nullable = false)
    private String adjustmentKind;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "resulting_balance", precision = 12, scale = 2, nullable = false)
    private BigDecimal resultingBalance;

    @Column(name = "resulting_cyc_credit", precision = 12, scale = 2, nullable = false)
    private BigDecimal resultingCycCredit;

    @Column(name = "resulting_cyc_debit", precision = 12, scale = 2, nullable = false)
    private BigDecimal resultingCycDebit;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    protected BalanceAdjustmentEntity() {
    }

    public BalanceAdjustmentEntity(String idempotencyKey, Long acctId, String adjustmentKind, BigDecimal amount,
            BigDecimal resultingBalance, BigDecimal resultingCycCredit, BigDecimal resultingCycDebit,
            Instant appliedAt) {
        this.idempotencyKey = idempotencyKey;
        this.acctId = acctId;
        this.adjustmentKind = adjustmentKind;
        this.amount = amount;
        this.resultingBalance = resultingBalance;
        this.resultingCycCredit = resultingCycCredit;
        this.resultingCycDebit = resultingCycDebit;
        this.appliedAt = appliedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getAcctId() {
        return acctId;
    }

    public String getAdjustmentKind() {
        return adjustmentKind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getResultingBalance() {
        return resultingBalance;
    }

    public BigDecimal getResultingCycCredit() {
        return resultingCycCredit;
    }

    public BigDecimal getResultingCycDebit() {
        return resultingCycDebit;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }
}
