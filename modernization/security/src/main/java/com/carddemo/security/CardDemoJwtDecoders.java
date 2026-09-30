package com.carddemo.security;

import java.time.Duration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Builds the one decoder every service uses: the realm signing keys fetched from the JWKS of the
 * issuer, and the same three checks on top of it.
 *
 * <p>The keys come from the JWKS of the issuer unless a deployment reaches the realm on an address the
 * tokens do not name, in which case that address is configured separately and the issuer of the token is
 * still checked against the one the realm puts in {@code iss}.
 *
 * <p>Nothing here knows a signing secret. A token signed with a key the realm does not publish - the
 * HS256 token the identity service used to mint, or an {@code alg: none} token - has no matching JWKS
 * entry and is refused before any claim is read.
 */
public final class CardDemoJwtDecoders {

    private CardDemoJwtDecoders() {
    }

    /**
     * The metadata of the issuer is read on the first token rather than at startup, so a service does
     * not have to wait for Keycloak to come up before it can report itself healthy.
     */
    public static JwtDecoder servlet(TokenProperties properties) {
        return new LazyJwtDecoder(() -> {
            NimbusJwtDecoder decoder = properties.jwkSetUri() == null
                    || properties.jwkSetUri().isBlank()
                    ? NimbusJwtDecoder.withIssuerLocation(properties.issuerUri()).build()
                    : NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
            decoder.setJwtValidator(validator(properties));
            return decoder;
        });
    }

    public static ReactiveJwtDecoder reactive(TokenProperties properties) {
        return new LazyReactiveJwtDecoder(() -> {
            NimbusReactiveJwtDecoder decoder = properties.jwkSetUri() == null
                    || properties.jwkSetUri().isBlank()
                    ? NimbusReactiveJwtDecoder.withIssuerLocation(properties.issuerUri()).build()
                    : NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
            decoder.setJwtValidator(validator(properties));
            return decoder;
        });
    }

    static OAuth2TokenValidator<Jwt> validator(TokenProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(properties.issuerUri()),
                new JwtTimestampValidator(Duration.ofSeconds(properties.clockSkew())),
                new AudienceValidator(properties.audience()),
                new SecondFactorValidator(properties));
    }
}
