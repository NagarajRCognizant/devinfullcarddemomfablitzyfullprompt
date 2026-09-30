package com.carddemo.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * COSGN00C moved the eight character user id and SEC-USR-TYPE into the communication area; the
 * verified token now carries both, and the projection has to produce the same two values.
 */
class SignedOnUserTest {

    @Test
    void theUsernameOfTheRealmBecomesTheUpperCaseKeyOfUsrsec() {
        SignedOnUser signedOn = SignedOnUser.of(TokenFixtures.token().build());

        assertThat(signedOn.userId()).isEqualTo("ADMIN001");
    }

    @Test
    void theAdminRoleIsTheTypeAOfTheAdminMenu() {
        SignedOnUser signedOn = SignedOnUser.of(TokenFixtures.token().build());

        assertThat(signedOn.userType()).isEqualTo("A");
        assertThat(signedOn.administrator()).isTrue();
    }

    @Test
    void everyOtherRoleIsTheTypeUOfTheMainMenu() {
        SignedOnUser signedOn = SignedOnUser.of(TokenFixtures.token()
                .claim("preferred_username", "user0001")
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .build());

        assertThat(signedOn.userId()).isEqualTo("USER0001");
        assertThat(signedOn.userType()).isEqualTo("U");
        assertThat(signedOn.administrator()).isFalse();
    }

    @Test
    void aTokenWithNoUsernameHasNoKeyToReadUsrsecWith() {
        Jwt token = Jwt.withTokenValue("token").header("alg", "RS256")
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .build();

        assertThat(SignedOnUser.of(token).userId()).isEmpty();
    }

    @Test
    void theFlatRolesOfTheLegacySignOnStillNameTheAdministrator() {
        SignedOnUser prefixed = SignedOnUser.of(Jwt.withTokenValue("token").header("alg", "HS256")
                .claim("preferred_username", "admin001")
                .claim("roles", List.of("ROLE_ADMIN"))
                .build());
        SignedOnUser plain = SignedOnUser.of(Jwt.withTokenValue("token").header("alg", "HS256")
                .claim("preferred_username", "admin001")
                .claim("roles", List.of("ADMIN"))
                .build());

        assertThat(prefixed.administrator()).isTrue();
        assertThat(plain.administrator()).isTrue();
    }

    @Test
    void aTokenWithNoRoleAtAllIsARegularUser() {
        SignedOnUser signedOn = SignedOnUser.of(Jwt.withTokenValue("token").header("alg", "RS256")
                .claim("preferred_username", "user0001")
                .build());

        assertThat(signedOn.userType()).isEqualTo("U");
    }

    @Test
    void aRealmAccessClaimThatHoldsNoRoleListIsARegularUser() {
        SignedOnUser signedOn = SignedOnUser.of(TokenFixtures.token()
                .claim("realm_access", Map.of("roles", "ADMIN"))
                .build());

        assertThat(signedOn.userType()).isEqualTo("U");
    }
}
