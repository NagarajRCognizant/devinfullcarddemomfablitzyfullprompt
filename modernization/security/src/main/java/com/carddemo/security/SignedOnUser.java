package com.carddemo.security;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The token read the way the programs read the sign-on: an eight character user id and the one
 * character SEC-USR-TYPE the menus branch on.
 *
 * <p>Keycloak stores a username in lower case, while USRSEC is a KSDS keyed on an upper case
 * CHAR(8); the id is folded back to upper case so it is the key of the same row COSGN00C read.
 *
 * @param userId SEC-USR-ID of the row the caller signed on with
 * @param userType 'A' for CDEMO-USRTYP-ADMIN, 'U' for every other user, derived from the realm role
 */
public record SignedOnUser(String userId, String userType) {

    /** CDEMO-USRTYP-ADMIN of app/cpy/COCOM01Y.cpy. */
    public static final String ADMIN = "A";

    /** Any other SEC-USR-TYPE value; the programs only test for 'A'. */
    public static final String REGULAR = "U";

    private static final String USERNAME = "preferred_username";
    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String ADMIN_ROLE = "ADMIN";

    public static SignedOnUser of(Jwt token) {
        String username = token.getClaimAsString(USERNAME);
        String userId = username == null ? "" : username.trim().toUpperCase(Locale.ROOT);
        return new SignedOnUser(userId, isAdmin(token) ? ADMIN : REGULAR);
    }

    public boolean administrator() {
        return ADMIN.equals(userType);
    }

    private static boolean isAdmin(Jwt token) {
        Map<String, Object> realmAccess = token.getClaimAsMap(REALM_ACCESS);
        if (realmAccess != null && realmAccess.get(ROLES) instanceof Collection<?> roles) {
            if (roles.stream().map(String::valueOf).anyMatch(ADMIN_ROLE::equals)) {
                return true;
            }
        }
        List<String> flat = token.getClaimAsStringList(ROLES);
        return flat != null && (flat.contains(ADMIN_ROLE) || flat.contains("ROLE_" + ADMIN_ROLE));
    }
}
