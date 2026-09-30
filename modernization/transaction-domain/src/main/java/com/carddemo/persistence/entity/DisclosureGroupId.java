package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** DIS-GROUP-KEY of the DISCGRP record (app/cpy/CVTRA02Y.cpy): group, type code and category. */
@Embeddable
public class DisclosureGroupId implements Serializable {

    private static final long serialVersionUID = 1L;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "dis_acct_group_id", length = 10, nullable = false, columnDefinition = "char(10)")
    private String disAcctGroupId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "dis_tran_type_cd", length = 2, nullable = false, columnDefinition = "char(2)")
    private String disTranTypeCd;

    @Column(name = "dis_tran_cat_cd", nullable = false)
    private Integer disTranCatCd;

    protected DisclosureGroupId() {
    }

    public DisclosureGroupId(String disAcctGroupId, String disTranTypeCd, Integer disTranCatCd) {
        this.disAcctGroupId = disAcctGroupId;
        this.disTranTypeCd = disTranTypeCd;
        this.disTranCatCd = disTranCatCd;
    }

    public String getDisAcctGroupId() {
        return disAcctGroupId;
    }

    public String getDisTranTypeCd() {
        return disTranTypeCd;
    }

    public Integer getDisTranCatCd() {
        return disTranCatCd;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DisclosureGroupId that)) {
            return false;
        }
        return Objects.equals(disAcctGroupId, that.disAcctGroupId)
                && Objects.equals(disTranTypeCd, that.disTranTypeCd)
                && Objects.equals(disTranCatCd, that.disTranCatCd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(disAcctGroupId, disTranTypeCd, disTranCatCd);
    }
}
