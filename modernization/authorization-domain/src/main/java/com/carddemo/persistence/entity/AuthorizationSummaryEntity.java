package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * IMS HIDAM root segment PAUTSUM0 of database DBPAUTP0 (copybook cpy/CIPAUSMY.cpy, 100 bytes).
 *
 * <p>The hierarchical root becomes the parent table of a parent/child relational model keyed by the
 * account id that the IMS root key ACCNTID carries, so the single-segment GU by account id of
 * COPAUA0C and COPAUS0C becomes a primary key read. The packed decimal balances (S9(9)V99 COMP-3)
 * map to {@code numeric(11,2)} and the binary counters (S9(4) COMP) to {@code smallint}; the
 * five-occurrence PA-ACCOUNT-STATUS array is carried as the fixed ten character string it occupies
 * in the segment, because no program indexes into it.
 *
 * <p>IMS gives the root segment an implicit lock for the duration of the unit of work. The target
 * uses an optimistic version column instead: two authorizations for the same account update the
 * counters and balances of this row, and the loser of the race is retried rather than silently
 * overwriting the other's totals (see docs/12-consistency-model-delta-register.md, CMD-11).
 */
@Entity
@Table(name = "pending_auth_summary")
public class AuthorizationSummaryEntity {

    /** PA-ACCT-ID PIC S9(11) COMP-3, the IMS root key ACCNTID. */
    @Id
    @Column(name = "acct_id", nullable = false)
    private Long acctId;

    /** PA-CUST-ID PIC 9(09). */
    @Column(name = "cust_id", nullable = false)
    private Long custId;

    /** PA-AUTH-STATUS PIC X(01). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_status", length = 1, columnDefinition = "char(1)")
    private String authStatus;

    /** PA-ACCOUNT-STATUS PIC X(02) OCCURS 5 TIMES, carried as the ten characters it occupies. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "account_status", length = 10, columnDefinition = "char(10)")
    private String accountStatus;

    /** PA-CREDIT-LIMIT PIC S9(09)V99 COMP-3. */
    @Column(name = "credit_limit", nullable = false, precision = 11, scale = 2)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    /** PA-CASH-LIMIT PIC S9(09)V99 COMP-3. */
    @Column(name = "cash_limit", nullable = false, precision = 11, scale = 2)
    private BigDecimal cashLimit = BigDecimal.ZERO;

    /** PA-CREDIT-BALANCE PIC S9(09)V99 COMP-3. */
    @Column(name = "credit_balance", nullable = false, precision = 11, scale = 2)
    private BigDecimal creditBalance = BigDecimal.ZERO;

    /** PA-CASH-BALANCE PIC S9(09)V99 COMP-3. */
    @Column(name = "cash_balance", nullable = false, precision = 11, scale = 2)
    private BigDecimal cashBalance = BigDecimal.ZERO;

    /** PA-APPROVED-AUTH-CNT PIC S9(04) COMP. */
    @Column(name = "approved_auth_cnt", nullable = false)
    private Short approvedAuthCnt = 0;

    /** PA-DECLINED-AUTH-CNT PIC S9(04) COMP. */
    @Column(name = "declined_auth_cnt", nullable = false)
    private Short declinedAuthCnt = 0;

    /** PA-APPROVED-AUTH-AMT PIC S9(09)V99 COMP-3. */
    @Column(name = "approved_auth_amt", nullable = false, precision = 11, scale = 2)
    private BigDecimal approvedAuthAmt = BigDecimal.ZERO;

    /** PA-DECLINED-AUTH-AMT PIC S9(09)V99 COMP-3. */
    @Column(name = "declined_auth_amt", nullable = false, precision = 11, scale = 2)
    private BigDecimal declinedAuthAmt = BigDecimal.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Long getAcctId() {
        return acctId;
    }

    public void setAcctId(Long acctId) {
        this.acctId = acctId;
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
    }

    public String getAuthStatus() {
        return authStatus;
    }

    public void setAuthStatus(String authStatus) {
        this.authStatus = authStatus;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public BigDecimal getCashLimit() {
        return cashLimit;
    }

    public void setCashLimit(BigDecimal cashLimit) {
        this.cashLimit = cashLimit;
    }

    public BigDecimal getCreditBalance() {
        return creditBalance;
    }

    public void setCreditBalance(BigDecimal creditBalance) {
        this.creditBalance = creditBalance;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }

    public Short getApprovedAuthCnt() {
        return approvedAuthCnt;
    }

    public void setApprovedAuthCnt(Short approvedAuthCnt) {
        this.approvedAuthCnt = approvedAuthCnt;
    }

    public Short getDeclinedAuthCnt() {
        return declinedAuthCnt;
    }

    public void setDeclinedAuthCnt(Short declinedAuthCnt) {
        this.declinedAuthCnt = declinedAuthCnt;
    }

    public BigDecimal getApprovedAuthAmt() {
        return approvedAuthAmt;
    }

    public void setApprovedAuthAmt(BigDecimal approvedAuthAmt) {
        this.approvedAuthAmt = approvedAuthAmt;
    }

    public BigDecimal getDeclinedAuthAmt() {
        return declinedAuthAmt;
    }

    public void setDeclinedAuthAmt(BigDecimal declinedAuthAmt) {
        this.declinedAuthAmt = declinedAuthAmt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
