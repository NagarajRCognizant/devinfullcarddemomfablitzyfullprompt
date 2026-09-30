package com.carddemo.config;

import com.carddemo.persistence.entity.SecurityUserEntity;
import com.carddemo.service.UserTypes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Component;

/**
 * Issues the access token of a successful sign-on.
 *
 * <p>The claims carry exactly what the COMMAREA carried: the user id and the user type, plus the
 * single authority derived from the type, so the services can apply the COMEN01C admin-only rule
 * without reading USRSEC again.
 *
 * <p>Only present in {@code carddemo.security.mode=legacy}; the realm issues the token otherwise.
 */
@Component
@ConditionalOnProperty(prefix = "carddemo.security", name = "mode", havingValue = "legacy")
public class TokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public TokenIssuer(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public String issue(SecurityUserEntity user, String userType) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttlMinutes(), ChronoUnit.MINUTES))
                .subject(UserTypes.normaliseId(user.getUserId()))
                .claim("userType", userType)
                .claim("roles", UserTypes.isAdmin(userType) ? "ROLE_ADMIN" : "ROLE_USER")
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public long ttlSeconds() {
        return properties.ttlMinutes() * 60;
    }
}
