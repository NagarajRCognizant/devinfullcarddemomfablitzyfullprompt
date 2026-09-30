package com.carddemo.api.dto;

/**
 * The editable fields of the COACTUP map as keyed text.
 *
 * <p>This is the explicit request/response replacement for the ACUP-OLD-DETAILS and
 * ACUP-NEW-DETAILS areas that COACTUPC carried in the COMMAREA between pseudo conversational
 * turns: the client receives the fetched values as {@code original} and sends them back
 * unchanged alongside the keyed values, so the change detection and the record-changed check can
 * run without server side screen state.
 *
 * <p>Every field stays a string because the edits are defined on the keyed characters: an
 * unparsable amount, a blank, an asterisk and a zero are all distinct inputs in the source.
 */
public record AccountUpdateForm(
        String accountId,
        String activeStatus,
        String currentBalance,
        String creditLimit,
        String cashCreditLimit,
        String openYear,
        String openMonth,
        String openDay,
        String expiryYear,
        String expiryMonth,
        String expiryDay,
        String reissueYear,
        String reissueMonth,
        String reissueDay,
        String currentCycleCredit,
        String currentCycleDebit,
        String groupId,
        String customerId,
        String ssnPart1,
        String ssnPart2,
        String ssnPart3,
        String dobYear,
        String dobMonth,
        String dobDay,
        String ficoScore,
        String firstName,
        String middleName,
        String lastName,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String zip,
        String country,
        String phone1Area,
        String phone1Prefix,
        String phone1Line,
        String phone2Area,
        String phone2Prefix,
        String phone2Line,
        String governmentIssuedId,
        String eftAccountId,
        String primaryCardHolder) {
}
