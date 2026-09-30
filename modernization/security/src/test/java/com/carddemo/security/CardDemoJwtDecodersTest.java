package com.carddemo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * The four checks every service applies to a token, and the start-up behaviour of the decoder that
 * applies them.
 */
class CardDemoJwtDecodersTest {

    private final TokenProperties properties = TokenFixtures.properties();

    @Test
    void aTokenOfTheRealmForThisServicePassesEveryCheck() {
        assertThat(CardDemoJwtDecoders.validator(properties)
                .validate(TokenFixtures.token().build())
                .hasErrors())
                .isFalse();
    }

    @Test
    void aTokenOfAnotherIssuerIsRefused() {
        assertThat(CardDemoJwtDecoders.validator(properties)
                .validate(TokenFixtures.token().issuer("http://evil.example/realms/carddemo").build())
                .hasErrors())
                .isTrue();
    }

    @Test
    void anExpiredTokenIsRefusedBeyondTheAllowedSkew() {
        Instant longGone = Instant.now().minus(10, ChronoUnit.MINUTES);

        assertThat(CardDemoJwtDecoders.validator(properties)
                .validate(TokenFixtures.token()
                        .issuedAt(longGone.minus(5, ChronoUnit.MINUTES))
                        .expiresAt(longGone)
                        .build())
                .hasErrors())
                .isTrue();
    }

    @Test
    void aTokenThatExpiredWithinTheAllowedSkewIsStillAccepted() {
        Instant justGone = Instant.now().minus(5, ChronoUnit.SECONDS);

        assertThat(CardDemoJwtDecoders.validator(properties)
                .validate(TokenFixtures.token()
                        .issuedAt(justGone.minus(5, ChronoUnit.MINUTES))
                        .expiresAt(justGone)
                        .build())
                .hasErrors())
                .isFalse();
    }

    @Test
    void aTokenWithoutTheSecondFactorIsRefused() {
        assertThat(CardDemoJwtDecoders.validator(properties)
                .validate(TokenFixtures.token().claim("azp", "unknown").build())
                .hasErrors())
                .isTrue();
    }

    @Test
    void theIssuerIsNotReadUntilTheFirstTokenArrives() {
        // Built against a port nothing listens on: constructing the decoder has to succeed anyway, so
        // a service is healthy before Keycloak is, and only the first request pays for the metadata.
        TokenProperties unreachable = new TokenProperties("http://127.0.0.1:1/realms/carddemo", null,
                TokenFixtures.AUDIENCE, 60L, true, List.of("carddemo-ui"), List.of(),
                List.of("otp"), List.of());

        // Constructing it has to succeed; only the decode then fails, on the unreachable metadata.
        JwtDecoder decoder = CardDemoJwtDecoders.servlet(unreachable);

        assertThatThrownBy(() -> decoder.decode("not.a.token")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void theKeysAreFetchedWhereTheServiceCanReachTheRealmAndNotWhereTheTokensSayItIs() {
        // A container stack has two addresses for one realm: the published one the browser is
        // redirected to, which is what the tokens carry, and the internal one the service can dial.
        // Configuring the second must not relax the check on the first.
        TokenProperties split = new TokenProperties("https://sso.example/realms/carddemo",
                "http://keycloak:8080/realms/carddemo/protocol/openid-connect/certs",
                TokenFixtures.AUDIENCE, 60L, true, List.of("carddemo-ui"), List.of(),
                List.of("otp"), List.of());

        assertThat(CardDemoJwtDecoders.servlet(split)).isNotNull();
        assertThat(CardDemoJwtDecoders.validator(split)
                .validate(TokenFixtures.token().issuer("https://sso.example/realms/carddemo").build())
                .hasErrors())
                .isFalse();
        assertThat(CardDemoJwtDecoders.validator(split)
                .validate(TokenFixtures.token()
                        .issuer("http://keycloak:8080/realms/carddemo")
                        .build())
                .hasErrors())
                .isTrue();
    }

    @Test
    void anAlgNoneTokenIsRefusedBeforeAnyClaimIsRead() {
        // The realm signs with RS256, so an unsigned token has no algorithm the decoder will accept.
        assertThatThrownBy(() -> realmKeyDecoder()
                .decode("eyJhbGciOiJub25lIn0.eyJpc3MiOiJodHRwOi8vZXZpbCJ9."))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void anHs256TokenIsRefusedTheWayTheOldSelfIssuedOneNowIs() {
        // Signed with the shared secret the identity service used to hold: nothing accepts it now, and
        // the algorithm is refused before the JWKS of the realm is even consulted.
        String hs256 = "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODgvcmVhbG1zL2Nhcm"
                + "RkZW1vIn0.7pMh3Cj0mYtQOxSCS_1AoDGrfmyfsMk0-mUvfP0dtiY";

        assertThatThrownBy(() -> realmKeyDecoder().decode(hs256))
                .isInstanceOf(JwtException.class);
    }

    /** A decoder that will only accept the RS256 keys of a realm, pointed at no reachable JWKS. */
    private JwtDecoder realmKeyDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri("http://127.0.0.1:1/realms/carddemo/protocol/openid-connect/certs")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(CardDemoJwtDecoders.validator(properties));
        return decoder;
    }
}
