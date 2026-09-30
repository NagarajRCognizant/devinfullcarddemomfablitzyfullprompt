package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * TRANCATG VSAM KSDS record (app/cpy/CVTRA04Y.cpy, RECLN 60, KEYS(6 0)): the category description
 * CBTRN03C prints for each transaction.
 */
@Entity
@Table(name = "transaction_category")
public class TransactionCategoryEntity {

    @EmbeddedId
    private TransactionCategoryId id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_cat_type_desc", length = 50, nullable = false, columnDefinition = "char(50)")
    private String tranCatTypeDesc;

    protected TransactionCategoryEntity() {
    }

    public TransactionCategoryEntity(TransactionCategoryId id, String tranCatTypeDesc) {
        this.id = id;
        this.tranCatTypeDesc = tranCatTypeDesc;
    }

    public TransactionCategoryId getId() {
        return id;
    }

    public String getTranCatTypeDesc() {
        return tranCatTypeDesc;
    }
}
