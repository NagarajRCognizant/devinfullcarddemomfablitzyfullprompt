package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;

/** ACCTDAT VSAM KSDS record (app/cpy/CVACT01Y.cpy, RECLN 300, KEYS(11 0)). */
@Entity
@Table(name = "account")
public class AccountEntity {

    @Id
    @Column(name = "acct_id", nullable = false)
    private Long acctId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "active_status", length = 1, columnDefinition = "char(1)")
    private String activeStatus;

    @Column(name = "curr_bal", precision = 12, scale = 2)
    private BigDecimal currBal;

    @Column(name = "credit_limit", precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "cash_credit_limit", precision = 12, scale = 2)
    private BigDecimal cashCreditLimit;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "open_date", length = 10, columnDefinition = "char(10)")
    private String openDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "expiration_date", length = 10, columnDefinition = "char(10)")
    private String expirationDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "reissue_date", length = 10, columnDefinition = "char(10)")
    private String reissueDate;

    @Column(name = "curr_cyc_credit", precision = 12, scale = 2)
    private BigDecimal currCycCredit;

    @Column(name = "curr_cyc_debit", precision = 12, scale = 2)
    private BigDecimal currCycDebit;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "addr_zip", length = 10, columnDefinition = "char(10)")
    private String addrZip;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "group_id", length = 10, columnDefinition = "char(10)")
    private String groupId;

    public Long getAcctId() {
        return acctId;
    }

    public void setAcctId(Long acctId) {
        this.acctId = acctId;
    }

    public String getActiveStatus() {
        return activeStatus;
    }

    public void setActiveStatus(String activeStatus) {
        this.activeStatus = activeStatus;
    }

    public BigDecimal getCurrBal() {
        return currBal;
    }

    public void setCurrBal(BigDecimal currBal) {
        this.currBal = currBal;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public BigDecimal getCashCreditLimit() {
        return cashCreditLimit;
    }

    public void setCashCreditLimit(BigDecimal cashCreditLimit) {
        this.cashCreditLimit = cashCreditLimit;
    }

    public String getOpenDate() {
        return openDate;
    }

    public void setOpenDate(String openDate) {
        this.openDate = openDate;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    public String getReissueDate() {
        return reissueDate;
    }

    public void setReissueDate(String reissueDate) {
        this.reissueDate = reissueDate;
    }

    public BigDecimal getCurrCycCredit() {
        return currCycCredit;
    }

    public void setCurrCycCredit(BigDecimal currCycCredit) {
        this.currCycCredit = currCycCredit;
    }

    public BigDecimal getCurrCycDebit() {
        return currCycDebit;
    }

    public void setCurrCycDebit(BigDecimal currCycDebit) {
        this.currCycDebit = currCycDebit;
    }

    public String getAddrZip() {
        return addrZip;
    }

    public void setAddrZip(String addrZip) {
        this.addrZip = addrZip;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
}
