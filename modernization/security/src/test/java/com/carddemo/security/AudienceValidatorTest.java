package com.carddemo.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A token minted for one service must not be replayable at another, which is what the audience of the
 * realm's mapper is for.
 */
class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator(TokenFixtures.AUDIENCE);

    @Test
    void theAudienceOfThisServiceIsAccepted() {
        assertThat(validator.validate(TokenFixtures.token().build()).hasErrors()).isFalse();
    }

    @Test
    void aTokenForAnotherServiceIsRefused() {
        assertThat(validator.validate(TokenFixtures.token()
                .audience(List.of("carddemo-authorization-service"))
                .build())
                .hasErrors())
                .isTrue();
    }

    @Test
    void aTokenWithNoAudienceIsRefused() {
        assertThat(validator.validate(TokenFixtures.token().audience(List.of()).build())
                .hasErrors())
                .isTrue();
    }
}
