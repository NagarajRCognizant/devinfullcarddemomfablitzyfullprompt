package com.carddemo.service;

import java.math.BigDecimal;

/**
 * The keyed screen values after every edit of COACTUPC paragraph 1200-EDIT-MAP-INPUTS passed,
 * already assembled into the shapes the ACCTDAT and CUSTDAT records hold.
 *
 * <p>Paragraph 9600-WRITE-PROCESSING builds the dates with {@code STRING year '-' month '-' day}
 * and the phone numbers with {@code STRING '(' area ')' prefix '-' line}, so those composites are
 * produced here and carried through to the entities unchanged.
 */
public record ValidatedAccountUpdate(
        long accountId,
        String activeStatus,
        BigDecimal currentBalance,
        BigDecimal creditLimit,
        BigDecimal cashCreditLimit,
        BigDecimal currentCycleCredit,
        BigDecimal currentCycleDebit,
        String openDate,
        String expirationDate,
        String reissueDate,
        String groupId,
        long customerId,
        String firstName,
        String middleName,
        String lastName,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String country,
        String zip,
        String phoneNum1,
        String phoneNum2,
        String ssn,
        String governmentIssuedId,
        String dateOfBirth,
        String eftAccountId,
        String primaryCardHolder,
        int ficoScore) {
}
