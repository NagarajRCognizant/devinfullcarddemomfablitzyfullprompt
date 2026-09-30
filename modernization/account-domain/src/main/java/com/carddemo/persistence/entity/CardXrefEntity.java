package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * CARDXREF VSAM KSDS record (app/cpy/CVACT03Y.cpy, RECLN 50, KEYS(16 0)).
 *
 * <p>The online programs read the file through its account alternate index
 * (CXACAIX, KEYS(11 25)); the modernized equivalent is the {@code ix_card_xref_acct_id}
 * index declared in the Flyway migration.
 */
@Entity
@Table(name = "card_xref")
public class CardXrefEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "xref_card_num", length = 16, nullable = false, columnDefinition = "char(16)")
    private String xrefCardNum;

    @Column(name = "xref_cust_id", nullable = false)
    private Long xrefCustId;

    @Column(name = "xref_acct_id", nullable = false)
    private Long xrefAcctId;

    public String getXrefCardNum() {
        return xrefCardNum;
    }

    public void setXrefCardNum(String xrefCardNum) {
        this.xrefCardNum = xrefCardNum;
    }

    public Long getXrefCustId() {
        return xrefCustId;
    }

    public void setXrefCustId(Long xrefCustId) {
        this.xrefCustId = xrefCustId;
    }

    public Long getXrefAcctId() {
        return xrefAcctId;
    }

    public void setXrefAcctId(Long xrefAcctId) {
        this.xrefAcctId = xrefAcctId;
    }
}
