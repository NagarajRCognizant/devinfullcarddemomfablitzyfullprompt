package com.carddemo.domain;

import com.carddemo.cobol.CobolText;

/**
 * The message literals of the two online programs, kept verbatim.
 *
 * <p>Each constant is one 88-level condition name of WS-RETURN-MSG or WS-INFO-MSG in
 * app/cbl/COACTVWC.cbl / app/cbl/COACTUPC.cbl, or one of the STRING statements that build a
 * message inline. The texts are part of the observable behaviour of the screens, so they are
 * reproduced exactly, including their irregular spacing.
 *
 * <p>The {@code Resp:}/{@code Reas:} suffixes that the COBOL appends to the not-found and file
 * error messages carry CICS RESP/RESP2 values. Those are platform diagnostics rather than
 * business text, so they are dropped here and the omission is recorded in the mainframe-to-target
 * mapping.
 */
public final class ScreenMessages {

    // ---------------------------------------------------------------- COACTVWC (Account View)

    /** WS-PROMPT-FOR-INPUT. */
    public static final String VIEW_PROMPT_FOR_INPUT = "Enter or update id of account to display";
    /** WS-INFORM-OUTPUT. */
    public static final String VIEW_INFORM_OUTPUT = "Displaying details of given Account";
    /** WS-PROMPT-FOR-ACCT, shared by both programs. */
    public static final String PROMPT_FOR_ACCT = "Account number not provided";
    /** NO-SEARCH-CRITERIA-RECEIVED, shared by both programs. */
    public static final String NO_SEARCH_CRITERIA_RECEIVED = "No input received";
    /** 2210-EDIT-ACCOUNT of COACTVWC moves this literal, note the double space after "must". */
    public static final String VIEW_ACCT_FILTER_NOT_VALID =
            "Account Filter must  be a non-zero 11 digit number";

    // ---------------------------------------------------------------- COACTUPC (Account Update)

    /** 1210-EDIT-ACCOUNT of COACTUPC builds this message with STRING. */
    public static final String UPDATE_ACCT_FILTER_NOT_VALID =
            "Account Number if supplied must be a 11 digit Non-Zero Number";
    /** FOUND-ACCOUNT-DATA. */
    public static final String FOUND_ACCOUNT_DATA = "Details of selected account shown above";
    /** PROMPT-FOR-SEARCH-KEYS. */
    public static final String PROMPT_FOR_SEARCH_KEYS = "Enter or update id of account to update";
    /** PROMPT-FOR-CHANGES. */
    public static final String PROMPT_FOR_CHANGES = "Update account details presented above.";
    /** PROMPT-FOR-CONFIRMATION. */
    public static final String PROMPT_FOR_CONFIRMATION = "Changes validated.Press F5 to save";
    /** CONFIRM-UPDATE-SUCCESS. */
    public static final String CONFIRM_UPDATE_SUCCESS = "Changes committed to database";
    /** INFORM-FAILURE. */
    public static final String INFORM_FAILURE = "Changes unsuccessful. Please try again";
    /** NO-CHANGES-DETECTED. */
    public static final String NO_CHANGES_DETECTED =
            "No change detected with respect to values fetched.";
    /** COULD-NOT-LOCK-ACCT-FOR-UPDATE. */
    public static final String COULD_NOT_LOCK_ACCT_FOR_UPDATE =
            "Could not lock account record for update";
    /** COULD-NOT-LOCK-CUST-FOR-UPDATE. */
    public static final String COULD_NOT_LOCK_CUST_FOR_UPDATE =
            "Could not lock customer record for update";
    /** DATA-WAS-CHANGED-BEFORE-UPDATE. */
    public static final String DATA_WAS_CHANGED_BEFORE_UPDATE =
            "Record changed by some one else. Please review";
    /** LOCKED-BUT-UPDATE-FAILED. */
    public static final String LOCKED_BUT_UPDATE_FAILED = "Update of record failed";

    // ---------------------------------------------------------------- COMEN01C (access rule)

    /** PROCESS-ENTER-KEY of COMEN01C, note the trailing spaces of the PIC X(33) literal. */
    public static final String NO_ACCESS_ADMIN_ONLY = "No access - Admin Only option... ";

    private ScreenMessages() {
    }

    /** 9200-GETCARDXREF-BYACCT, NOTFND branch. */
    public static String accountNotInCrossReference(String accountId) {
        return "Account:" + accountId(accountId) + " not found in Cross ref file.";
    }

    /** 9300-GETACCTDATA-BYACCT, NOTFND branch. */
    public static String accountNotInMaster(String accountId) {
        return "Account:" + accountId(accountId) + " not found in Acct Master file.";
    }

    /** 9400-GETCUSTDATA-BYCUST, NOTFND branch. */
    public static String customerNotInMaster(String customerId) {
        return "CustId:" + CobolText.padLeftZero(CobolText.trim(customerId), 9)
                + " not found in customer master.";
    }

    /** WS-FILE-ERROR-MESSAGE, used by every WHEN OTHER branch of the file reads. */
    public static String fileError(String operation, String file) {
        return "File Error: " + CobolText.padRight(operation, 8) + " on " + CobolText.padRight(file, 9);
    }

    private static String accountId(String accountId) {
        return CobolText.padLeftZero(CobolText.trim(accountId), 11);
    }
}
