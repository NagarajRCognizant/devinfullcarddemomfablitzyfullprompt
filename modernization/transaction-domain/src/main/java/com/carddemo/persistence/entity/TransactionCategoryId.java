package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** TRAN-CAT-KEY of the TRANCATG record (app/cpy/CVTRA04Y.cpy): type code plus category code. */
@Embeddable
public class TransactionCategoryId implements Serializable {

    private static final long serialVersionUID = 1L;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_type_cd", length = 2, nullable = false, columnDefinition = "char(2)")
    private String tranTypeCd;

    @Column(name = "tran_cat_cd", nullable = false)
    private Integer tranCatCd;

    protected TransactionCategoryId() {
    }

    public TransactionCategoryId(String tranTypeCd, Integer tranCatCd) {
        this.tranTypeCd = tranTypeCd;
        this.tranCatCd = tranCatCd;
    }

    public String getTranTypeCd() {
        return tranTypeCd;
    }

    public Integer getTranCatCd() {
        return tranCatCd;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransactionCategoryId that)) {
            return false;
        }
        return Objects.equals(tranTypeCd, that.tranTypeCd) && Objects.equals(tranCatCd, that.tranCatCd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tranTypeCd, tranCatCd);
    }
}
