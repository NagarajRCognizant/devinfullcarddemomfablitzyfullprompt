package com.carddemo.keycloak;

import com.carddemo.cobol.CobolText;
import com.carddemo.persistence.entity.SecurityUserEntity;
import com.carddemo.service.UserTypes;
import java.util.Locale;

/**
 * The USRSEC record as the realm needs it.
 *
 * <p>The user id is a PIC X(08) key in upper case; a Keycloak username is stored folded to lower
 * case, so the username is the lower case form of the trimmed key and the token is folded back on
 * the way in.
 *
 * @param userId trimmed SEC-USR-ID
 * @param firstName SEC-USR-FNAME
 * @param lastName SEC-USR-LNAME
 * @param password SEC-USR-PWD as keyed on the screen, set as the initial credential
 * @param realmRole the role the type maps to: {@code ADMIN} for 'A', {@code USER} otherwise
 */
public record DirectoryUser(String userId, String firstName, String lastName, String password,
        String realmRole) {

    /** Realm role the SEC-USR-TYPE 'A' of CDEMO-USRTYP-ADMIN maps to. */
    public static final String ADMIN_ROLE = "ADMIN";

    /** Realm role every other SEC-USR-TYPE maps to. */
    public static final String USER_ROLE = "USER";

    public static DirectoryUser of(SecurityUserEntity user) {
        return new DirectoryUser(UserTypes.normaliseId(user.getUserId()),
                CobolText.trim(user.getFirstName()), CobolText.trim(user.getLastName()),
                CobolText.trim(user.getPassword()),
                UserTypes.isAdmin(user.getUserType()) ? ADMIN_ROLE : USER_ROLE);
    }

    /** The username of the account: the key of the record, as Keycloak stores a username. */
    public String username() {
        return userId.toLowerCase(Locale.ROOT);
    }

    public boolean administrator() {
        return ADMIN_ROLE.equals(realmRole);
    }
}
