package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * DB2 table CARDDEMO.AUTHFRDS (ddl/AUTHFRDS.ddl, DCLGEN dcl/AUTHFRDS.dcl) written by COPAUS2C.
 *
 * <p>The fraud analytics store stays a relational table, so the mapping is one to one: DB2
 * {@code DECIMAL(12,2)} becomes {@code numeric(12,2)}, {@code TIMESTAMP} becomes
 * {@code timestamp(6)}, {@code DATE} becomes {@code date}, and {@code SMALLINT POS_ENTRY_MODE}
 * stays a small integer even though the IMS segment carries the same value as two characters -
 * that widening is in the source, not introduced here.
 *
 * <p>In the source this table lives in DB2 while the authorization detail lives in IMS, and
 * COPAUS1C relies on the CICS syncpoint to commit both or neither. Both now live in the same
 * PostgreSQL database owned by this service, so the fraud marking is a single local transaction;
 * that is recorded as CMD-13 in the consistency-model delta register.
 */
@Entity
@Table(name = "auth_fraud")
public class FraudReportEntity {

    @EmbeddedId
    private FraudReportId id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_type", length = 4, columnDefinition = "char(4)")
    private String authType;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_expiry_date", length = 4, columnDefinition = "char(4)")
    private String cardExpiryDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "message_type", length = 6, columnDefinition = "char(6)")
    private String messageType;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "message_source", length = 6, columnDefinition = "char(6)")
    private String messageSource;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_id_code", length = 6, columnDefinition = "char(6)")
    private String authIdCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_resp_code", length = 2, columnDefinition = "char(2)")
    private String authRespCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_resp_reason", length = 4, columnDefinition = "char(4)")
    private String authRespReason;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "processing_code", length = 6, columnDefinition = "char(6)")
    private String processingCode;

    @Column(name = "transaction_amt", precision = 12, scale = 2)
    private BigDecimal transactionAmt;

    @Column(name = "approved_amt", precision = 12, scale = 2)
    private BigDecimal approvedAmt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_catagory_code", length = 4, columnDefinition = "char(4)")
    private String merchantCatagoryCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "acqr_country_code", length = 3, columnDefinition = "char(3)")
    private String acqrCountryCode;

    /** DB2 SMALLINT; COPAUS2C moves the two character PA-POS-ENTRY-MODE into it. */
    @Column(name = "pos_entry_mode")
    private Short posEntryMode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_id", length = 15, columnDefinition = "char(15)")
    private String merchantId;

    /** DB2 VARCHAR(22); COPAUS2C sets MERCHANT-NAME-LEN to the full field length. */
    @Column(name = "merchant_name", length = 22)
    private String merchantName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_city", length = 13, columnDefinition = "char(13)")
    private String merchantCity;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_state", length = 2, columnDefinition = "char(2)")
    private String merchantState;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_zip", length = 9, columnDefinition = "char(9)")
    private String merchantZip;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "transaction_id", length = 15, columnDefinition = "char(15)")
    private String transactionId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "match_status", length = 1, columnDefinition = "char(1)")
    private String matchStatus;

    /** F when the authorization is reported as fraud, R when the report is removed. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_fraud", length = 1, columnDefinition = "char(1)")
    private String authFraud;

    /** {@code CURRENT DATE} on both the insert and the update path. */
    @Column(name = "fraud_rpt_date")
    private LocalDate fraudRptDate;

    @Column(name = "acct_id", precision = 11, scale = 0)
    private BigDecimal acctId;

    @Column(name = "cust_id", precision = 9, scale = 0)
    private BigDecimal custId;

    public FraudReportId getId() {
        return id;
    }

    public void setId(FraudReportId id) {
        this.id = id;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getCardExpiryDate() {
        return cardExpiryDate;
    }

    public void setCardExpiryDate(String cardExpiryDate) {
        this.cardExpiryDate = cardExpiryDate;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getMessageSource() {
        return messageSource;
    }

    public void setMessageSource(String messageSource) {
        this.messageSource = messageSource;
    }

    public String getAuthIdCode() {
        return authIdCode;
    }

    public void setAuthIdCode(String authIdCode) {
        this.authIdCode = authIdCode;
    }

    public String getAuthRespCode() {
        return authRespCode;
    }

    public void setAuthRespCode(String authRespCode) {
        this.authRespCode = authRespCode;
    }

    public String getAuthRespReason() {
        return authRespReason;
    }

    public void setAuthRespReason(String authRespReason) {
        this.authRespReason = authRespReason;
    }

    public String getProcessingCode() {
        return processingCode;
    }

    public void setProcessingCode(String processingCode) {
        this.processingCode = processingCode;
    }

    public BigDecimal getTransactionAmt() {
        return transactionAmt;
    }

    public void setTransactionAmt(BigDecimal transactionAmt) {
        this.transactionAmt = transactionAmt;
    }

    public BigDecimal getApprovedAmt() {
        return approvedAmt;
    }

    public void setApprovedAmt(BigDecimal approvedAmt) {
        this.approvedAmt = approvedAmt;
    }

    public String getMerchantCatagoryCode() {
        return merchantCatagoryCode;
    }

    public void setMerchantCatagoryCode(String merchantCatagoryCode) {
        this.merchantCatagoryCode = merchantCatagoryCode;
    }

    public String getAcqrCountryCode() {
        return acqrCountryCode;
    }

    public void setAcqrCountryCode(String acqrCountryCode) {
        this.acqrCountryCode = acqrCountryCode;
    }

    public Short getPosEntryMode() {
        return posEntryMode;
    }

    public void setPosEntryMode(Short posEntryMode) {
        this.posEntryMode = posEntryMode;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getMerchantCity() {
        return merchantCity;
    }

    public void setMerchantCity(String merchantCity) {
        this.merchantCity = merchantCity;
    }

    public String getMerchantState() {
        return merchantState;
    }

    public void setMerchantState(String merchantState) {
        this.merchantState = merchantState;
    }

    public String getMerchantZip() {
        return merchantZip;
    }

    public void setMerchantZip(String merchantZip) {
        this.merchantZip = merchantZip;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(String matchStatus) {
        this.matchStatus = matchStatus;
    }

    public String getAuthFraud() {
        return authFraud;
    }

    public void setAuthFraud(String authFraud) {
        this.authFraud = authFraud;
    }

    public LocalDate getFraudRptDate() {
        return fraudRptDate;
    }

    public void setFraudRptDate(LocalDate fraudRptDate) {
        this.fraudRptDate = fraudRptDate;
    }

    public BigDecimal getAcctId() {
        return acctId;
    }

    public void setAcctId(BigDecimal acctId) {
        this.acctId = acctId;
    }

    public BigDecimal getCustId() {
        return custId;
    }

    public void setCustId(BigDecimal custId) {
        this.custId = custId;
    }
}
