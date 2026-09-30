package com.carddemo.service;

import com.carddemo.api.dto.AccountDetails;
import com.carddemo.api.dto.AccountUpdateForm;
import com.carddemo.api.dto.MoneyValue;
import com.carddemo.cobol.CobolNumeric;
import com.carddemo.cobol.CobolText;
import com.carddemo.persistence.entity.AccountEntity;
import com.carddemo.persistence.entity.CustomerEntity;
import org.springframework.stereotype.Component;

/**
 * Turns the stored account and customer records into the two screen shapes.
 *
 * <p>{@link #toDetails} is paragraph 1400-SEND-SCREEN of COACTVWC (through 1300-SETUP-SCREEN-VARS)
 * and {@link #toForm} is paragraph 9500-STORE-FETCHED-DATA of COACTUPC: the record fields are
 * split into the individual map fields, i.e. the stored {@code YYYY-MM-DD} dates into year, month
 * and day, the nine SSN digits into 3/2/4 and the {@code (AAA)BBB-CCCC} phone numbers into area
 * code, prefix and line number.
 */
@Component
public class AccountScreenMapper {

    /** COACTVWC screen output. */
    public AccountDetails toDetails(AccountEntity account, CustomerEntity customer, String cardNumber) {
        String ssn = CobolText.padRight(CobolText.trim(customer.getSsn()), 9);
        return new AccountDetails(
                formatAccountId(account.getAcctId()),
                CobolText.trim(account.getActiveStatus()),
                MoneyValue.of(account.getCurrBal()),
                MoneyValue.of(account.getCreditLimit()),
                MoneyValue.of(account.getCashCreditLimit()),
                MoneyValue.of(account.getCurrCycCredit()),
                MoneyValue.of(account.getCurrCycDebit()),
                CobolText.trim(account.getOpenDate()),
                CobolText.trim(account.getExpirationDate()),
                CobolText.trim(account.getReissueDate()),
                CobolText.trim(account.getGroupId()),
                formatCustomerId(customer.getCustId()),
                CobolText.trim(cardNumber),
                ssn,
                ssn.substring(0, 3) + "-" + ssn.substring(3, 5) + "-" + ssn.substring(5, 9),
                customer.getFicoCreditScore(),
                CobolText.trim(customer.getDobYyyyMmDd()),
                CobolText.trim(customer.getFirstName()),
                CobolText.trim(customer.getMiddleName()),
                CobolText.trim(customer.getLastName()),
                CobolText.trim(customer.getAddrLine1()),
                CobolText.trim(customer.getAddrLine2()),
                CobolText.trim(customer.getAddrLine3()),
                CobolText.trim(customer.getAddrStateCd()),
                CobolText.trim(customer.getAddrZip()),
                CobolText.trim(customer.getAddrCountryCd()),
                CobolText.trim(customer.getPhoneNum1()),
                CobolText.trim(customer.getPhoneNum2()),
                CobolText.trim(customer.getGovtIssuedId()),
                CobolText.trim(customer.getEftAccountId()),
                CobolText.trim(customer.getPriCardHolderInd()));
    }

    /** COACTUPC paragraph 9500-STORE-FETCHED-DATA: the ACUP-OLD-DETAILS snapshot. */
    public AccountUpdateForm toForm(AccountEntity account, CustomerEntity customer) {
        String ssn = CobolText.padRight(CobolText.trim(customer.getSsn()), 9);
        String openDate = CobolText.padRight(account.getOpenDate(), 10);
        String expiryDate = CobolText.padRight(account.getExpirationDate(), 10);
        String reissueDate = CobolText.padRight(account.getReissueDate(), 10);
        String dob = CobolText.padRight(customer.getDobYyyyMmDd(), 10);
        String phone1 = CobolText.padRight(customer.getPhoneNum1(), 15);
        String phone2 = CobolText.padRight(customer.getPhoneNum2(), 15);
        return new AccountUpdateForm(
                formatAccountId(account.getAcctId()),
                CobolText.trim(account.getActiveStatus()),
                CobolNumeric.format(account.getCurrBal()),
                CobolNumeric.format(account.getCreditLimit()),
                CobolNumeric.format(account.getCashCreditLimit()),
                openDate.substring(0, 4),
                openDate.substring(5, 7),
                openDate.substring(8, 10),
                expiryDate.substring(0, 4),
                expiryDate.substring(5, 7),
                expiryDate.substring(8, 10),
                reissueDate.substring(0, 4),
                reissueDate.substring(5, 7),
                reissueDate.substring(8, 10),
                CobolNumeric.format(account.getCurrCycCredit()),
                CobolNumeric.format(account.getCurrCycDebit()),
                CobolText.trim(account.getGroupId()),
                formatCustomerId(customer.getCustId()),
                ssn.substring(0, 3),
                ssn.substring(3, 5),
                ssn.substring(5, 9),
                dob.substring(0, 4),
                dob.substring(5, 7),
                dob.substring(8, 10),
                customer.getFicoCreditScore() == null ? "" : Integer.toString(customer.getFicoCreditScore()),
                CobolText.trim(customer.getFirstName()),
                CobolText.trim(customer.getMiddleName()),
                CobolText.trim(customer.getLastName()),
                CobolText.trim(customer.getAddrLine1()),
                CobolText.trim(customer.getAddrLine2()),
                CobolText.trim(customer.getAddrLine3()),
                CobolText.trim(customer.getAddrStateCd()),
                CobolText.trim(customer.getAddrZip()),
                CobolText.trim(customer.getAddrCountryCd()),
                phone1.substring(1, 4),
                phone1.substring(5, 8),
                phone1.substring(9, 13),
                phone2.substring(1, 4),
                phone2.substring(5, 8),
                phone2.substring(9, 13),
                CobolText.trim(customer.getGovtIssuedId()),
                CobolText.trim(customer.getEftAccountId()),
                CobolText.trim(customer.getPriCardHolderInd()));
    }

    /** ACCT-ID PIC 9(11). */
    public static String formatAccountId(Long accountId) {
        return accountId == null ? "" : CobolText.padLeftZero(Long.toString(accountId), 11);
    }

    /** CUST-ID PIC 9(09). */
    public static String formatCustomerId(Long customerId) {
        return customerId == null ? "" : CobolText.padLeftZero(Long.toString(customerId), 9);
    }
}
