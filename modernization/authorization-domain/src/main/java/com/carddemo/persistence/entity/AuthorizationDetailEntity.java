package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * IMS child segment PAUTDTL1 of database DBPAUTP0 (copybook cpy/CIPAUDTY.cpy, 200 bytes).
 *
 * <p>The hierarchical child becomes the child table of the parent/child model: the physical parent
 * pointer of IMS becomes a foreign key on the account id, and the GNP walk of COPAUS0C and
 * CBPAUP0C becomes a keyed range scan ordered by the authorization key. Because the key holds the
 * nines complement of the date and of the time, ascending order is newest first - the same order
 * DL/I returns children in - so no sort direction has to be reversed anywhere in the target.
 *
 * <p>The complemented key parts are kept as their own columns as well, since CBPAUP0C recomputes
 * the original Julian date from PA-AUTH-DATE-9C and COPAUS2C recomputes the time of day from
 * PA-AUTH-TIME-9C; neither is a formatting concern, both are business calculations.
 */
@Entity
@Table(name = "pending_auth_detail")
public class AuthorizationDetailEntity {

    @EmbeddedId
    private AuthorizationDetailId id;

    /** PA-AUTH-DATE-9C PIC S9(05) COMP-3: 99999 minus the Julian authorization date. */
    @Column(name = "auth_date_9c", nullable = false)
    private Integer authDate9c;

    /** PA-AUTH-TIME-9C PIC S9(09) COMP-3: 999999999 minus the time of day in milliseconds. */
    @Column(name = "auth_time_9c", nullable = false)
    private Long authTime9c;

    /** PA-AUTH-ORIG-DATE PIC X(06), YYMMDD as the acquirer sent it. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_orig_date", length = 6, columnDefinition = "char(6)")
    private String authOrigDate;

    /** PA-AUTH-ORIG-TIME PIC X(06), HHMMSS as the acquirer sent it. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_orig_time", length = 6, columnDefinition = "char(6)")
    private String authOrigTime;

    /** PA-CARD-NUM PIC X(16). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_num", length = 16, nullable = false, columnDefinition = "char(16)")
    private String cardNum;

    /** PA-AUTH-TYPE PIC X(04). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_type", length = 4, columnDefinition = "char(4)")
    private String authType;

    /** PA-CARD-EXPIRY-DATE PIC X(04), MMYY. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_expiry_date", length = 4, columnDefinition = "char(4)")
    private String cardExpiryDate;

    /** PA-MESSAGE-TYPE PIC X(06). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "message_type", length = 6, columnDefinition = "char(6)")
    private String messageType;

    /** PA-MESSAGE-SOURCE PIC X(06). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "message_source", length = 6, columnDefinition = "char(6)")
    private String messageSource;

    /** PA-AUTH-ID-CODE PIC X(06). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_id_code", length = 6, columnDefinition = "char(6)")
    private String authIdCode;

    /** PA-AUTH-RESP-CODE PIC X(02): '00' is the approved condition name PA-AUTH-APPROVED. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_resp_code", length = 2, columnDefinition = "char(2)")
    private String authRespCode;

    /** PA-AUTH-RESP-REASON PIC X(04). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_resp_reason", length = 4, columnDefinition = "char(4)")
    private String authRespReason;

    /** PA-PROCESSING-CODE PIC 9(06), carried as characters because it is a code, not a quantity. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "processing_code", length = 6, columnDefinition = "char(6)")
    private String processingCode;

    /** PA-TRANSACTION-AMT PIC S9(10)V99 COMP-3. */
    @Column(name = "transaction_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal transactionAmt = BigDecimal.ZERO;

    /** PA-APPROVED-AMT PIC S9(10)V99 COMP-3. */
    @Column(name = "approved_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal approvedAmt = BigDecimal.ZERO;

    /** PA-MERCHANT-CATAGORY-CODE PIC X(04). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_category_code", length = 4, columnDefinition = "char(4)")
    private String merchantCategoryCode;

    /** PA-ACQR-COUNTRY-CODE PIC X(03). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "acqr_country_code", length = 3, columnDefinition = "char(3)")
    private String acqrCountryCode;

    /** PA-POS-ENTRY-MODE PIC 9(02). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "pos_entry_mode", length = 2, columnDefinition = "char(2)")
    private String posEntryMode;

    /** PA-MERCHANT-ID PIC X(15). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_id", length = 15, columnDefinition = "char(15)")
    private String merchantId;

    /** PA-MERCHANT-NAME PIC X(22). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_name", length = 22, columnDefinition = "char(22)")
    private String merchantName;

    /** PA-MERCHANT-CITY PIC X(13). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_city", length = 13, columnDefinition = "char(13)")
    private String merchantCity;

    /** PA-MERCHANT-STATE PIC X(02). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_state", length = 2, columnDefinition = "char(2)")
    private String merchantState;

    /** PA-MERCHANT-ZIP PIC X(09). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_zip", length = 9, columnDefinition = "char(9)")
    private String merchantZip;

    /** PA-TRANSACTION-ID PIC X(15). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "transaction_id", length = 15, columnDefinition = "char(15)")
    private String transactionId;

    /** PA-MATCH-STATUS PIC X(01): P pending, D declined, E pending expired, M matched. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "match_status", length = 1, columnDefinition = "char(1)")
    private String matchStatus;

    /** PA-AUTH-FRAUD PIC X(01): F confirmed, R removed, space never reported. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_fraud", length = 1, columnDefinition = "char(1)")
    private String authFraud;

    /** PA-FRAUD-RPT-DATE PIC X(08), the MM/DD/YY CICS FORMATTIME value COPAUS2C writes. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "fraud_rpt_date", length = 8, columnDefinition = "char(8)")
    private String fraudRptDate;

    public AuthorizationDetailId getId() {
        return id;
    }

    public void setId(AuthorizationDetailId id) {
        this.id = id;
    }

    public Integer getAuthDate9c() {
        return authDate9c;
    }

    public void setAuthDate9c(Integer authDate9c) {
        this.authDate9c = authDate9c;
    }

    public Long getAuthTime9c() {
        return authTime9c;
    }

    public void setAuthTime9c(Long authTime9c) {
        this.authTime9c = authTime9c;
    }

    public String getAuthOrigDate() {
        return authOrigDate;
    }

    public void setAuthOrigDate(String authOrigDate) {
        this.authOrigDate = authOrigDate;
    }

    public String getAuthOrigTime() {
        return authOrigTime;
    }

    public void setAuthOrigTime(String authOrigTime) {
        this.authOrigTime = authOrigTime;
    }

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
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

    public String getMerchantCategoryCode() {
        return merchantCategoryCode;
    }

    public void setMerchantCategoryCode(String merchantCategoryCode) {
        this.merchantCategoryCode = merchantCategoryCode;
    }

    public String getAcqrCountryCode() {
        return acqrCountryCode;
    }

    public void setAcqrCountryCode(String acqrCountryCode) {
        this.acqrCountryCode = acqrCountryCode;
    }

    public String getPosEntryMode() {
        return posEntryMode;
    }

    public void setPosEntryMode(String posEntryMode) {
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

    public String getFraudRptDate() {
        return fraudRptDate;
    }

    public void setFraudRptDate(String fraudRptDate) {
        this.fraudRptDate = fraudRptDate;
    }
}
