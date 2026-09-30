package com.carddemo.authorization.domain;

/**
 * The literal messages the authorization screens write into WS-MESSAGE.
 *
 * <p>They are user visible output, so they are carried over unchanged, including the trailing
 * ellipses and the spacing of the source literals. The PF key names that produced them are gone -
 * the buttons of the React screens raise the same conditions - but the text a user reads is the
 * same text the 3270 map showed.
 */
public final class AuthorizationMessages {

    /** COPAUS0C PROCESS-ENTER-KEY: the account id field was left empty. */
    public static final String PLEASE_ENTER_ACCT_ID = "Please enter Acct Id...";

    /** COPAUS0C PROCESS-ENTER-KEY: the account id field held a non numeric value. */
    public static final String ACCT_ID_MUST_BE_NUMERIC = "Acct Id must be Numeric ...";

    /** COPAUS0C PROCESS-ENTER-KEY: a selection character other than S or s. */
    public static final String INVALID_SELECTION = "Invalid selection. Valid value is S";

    /** COPAUS0C PROCESS-PF7-KEY: already on the first page of authorizations. */
    public static final String TOP_OF_PAGE = "You are already at the top of the page...";

    /** COPAUS0C PROCESS-PF8-KEY: no further authorizations after the current page. */
    public static final String BOTTOM_OF_PAGE = "You are already at the bottom of the page...";

    /** COPAUS1C PROCESS-PF8-KEY: no authorization after the one on display. */
    public static final String LAST_AUTHORIZATION = "Already at the last Authorization...";

    /** COPAUS1C MARK-AUTH-FRAUD when the authorization was already confirmed as fraud. */
    public static final String FRAUD_REMOVED = "AUTH FRAUD REMOVED...";

    /** COPAUS1C MARK-AUTH-FRAUD when the authorization was not yet reported. */
    public static final String FRAUD_MARKED = "AUTH MARKED FRAUD...";

    private AuthorizationMessages() {
    }

    /** COPAUS0C GETCARDXREF-BYACCT, the NOTFND branch of the cross reference read. */
    public static String accountNotFoundInXref(String accountId) {
        return "Account:" + accountId + " not found in XREF file.";
    }

    /** COPAUS0C GETACCTDATA-BYACCT, the NOTFND branch of the account master read. */
    public static String accountNotFoundInAcct(String accountId) {
        return "Account:" + accountId + " not found in ACCT file.";
    }

    /** COPAUS0C GETCUSTDATA-BYCUST, the NOTFND branch of the customer master read. */
    public static String customerNotFound(String customerId) {
        return "Customer:" + customerId + " not found in CUST file.";
    }

    /** COPAUS1C POPULATE-AUTH-DETAILS when the selected authorization no longer exists. */
    public static String authorizationNotFound(String authKey) {
        return "Authorization:" + authKey + " not found.";
    }
}
