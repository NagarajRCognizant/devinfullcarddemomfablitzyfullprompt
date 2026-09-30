package com.carddemo.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One page of the sequential CARDXREF read of CBSTM03A, with the CUSTDAT and ACCTDAT records each
 * cross reference entry leads to.
 *
 * <p>The statement job belongs to the transaction context and cannot read these three files, so
 * the account context publishes them in the order the cluster delivered them - ascending card
 * number - and resolves the two keyed reads 2000-CUSTFILE-GET and 3000-ACCTFILE-GET on the way.
 * Those reads abend the source program when they miss, so a miss is reported on the row rather
 * than dropped and the caller keeps that decision.
 */
public record StatementPartyResponse(List<StatementParty> rows, boolean lastPage) {

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
}
