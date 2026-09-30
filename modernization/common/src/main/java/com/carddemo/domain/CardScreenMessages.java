package com.carddemo.domain;

/**
 * The message literals of the three credit card screens, kept verbatim.
 *
 * <p>Each constant is one 88-level condition name of WS-ERROR-MSG, WS-RETURN-MSG or WS-INFO-MSG in
 * app/cbl/COCRDLIC.cbl (list), app/cbl/COCRDSLC.cbl (view) and app/cbl/COCRDUPC.cbl (update),
 * including the irregular spacing the maps showed.
 */
public final class CardScreenMessages {

    // ------------------------------------------------------------------- COCRDLIC (Card List)

    /** WS-INFORM-REC-ACTIONS. */
    public static final String LIST_ACTIONS = "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD";
    /** WS-NO-RECORDS-FOUND. */
    public static final String NO_RECORDS_FOUND = "NO RECORDS FOUND FOR THIS SEARCH CONDITION.";
    /** WS-MORE-THAN-1-ACTION. */
    public static final String MORE_THAN_ONE_ACTION = "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE";
    /** WS-INVALID-ACTION-CODE. */
    public static final String INVALID_ACTION_CODE = "INVALID ACTION CODE";
    /** 1400-SETUP-MESSAGE, PF7 on the first page. */
    public static final String NO_PREVIOUS_PAGES = "NO PREVIOUS PAGES TO DISPLAY";
    /** 1400-SETUP-MESSAGE, PF8 when the last page has already been shown. */
    public static final String NO_MORE_PAGES = "NO MORE PAGES TO DISPLAY";
    /** 9000-READ-FORWARD, ENDFILE branch. */
    public static final String NO_MORE_RECORDS = "NO MORE RECORDS TO SHOW";
    /** 2210-EDIT-ACCOUNT of COCRDLIC. */
    public static final String LIST_ACCOUNT_FILTER_INVALID =
            "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER";
    /** 2220-EDIT-CARD of COCRDLIC. */
    public static final String LIST_CARD_FILTER_INVALID =
            "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER";

    // ------------------------------------------------------------------- COCRDSLC (Card View)

    /** FOUND-CARDS-FOR-ACCOUNT of COCRDSLC, with its three leading spaces. */
    public static final String VIEW_DISPLAYING_DETAILS = "   Displaying requested details";
    /** WS-PROMPT-FOR-INPUT of COCRDSLC, shared with the update screen. */
    public static final String PROMPT_FOR_SEARCH_KEYS = "Please enter Account and Card Number";

    // ----------------------------------------------------------------- COCRDUPC (Card Update)

    /** FOUND-CARDS-FOR-ACCOUNT of COCRDUPC. */
    public static final String UPDATE_DETAILS_SHOWN = "Details of selected card shown above";
    /** PROMPT-FOR-CHANGES. */
    public static final String PROMPT_FOR_CHANGES = "Update card details presented above.";
    /** PROMPT-FOR-CONFIRMATION. */
    public static final String PROMPT_FOR_CONFIRMATION = "Changes validated.Press F5 to save";
    /** CONFIRM-UPDATE-SUCCESS. */
    public static final String UPDATE_SUCCESS = "Changes committed to database";
    /** INFORM-FAILURE. */
    public static final String UPDATE_FAILURE = "Changes unsuccessful. Please try again";
    /** NO-CHANGES-DETECTED. */
    public static final String NO_CHANGES_DETECTED = "No change detected with respect to values fetched.";
    /** WS-PROMPT-FOR-NAME. */
    public static final String PROMPT_FOR_NAME = "Card name not provided";
    /** WS-NAME-MUST-BE-ALPHA. */
    public static final String NAME_MUST_BE_ALPHA = "Card name can only contain alphabets and spaces";
    /** CARD-STATUS-MUST-BE-YES-NO. */
    public static final String STATUS_MUST_BE_YES_NO = "Card Active Status must be Y or N";
    /** CARD-EXPIRY-MONTH-NOT-VALID. */
    public static final String EXPIRY_MONTH_NOT_VALID = "Card expiry month must be between 1 and 12";
    /** CARD-EXPIRY-YEAR-NOT-VALID. */
    public static final String EXPIRY_YEAR_NOT_VALID = "Invalid card expiry year";
    /** DATA-WAS-CHANGED-BEFORE-UPDATE. */
    public static final String CHANGED_BY_SOMEONE_ELSE = "Record changed by some one else. Please review";
    /** LOCKED-BUT-UPDATE-FAILED. */
    public static final String UPDATE_OF_RECORD_FAILED = "Update of record failed";

    // ------------------------------------------------------- shared by the view and the update

    /** WS-PROMPT-FOR-CARD. */
    public static final String PROMPT_FOR_CARD = "Card number not provided";
    /** SEARCHED-ACCT-ZEROES and SEARCHED-ACCT-NOT-NUMERIC, which carry the same text. */
    public static final String ACCOUNT_NOT_ELEVEN_DIGITS =
            "Account number must be a non zero 11 digit number";
    /** SEARCHED-CARD-NOT-NUMERIC. */
    public static final String CARD_NOT_SIXTEEN_DIGITS =
            "Card number if supplied must be a 16 digit number";
    /** DID-NOT-FIND-ACCT-IN-CARDXREF. */
    public static final String ACCOUNT_NOT_IN_CARDS = "Did not find this account in cards database";
    /** DID-NOT-FIND-ACCTCARD-COMBO. */
    public static final String CARD_COMBINATION_NOT_FOUND = "Did not find cards for this search condition";

    private CardScreenMessages() {
    }
}
