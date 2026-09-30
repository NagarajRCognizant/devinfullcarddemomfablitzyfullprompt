package com.carddemo.authorization.client;

import java.math.BigDecimal;

/**
 * The cross reference, account and customer data the authorization programs read for themselves,
 * as the account service returns it.
 *
 * <p>The three flags are the source read outcomes (WS-XREF-READ-FLG, WS-ACCT-MASTER-READ-FLG,
 * WS-CUST-MASTER-READ-FLG); the decision logic of COPAUA0C and the header of the CPVS screen are
 * both driven by them, so they are carried rather than collapsed into a null check.
 */
public record CardholderView(
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
        String firstName,
        String middleName,
        String lastName,
        String addressLine1,
        String addressLine2,
        String addressLine3,
        String stateCode,
        String zip,
        String phone1) {

    public static CardholderView notFound(String cardNumber) {
        return new CardholderView(false, false, false, cardNumber, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }
}
