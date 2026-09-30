package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * TRANSACT VSAM KSDS record (app/cpy/CVTRA05Y.cpy, RECLN 350, KEYS(16 0)).
 *
 * <p>TRAN-ID is {@code PIC X(16)}, so it stays a fixed length character key here: COTRN02C and
 * COBIL00C derive the next identifier by reading the last key of the file and adding one, which
 * only reproduces the source ordering if the padding of the stored value is preserved.
 *
 * <p>The two timestamps are the 26 character text the programs store, read and display unchanged;
 * CBTRN02C writes them in DB2 format and COTRN02C accepts a date, so no column type narrower than
 * the source text would hold every value the file can carry.
 */
@Entity
@Table(name = "transaction")
public class TransactionEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_id", length = 16, nullable = false, columnDefinition = "char(16)")
    private String tranId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_type_cd", length = 2, columnDefinition = "char(2)")
    private String tranTypeCd;

    @Column(name = "tran_cat_cd")
    private Integer tranCatCd;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_source", length = 10, columnDefinition = "char(10)")
    private String tranSource;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_desc", length = 100, columnDefinition = "char(100)")
    private String tranDesc;

    @Column(name = "tran_amt", precision = 11, scale = 2)
    private BigDecimal tranAmt;

    @Column(name = "tran_merchant_id")
    private Long tranMerchantId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_merchant_name", length = 50, columnDefinition = "char(50)")
    private String tranMerchantName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_merchant_city", length = 50, columnDefinition = "char(50)")
    private String tranMerchantCity;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_merchant_zip", length = 10, columnDefinition = "char(10)")
    private String tranMerchantZip;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_card_num", length = 16, columnDefinition = "char(16)")
    private String tranCardNum;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_orig_ts", length = 26, columnDefinition = "char(26)")
    private String tranOrigTs;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tran_proc_ts", length = 26, columnDefinition = "char(26)")
    private String tranProcTs;

    public String getTranId() {
        return tranId;
    }

    public void setTranId(String tranId) {
        this.tranId = tranId;
    }

    public String getTranTypeCd() {
        return tranTypeCd;
    }

    public void setTranTypeCd(String tranTypeCd) {
        this.tranTypeCd = tranTypeCd;
    }

    public Integer getTranCatCd() {
        return tranCatCd;
    }

    public void setTranCatCd(Integer tranCatCd) {
        this.tranCatCd = tranCatCd;
    }

    public String getTranSource() {
        return tranSource;
    }

    public void setTranSource(String tranSource) {
        this.tranSource = tranSource;
    }

    public String getTranDesc() {
        return tranDesc;
    }

    public void setTranDesc(String tranDesc) {
        this.tranDesc = tranDesc;
    }

    public BigDecimal getTranAmt() {
        return tranAmt;
    }

    public void setTranAmt(BigDecimal tranAmt) {
        this.tranAmt = tranAmt;
    }

    public Long getTranMerchantId() {
        return tranMerchantId;
    }

    public void setTranMerchantId(Long tranMerchantId) {
        this.tranMerchantId = tranMerchantId;
    }

    public String getTranMerchantName() {
        return tranMerchantName;
    }

    public void setTranMerchantName(String tranMerchantName) {
        this.tranMerchantName = tranMerchantName;
    }

    public String getTranMerchantCity() {
        return tranMerchantCity;
    }

    public void setTranMerchantCity(String tranMerchantCity) {
        this.tranMerchantCity = tranMerchantCity;
    }

    public String getTranMerchantZip() {
        return tranMerchantZip;
    }

    public void setTranMerchantZip(String tranMerchantZip) {
        this.tranMerchantZip = tranMerchantZip;
    }

    public String getTranCardNum() {
        return tranCardNum;
    }

    public void setTranCardNum(String tranCardNum) {
        this.tranCardNum = tranCardNum;
    }

    public String getTranOrigTs() {
        return tranOrigTs;
    }

    public void setTranOrigTs(String tranOrigTs) {
        this.tranOrigTs = tranOrigTs;
    }

    public String getTranProcTs() {
        return tranProcTs;
    }

    public void setTranProcTs(String tranProcTs) {
        this.tranProcTs = tranProcTs;
    }
}
