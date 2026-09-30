package com.carddemo.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a service accepts when a deployment configures only the issuer, which is the usual case: the
 * second factor is required, the SPA is the browser client and the converted CP00 trigger is the
 * service client.
 */
class TokenPropertiesTest {

    @Test
    void aDeploymentThatConfiguresOnlyTheIssuerGetsTheSafeDefaults() {
        TokenProperties properties = new TokenProperties(TokenFixtures.ISSUER, null, "carddemo-gateway",
                null, null, null, null, null, null);

        assertThat(properties.clockSkew()).isEqualTo(60L);
        assertThat(properties.requireSecondFactor()).isTrue();
        assertThat(properties.browserClientIds()).containsExactly("carddemo-ui");
        assertThat(properties.serviceClientIds()).containsExactly("carddemo-authorization-svc");
        assertThat(properties.amrValues()).containsExactly("otp", "mfa");
        assertThat(properties.acrValues()).isEmpty();
    }

    @Test
    void aConfiguredValueIsKept() {
        TokenProperties properties = new TokenProperties(TokenFixtures.ISSUER, null, "carddemo-gateway",
                5L, false, List.of("other-ui"), List.of("other-svc"), List.of("hwk"),
                List.of("gold"));

        assertThat(properties.clockSkew()).isEqualTo(5L);
        assertThat(properties.requireSecondFactor()).isFalse();
        assertThat(properties.browserClientIds()).containsExactly("other-ui");
        assertThat(properties.serviceClientIds()).containsExactly("other-svc");
        assertThat(properties.amrValues()).containsExactly("hwk");
        assertThat(properties.acrValues()).containsExactly("gold");
    }
}
