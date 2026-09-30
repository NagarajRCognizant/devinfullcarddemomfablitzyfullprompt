package com.carddemo.api.dto;

/**
 * The output fields of the COACTVW map (app/bms/COACTVW.bms) for one account and its customer.
 *
 * <p>Amounts carry both the exact value and the edited text of the map's
 * {@code PIC +ZZZ,ZZZ,ZZZ.99} fields; the dates and the trailing-space padded character fields of
 * the records are returned trimmed, which is what the map displays.
 */
public record AccountDetails(
        String accountId,
        String activeStatus,
        MoneyValue currentBalance,
        MoneyValue creditLimit,
        MoneyValue cashCreditLimit,
        MoneyValue currentCycleCredit,
        MoneyValue currentCycleDebit,
        String openDate,
        String expirationDate,
        String reissueDate,
        String groupId,
        String customerId,
        String cardNumber,
        String ssn,
        String ssnFormatted,
        Integer ficoScore,
        String dateOfBirth,
        String firstName,
        String middleName,
        String lastName,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String zip,
        String country,
        String phone1,
        String phone2,
        String governmentIssuedId,
        String eftAccountId,
        String primaryCardHolder) {
}
