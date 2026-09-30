package com.carddemo.transaction.client;

import java.math.BigDecimal;

/**
 * The cross reference and account master fields the transaction programs read for themselves, as
 * the account service returns them.
 *
 * <p>The flags are the source read outcomes: COTRN02C distinguishes "Account ID NOT found..." from
 * "Card Number NOT found..." by which keyed read missed, and COBIL00C needs the balance whether or
 * not the customer record exists, so the flags are carried rather than collapsed into a 404.
 *
 * <p>The credit limit, the cycle amounts, the expiry date and the account group are the remaining
 * ACCTDAT fields CBTRN02C 1500-B-LOOKUP-ACCT and CBACT04C 1100-GET-ACCT-DATA read before they
 * decide whether a transaction may be posted and at what rate.
 */
public record CardholderView(
        boolean cardFound,
        boolean accountFound,
        boolean customerFound,
        String cardNumber,
        Long accountId,
        BigDecimal currentBalance,
        BigDecimal creditLimit,
        BigDecimal currentCycleCredit,
        BigDecimal currentCycleDebit,
        String expirationDate,
        String accountGroupId) {

    /** The DFHRESP(NOTFND) of the cross reference read, which the account service reports as 404. */
    public static CardholderView notFound(String cardNumber) {
        return new CardholderView(false, false, false, cardNumber, null, null, null, null, null,
                null, null);
    }

    /** The screens read only the key and the balance; the batch jobs read the rest as well. */
    public static CardholderView found(String cardNumber, Long accountId, BigDecimal currentBalance) {
        return new CardholderView(true, true, true, cardNumber, accountId, currentBalance,
                null, null, null, null, null);
    }
}
