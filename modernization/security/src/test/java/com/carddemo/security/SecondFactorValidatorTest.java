package com.carddemo.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The second factor the source had no equivalent of: a token that did not come out of the enforced
 * browser flow, or out of a service account, is not enough to reach a screen.
 */
class SecondFactorValidatorTest {

    private final SecondFactorValidator validator =
            new SecondFactorValidator(TokenFixtures.properties());

    @Test
    void aTokenFromTheBrowserFlowIsAccepted() {
        assertThat(validator.validate(TokenFixtures.token().build()).hasErrors()).isFalse();
    }

    @Test
    void anExplicitOtpMethodIsAccepted() {
        assertThat(validator.validate(TokenFixtures.token()
                .claim("azp", "some-other-client")
                .claim("amr", List.of("pwd", "otp"))
                .build())
                .hasErrors())
                .isFalse();
    }

    @Test
    void theConfiguredAcrIsAccepted() {
        assertThat(validator.validate(TokenFixtures.token()
                .claim("azp", "some-other-client")
                .claim("acr", "mfa")
                .build())
                .hasErrors())
                .isFalse();
    }

    @Test
    void aServiceAccountDoesNotHaveToPresentASecondFactor() {
        assertThat(validator.validate(TokenFixtures.token()
                .claim("azp", "carddemo-authorization-svc")
                .claim("acr", "1")
                .build())
                .hasErrors())
                .isFalse();
    }

    @Test
    void aTokenFromAnUnknownClientWithNoEvidenceIsRefused() {
        assertThat(validator.validate(TokenFixtures.token()
                .claim("azp", "some-other-client")
                .build())
                .hasErrors())
                .isTrue();
    }

    @Test
    void aTokenWithNoAuthorizedPartyAndNoEvidenceIsRefused() {
        assertThat(validator.validate(Jwt.withTokenValue("token").header("alg", "RS256")
                .claim("preferred_username", "user0001")
                .build())
                .hasErrors())
                .isTrue();
    }

    @Test
    void methodsThatDoNotIncludeTheSecondFactorFallThroughToTheAcrOfTheRealm() {
        assertThat(validator.validate(TokenFixtures.token()
                .claim("azp", "some-other-client")
                .claim("amr", List.of("pwd"))
                .claim("acr", "mfa")
                .build())
                .hasErrors())
                .isFalse();
    }

    @Test
    void theRequirementCanBeTurnedOffForTheLegacyEvidenceRun() {
        TokenProperties relaxed = new TokenProperties(TokenFixtures.ISSUER, null, TokenFixtures.AUDIENCE,
                60L, false, List.of("carddemo-ui"), List.of(), List.of("otp"), List.of());

        assertThat(new SecondFactorValidator(relaxed).validate(TokenFixtures.token()
                .claim("azp", "some-other-client")
                .build())
                .hasErrors())
                .isFalse();
    }
}
