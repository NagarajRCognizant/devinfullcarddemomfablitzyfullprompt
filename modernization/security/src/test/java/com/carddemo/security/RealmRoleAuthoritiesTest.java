package com.carddemo.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

/**
 * SEC-USR-TYPE used to travel in the token this service signed; it now arrives as a realm role, and
 * the authorities the screens are guarded with have to come out the same either way.
 */
class RealmRoleAuthoritiesTest {

    private final RealmRoleAuthorities authorities = new RealmRoleAuthorities();

    @Test
    void aRealmRoleBecomesTheAuthorityTheScreensAreGuardedWith() {
        assertThat(names(TokenFixtures.token().build())).containsExactly("ROLE_ADMIN");
    }

    @Test
    void everyRealmRoleOfTheTokenIsMapped() {
        assertThat(names(TokenFixtures.token()
                .claim("realm_access", Map.of("roles", List.of("USER", "SERVICE")))
                .build()))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_SERVICE");
    }

    @Test
    void theFlatClaimOfTheOldSelfIssuedTokenIsStillRead() {
        assertThat(names(TokenFixtures.token()
                .claim("realm_access", Map.of())
                .claim("roles", List.of("ROLE_ADMIN"))
                .build()))
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void anAlreadyPrefixedRoleIsNotPrefixedTwice() {
        assertThat(names(TokenFixtures.token()
                .claim("realm_access", Map.of("roles", List.of("ROLE_USER")))
                .build()))
                .containsExactly("ROLE_USER");
    }

    @Test
    void aTokenWithNoRolesCarriesNoAuthority() {
        assertThat(names(TokenFixtures.token().claim("realm_access", Map.of()).build())).isEmpty();
    }

    @Test
    void aRealmAccessClaimThatHoldsNoRoleListCarriesNoAuthority() {
        assertThat(names(TokenFixtures.token().claim("realm_access", Map.of("roles", "ADMIN"))
                .build()))
                .isEmpty();
    }

    private List<String> names(org.springframework.security.oauth2.jwt.Jwt token) {
        return authorities.convert(token).stream().map(GrantedAuthority::getAuthority).toList();
    }
}
