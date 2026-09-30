package com.carddemo.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;

/** Tokens shaped like the ones the carddemo realm issues, built without a signature. */
final class TokenFixtures {

    static final String ISSUER = "http://localhost:8088/realms/carddemo";
    static final String AUDIENCE = "carddemo-account-service";

    private TokenFixtures() {
    }

    static Jwt.Builder token() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plus(5, ChronoUnit.MINUTES))
                .claim("preferred_username", "admin001")
                .claim("azp", "carddemo-ui")
                .claim("acr", "1")
                .claim("realm_access", Map.of("roles", List.of("ADMIN")));
    }

    static TokenProperties properties() {
        return new TokenProperties(ISSUER, null, AUDIENCE, 60L, true, List.of("carddemo-ui"),
                List.of("carddemo-authorization-svc"), List.of("otp", "mfa"), List.of("mfa"));
    }
}
