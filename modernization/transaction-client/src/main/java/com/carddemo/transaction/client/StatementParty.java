package com.carddemo.transaction.client;

import java.math.BigDecimal;

/**
 * One CARDXREF record of CBSTM03A with the CUSTDAT and ACCTDAT records it leads to, as the account
 * service returns them.
 *
 * <p>The two found flags are the reads of 2000-CUSTFILE-GET and 3000-ACCTFILE-GET, whose miss path
 * is an abend in the source; the statement job makes the same decision from these flags.
 */
public record StatementParty(
        String cardNumber,
        Long accountId,
        Long customerId,
        boolean accountFound,
        boolean customerFound,
        BigDecimal currentBalance,
        Integer ficoScore,
        String firstName,
        String middleName,
        String lastName,
        String addressLine1,
        String addressLine2,
        String addressLine3,
        String stateCode,
        String countryCode,
        String zip) {
}
