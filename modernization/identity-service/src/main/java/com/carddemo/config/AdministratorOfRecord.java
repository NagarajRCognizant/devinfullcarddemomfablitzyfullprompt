package com.carddemo.config;

import com.carddemo.persistence.entity.SecurityUserEntity;
import com.carddemo.persistence.repository.SecurityUserRepository;
import com.carddemo.security.SignedOnUser;
import com.carddemo.service.UserTypes;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * CDEMO-USRTYP-ADMIN of the USRSEC record, which is what the four user screens were reachable to.
 *
 * <p>The realm role is written from SEC-USR-TYPE when the account is provisioned, so ordinarily the
 * two say the same thing; the record is nevertheless what is asked, because it is the source of the
 * business identity and a role granted in the realm alone is not an authority the source ever had.
 * A caller whose token carries the administrator role while its record does not is refused.
 */
@Component
public class AdministratorOfRecord implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final SecurityUserRepository users;

    public AdministratorOfRecord(SecurityUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       RequestAuthorizationContext context) {
        Authentication caller = authentication.get();
        if (caller == null || !caller.isAuthenticated()
                || !(caller.getPrincipal() instanceof Jwt token)) {
            return new AuthorizationDecision(false);
        }
        boolean granted = caller.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        if (!granted) {
            return new AuthorizationDecision(false);
        }
        Optional<SecurityUserEntity> found =
                users.findById(UserTypes.key(SignedOnUser.of(token).userId()));
        return new AuthorizationDecision(
                found.map(user -> UserTypes.isAdmin(user.getUserType())).orElse(false));
    }
}
