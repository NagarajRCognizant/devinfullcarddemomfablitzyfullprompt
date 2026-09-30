package com.carddemo.batch.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Paragraph 1000-INITIALIZE: each field of the SYSIN parameter card is defaulted when it is blank,
 * zero or low values, so a job submitted with an unusable card still runs with the frequencies the
 * source used rather than checkpointing on every root or never.
 */
class AuthPurgePropertiesTest {

    @Test
    void anUnusableCheckpointFrequencyFallsBackToTheDefaultsOfTheSource() {
        AuthPurgeProperties properties = new AuthPurgeProperties();

        properties.setCheckpointFrequency(0);
        properties.setCheckpointDisplayFrequency(0);

        assertThat(properties.getCheckpointFrequency()).isEqualTo(5);
        assertThat(properties.getCheckpointDisplayFrequency()).isEqualTo(10);
    }

    @Test
    void aSuppliedFrequencyIsUsedAsGiven() {
        AuthPurgeProperties properties = new AuthPurgeProperties();

        properties.setCheckpointFrequency(25);
        properties.setCheckpointDisplayFrequency(4);
        properties.setExpiryDays(10);

        assertThat(properties.getCheckpointFrequency()).isEqualTo(25);
        assertThat(properties.getCheckpointDisplayFrequency()).isEqualTo(4);
        assertThat(properties.getExpiryDays()).isEqualTo(10);
        assertThat(properties.isDebug()).isFalse();
        assertThat(properties.isPersistSummaryAdjustments()).isTrue();
    }
}
