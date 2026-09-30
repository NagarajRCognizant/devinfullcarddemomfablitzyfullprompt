package com.carddemo.api.dto;

import java.math.BigDecimal;

/**
 * The cross reference, account master and customer master fields other bounded contexts need when
 * they can only reach this data through an API.
 *
 * <p>The three {@code found} flags reproduce WS-XREF-READ-FLG, WS-ACCT-MASTER-READ-FLG and
 * WS-CUST-MASTER-READ-FLG of COPAUA0C and COPAUS0C: a missing record is a documented outcome of
 * those programs, not a failure, so the response carries the flags instead of a 404 and the caller
 * keeps the decision logic the source program had.
 *
 * <p>The cycle amounts, the expiry date and the account group are the remaining ACCTDAT fields
 * CBTRN02C and CBACT04C read before they decide whether a transaction may be posted and at what
 * rate, so the transaction context receives them from the same read rather than a second call.
 */
public record CardholderLookupResponse(
        boolean cardFound,
        boolean accountFound,
        boolean customerFound,
        String cardNumber,
        Long accountId,
        Long customerId,
        String accountActiveStatus,
        BigDecimal creditLimit,
        BigDecimal cashCreditLimit,
        BigDecimal currentBalance,
        BigDecimal currentCycleCredit,
        BigDecimal currentCycleDebit,
        String expirationDate,
        String accountGroupId,
        String firstName,
        String middleName,
        String lastName,
        String addressLine1,
        String addressLine2,
        String addressLine3,
        String stateCode,
        String zip,
        String phone1) {

    /** The cross reference read found nothing, so nothing downstream was read either. */
    public static CardholderLookupResponse cardNotFound(String cardNumber) {
        return new CardholderLookupResponse(false, false, false, cardNumber, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
