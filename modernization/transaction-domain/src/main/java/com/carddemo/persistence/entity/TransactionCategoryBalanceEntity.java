package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * TCATBAL VSAM KSDS record (app/cpy/CVTRA01Y.cpy, RECLN 50, KEYS(17 0)).
 *
 * <p>CBTRN02C adds every posted amount to the balance of the account/type/category triple and
 * creates the row when the key is absent, which is the {@code INVALID KEY} branch of paragraph
 * 2700-UPDATE-TCATBAL; CBACT04C then reads the same rows to compute interest.
 */
@Entity
@Table(name = "transaction_category_balance")
public class TransactionCategoryBalanceEntity {

    @EmbeddedId
    private TransactionCategoryBalanceId id;

    @Column(name = "tran_cat_bal", precision = 11, scale = 2, nullable = false)
    private BigDecimal tranCatBal;

    protected TransactionCategoryBalanceEntity() {
    }

    public TransactionCategoryBalanceEntity(TransactionCategoryBalanceId id, BigDecimal tranCatBal) {
        this.id = id;
        this.tranCatBal = tranCatBal;
    }

    public TransactionCategoryBalanceId getId() {
        return id;
    }

    public BigDecimal getTranCatBal() {
        return tranCatBal;
    }

    public void setTranCatBal(BigDecimal tranCatBal) {
        this.tranCatBal = tranCatBal;
    }

    /** Paragraphs 2700-A/2700-B of CBTRN02C: the posted amount is added to the running balance. */
    public void add(BigDecimal amount) {
        this.tranCatBal = this.tranCatBal.add(amount);
    }
}
