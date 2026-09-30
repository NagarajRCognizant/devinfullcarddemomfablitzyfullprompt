package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** TRAN-CAT-KEY of the TCATBAL record (app/cpy/CVTRA01Y.cpy): account, type code and category. */
@Embeddable
public class TransactionCategoryBalanceId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "trancat_acct_id", nullable = false)
    private Long trancatAcctId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "trancat_type_cd", length = 2, nullable = false, columnDefinition = "char(2)")
    private String trancatTypeCd;

    @Column(name = "trancat_cd", nullable = false)
    private Integer trancatCd;

    protected TransactionCategoryBalanceId() {
    }

    public TransactionCategoryBalanceId(Long trancatAcctId, String trancatTypeCd, Integer trancatCd) {
        this.trancatAcctId = trancatAcctId;
        this.trancatTypeCd = trancatTypeCd;
        this.trancatCd = trancatCd;
    }

    public Long getTrancatAcctId() {
        return trancatAcctId;
    }

    public String getTrancatTypeCd() {
        return trancatTypeCd;
    }

    public Integer getTrancatCd() {
        return trancatCd;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransactionCategoryBalanceId that)) {
            return false;
        }
        return Objects.equals(trancatAcctId, that.trancatAcctId)
                && Objects.equals(trancatTypeCd, that.trancatTypeCd)
                && Objects.equals(trancatCd, that.trancatCd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trancatAcctId, trancatTypeCd, trancatCd);
    }
}
