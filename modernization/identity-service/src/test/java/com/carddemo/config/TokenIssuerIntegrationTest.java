package com.carddemo.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.PostgresIntegrationTest;
import com.carddemo.persistence.entity.SecurityUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** The token that replaces the CDEMO-USER-ID and CDEMO-USER-TYPE fields of the COMMAREA. */
class TokenIssuerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private TokenIssuer tokenIssuer;
    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void anAdministratorTokenCarriesTheUserIdTypeAndAuthority() {
        SecurityUserEntity user = new SecurityUserEntity();
        user.setUserId("ADMIN001");
        user.setUserType("A");

        Jwt token = jwtDecoder.decode(tokenIssuer.issue(user, "A"));

        assertThat(token.getSubject()).isEqualTo("ADMIN001");
        assertThat(token.getClaimAsString("userType")).isEqualTo("A");
        assertThat(token.getClaimAsString("roles")).isEqualTo("ROLE_ADMIN");
        assertThat(tokenIssuer.ttlSeconds()).isEqualTo(1800L);
    }

    @Test
    void aRegularUserTokenCarriesTheUserAuthority() {
        SecurityUserEntity user = new SecurityUserEntity();
        user.setUserId("USER0001");
        user.setUserType("U");

        assertThat(jwtDecoder.decode(tokenIssuer.issue(user, "U")).getClaimAsString("roles"))
                .isEqualTo("ROLE_USER");
    }
}
