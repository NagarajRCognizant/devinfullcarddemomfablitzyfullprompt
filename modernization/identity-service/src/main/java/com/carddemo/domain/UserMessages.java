package com.carddemo.domain;

import com.carddemo.cobol.CobolText;

/**
 * The message literals of the sign-on and user administration programs, kept verbatim.
 *
 * <p>Each constant is one MOVE of a literal to WS-MESSAGE in app/cbl/COSGN00C.cbl,
 * app/cbl/COUSR00C.cbl, app/cbl/COUSR01C.cbl, app/cbl/COUSR02C.cbl, app/cbl/COUSR03C.cbl or
 * app/cbl/COADM01C.cbl. The irregular spacing and the trailing ellipses are part of the observable
 * screen behaviour and are reproduced exactly.
 */
public final class UserMessages {

    // ---------------------------------------------------------------- COSGN00C (Sign-on)

    /** PROCESS-ENTER-KEY of COSGN00C. */
    public static final String SIGNON_USER_ID_REQUIRED = "Please enter User ID ...";
    /** PROCESS-ENTER-KEY of COSGN00C. */
    public static final String SIGNON_PASSWORD_REQUIRED = "Please enter Password ...";
    /** READ-USER-SEC-FILE of COSGN00C, DFHRESP(NOTFND). */
    public static final String SIGNON_USER_NOT_FOUND = "User not found. Try again ...";
    /** READ-USER-SEC-FILE of COSGN00C, password mismatch. */
    public static final String SIGNON_WRONG_PASSWORD = "Wrong Password. Try again ...";
    /** READ-USER-SEC-FILE of COSGN00C, WHEN OTHER. */
    public static final String SIGNON_UNABLE_TO_VERIFY = "Unable to verify the User ...";

    // ---------------------------------------------------------------- COUSR00C (User List)

    /** PROCESS-ENTER-KEY of COUSR00C, WHEN OTHER of the selection EVALUATE. */
    public static final String LIST_INVALID_SELECTION =
            "Invalid selection. Valid values are U and D";
    /** PROCESS-PAGE-FORWARD of COUSR00C. */
    public static final String LIST_AT_BOTTOM = "You are already at the bottom of the page...";
    /** PROCESS-PF7-KEY of COUSR00C. */
    public static final String LIST_AT_TOP = "You are already at the top of the page...";
    /** STARTBR-USER-SEC-FILE of COUSR00C, DFHRESP(NOTFND). */
    public static final String LIST_NO_USERS_FOUND = "You are at the top of the page...";
    /** READNEXT-USER-SEC-FILE of COUSR00C, DFHRESP(ENDFILE). */
    public static final String LIST_REACHED_BOTTOM = "You have reached the bottom of the page...";
    /** STARTBR/READNEXT/READPREV of COUSR00C, WHEN OTHER. */
    public static final String LIST_UNABLE_TO_LOOKUP = "Unable to lookup User...";

    // ---------------------------------------------------------------- COUSR01C (User Add)

    /** PROCESS-ENTER-KEY of COUSR01C. */
    public static final String FIRST_NAME_REQUIRED = "First Name can NOT be empty...";
    /** PROCESS-ENTER-KEY of COUSR01C. */
    public static final String LAST_NAME_REQUIRED = "Last Name can NOT be empty...";
    /** PROCESS-ENTER-KEY of COUSR01C and COUSR02C. */
    public static final String USER_ID_REQUIRED = "User ID can NOT be empty...";
    /** PROCESS-ENTER-KEY of COUSR01C. */
    public static final String PASSWORD_REQUIRED = "Password can NOT be empty...";
    /** PROCESS-ENTER-KEY of COUSR01C. */
    public static final String USER_TYPE_REQUIRED = "User Type can NOT be empty...";
    /** WRITE-USER-SEC-FILE of COUSR01C, DFHRESP(DUPKEY) and DFHRESP(DUPREC). */
    public static final String USER_ALREADY_EXISTS = "User ID already exist...";
    /** WRITE-USER-SEC-FILE of COUSR01C, WHEN OTHER. */
    public static final String UNABLE_TO_ADD_USER = "Unable to Add User...";

    // ---------------------------------------------------------------- COUSR02C (User Update)

    /** UPDATE-USER-INFO of COUSR02C when no editable field differs from the stored record. */
    public static final String NOTHING_TO_UPDATE = "Please modify to update ...";
    /** READ-USER-SEC-FILE of COUSR02C and COUSR03C, DFHRESP(NOTFND). */
    public static final String USER_ID_NOT_FOUND = "User ID NOT found...";
    /** UPDATE-USER-SEC-FILE of COUSR02C and DELETE-USER-INFO of COUSR03C, WHEN OTHER. */
    public static final String UNABLE_TO_UPDATE_USER = "Unable to Update User...";

    // ---------------------------------------------------------------- COADM01C (Admin menu)

    /** PROCESS-ENTER-KEY of COADM01C. */
    public static final String INVALID_MENU_OPTION = "Please enter a valid option number...";

    private UserMessages() {
    }

    /** WRITE-USER-SEC-FILE of COUSR01C, normal response. */
    public static String userAdded(String userId) {
        return "User " + CobolText.trim(userId) + " has been added ...";
    }

    /** UPDATE-USER-SEC-FILE of COUSR02C, normal response. */
    public static String userUpdated(String userId) {
        return "User " + CobolText.trim(userId) + " has been updated ...";
    }

    /** DELETE-USER-INFO of COUSR03C, normal response. */
    public static String userDeleted(String userId) {
        return "User " + CobolText.trim(userId) + " has been deleted ...";
    }
}
