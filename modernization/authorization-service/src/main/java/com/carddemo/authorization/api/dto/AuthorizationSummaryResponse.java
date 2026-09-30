package com.carddemo.authorization.api.dto;

import java.util.List;

/**
 * Map COPAU0A of transaction CPVS: the account and customer header, the pending authorization
 * totals and one page of five authorizations.
 *
 * <p>The paging cursor replaces the terminal's conversational state. COPAUS0C kept the key of the
 * last row and an array of page start keys in the COMMAREA; the response returns the same two keys
 * explicitly, so the client asks for the next or previous page by key and no session state is held
 * between requests.
 */
public record AuthorizationSummaryResponse(
        long accountId,
        Long customerId,
        String customerName,
        String addressLine1,
        String addressLine2,
        String phone,
        MoneyValue creditLimit,
        MoneyValue cashCreditLimit,
        int approvedCount,
        int declinedCount,
        MoneyValue creditBalance,
        MoneyValue cashBalance,
        MoneyValue approvedAmount,
        MoneyValue declinedAmount,
        boolean summaryFound,
        List<AuthorizationListRow> authorizations,
        String pageStartAuthKey,
        String lastAuthKey,
        boolean morePages,
        String message) {
}
