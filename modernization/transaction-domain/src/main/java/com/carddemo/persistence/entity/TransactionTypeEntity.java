package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * TRANTYPE VSAM KSDS record (app/cpy/CVTRA03Y.cpy, RECLN 60, KEYS(2 0)): the description CBTRN03C
 * prints next to every transaction of that type.
 */
@Entity
@Table(name = "transaction_type")
public class TransactionTypeEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_type", length = 2, nullable = false, columnDefinition = "char(2)")
    private String tranType;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_type_desc", length = 50, nullable = false, columnDefinition = "char(50)")
    private String tranTypeDesc;

    protected TransactionTypeEntity() {
    }

    public TransactionTypeEntity(String tranType, String tranTypeDesc) {
        this.tranType = tranType;
        this.tranTypeDesc = tranTypeDesc;
    }

    public String getTranType() {
        return tranType;
    }

    public String getTranTypeDesc() {
        return tranTypeDesc;
    }
}
