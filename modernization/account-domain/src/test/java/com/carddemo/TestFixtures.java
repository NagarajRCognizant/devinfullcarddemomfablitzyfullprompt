package com.carddemo;

import com.carddemo.persistence.entity.AccountEntity;
import com.carddemo.persistence.entity.CardXrefEntity;
import com.carddemo.persistence.entity.CustomerEntity;
import java.math.BigDecimal;

/** One account, its customer and its cross reference record, with values that pass every edit. */
public final class TestFixtures {

    public static final long ACCOUNT_ID = 11111111111L;
    public static final long CUSTOMER_ID = 100000001L;
    public static final String CARD_NUMBER = "4111111111111111";

    private TestFixtures() {
    }

    public static AccountEntity account() {
        AccountEntity account = new AccountEntity();
        account.setAcctId(ACCOUNT_ID);
        account.setActiveStatus("Y");
        account.setCurrBal(new BigDecimal("1025.50"));
        account.setCreditLimit(new BigDecimal("5000.00"));
        account.setCashCreditLimit(new BigDecimal("1000.00"));
        account.setOpenDate("2015-03-01");
        account.setExpirationDate("2026-03-01");
        account.setReissueDate("2022-03-01");
        account.setCurrCycCredit(new BigDecimal("120.00"));
        account.setCurrCycDebit(new BigDecimal("240.00"));
        account.setAddrZip("10001");
        account.setGroupId("GRP0000001");
        return account;
    }

    public static CustomerEntity customer() {
        CustomerEntity customer = new CustomerEntity();
        customer.setCustId(CUSTOMER_ID);
        customer.setFirstName("John");
        customer.setMiddleName("Q");
        customer.setLastName("Public");
        customer.setAddrLine1("1 Main Street");
        customer.setAddrLine2("Apt 2");
        customer.setAddrLine3("New York");
        customer.setAddrStateCd("NY");
        customer.setAddrCountryCd("USA");
        customer.setAddrZip("10001");
        customer.setPhoneNum1("(212)555-1234");
        customer.setPhoneNum2("(646)555-9876");
        customer.setSsn("123456789");
        customer.setGovtIssuedId("NY-DL-12345");
        customer.setDobYyyyMmDd("1980-07-04");
        customer.setEftAccountId("1234567890");
        customer.setPriCardHolderInd("Y");
        customer.setFicoCreditScore(720);
        return customer;
    }

    public static CardXrefEntity xref() {
        CardXrefEntity xref = new CardXrefEntity();
        xref.setXrefCardNum(CARD_NUMBER);
        xref.setXrefCustId(CUSTOMER_ID);
        xref.setXrefAcctId(ACCOUNT_ID);
        return xref;
    }
}
