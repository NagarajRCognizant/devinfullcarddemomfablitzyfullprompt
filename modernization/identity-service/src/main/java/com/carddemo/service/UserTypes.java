package com.carddemo.service;

import com.carddemo.cobol.CobolText;

/**
 * The SEC-USR-TYPE values and the key handling of USRSEC.
 *
 * <p>COSGN00C treats CDEMO-USRTYP-ADMIN ('A') as the only administrator value and routes every
 * other value, including a space, to the regular user menu; that single comparison is the whole
 * authorisation model of the source and is reproduced here.
 */
public final class UserTypes {

    /** CDEMO-USRTYP-ADMIN of app/cpy/COCOM01Y.cpy. */
    public static final String ADMIN = "A";
    /** CDEMO-USRTYP-USER of app/cpy/COCOM01Y.cpy. */
    public static final String REGULAR = "U";
    /** SEC-USR-ID PIC X(08). */
    public static final int USER_ID_LENGTH = 8;

    private UserTypes() {
    }

    public static boolean isAdmin(String userType) {
        return ADMIN.equals(CobolText.trim(userType));
    }

    /** The value stored in the fixed length key column: right padded with spaces. */
    public static String key(String userId) {
        String trimmed = CobolText.trim(userId);
        if (trimmed.length() > USER_ID_LENGTH) {
            trimmed = trimmed.substring(0, USER_ID_LENGTH);
        }
        return CobolText.padRight(trimmed, USER_ID_LENGTH);
    }

    /** The value shown on screen: the stored CHAR field without its padding. */
    public static String normaliseId(String userId) {
        return CobolText.trim(userId);
    }
}
