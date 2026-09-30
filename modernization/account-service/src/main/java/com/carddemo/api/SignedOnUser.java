package com.carddemo.api;

import com.carddemo.service.MenuAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The SEC-USR-ID and SEC-USR-TYPE of the signed-on operator, which the COMMAREA carried as
 * CDEMO-USER-ID and CDEMO-USER-TYPE.
 *
 * <p>COSGN00C read USRSEC and put the user id and type in the COMMAREA, and every screen after it
 * read them from there. The identity service now performs that read and puts the same two values in
 * the token, so the account screens take the type from the authenticated request rather than from a
 * value the caller states.
 */
final class SignedOnUser {

    private SignedOnUser() {
    }

    /**
     * The eight character USRSEC key of the operator.
     *
     * <p>The name of a token authentication is the realm's own identifier of the user, which is not what
     * COSGN00C put in the COMMAREA and not what a screen shows; the user id is read from the token the
     * same way {@link com.carddemo.security.SignedOnUser} reads it.
     */
    static String userId(Authentication authentication) {
        return authentication.getPrincipal() instanceof Jwt token
                ? com.carddemo.security.SignedOnUser.of(token).userId()
                : authentication.getName();
    }

    static String userType(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        return admin ? MenuAccessService.USER_TYPE_ADMIN : MenuAccessService.USER_TYPE_REGULAR;
    }
}
