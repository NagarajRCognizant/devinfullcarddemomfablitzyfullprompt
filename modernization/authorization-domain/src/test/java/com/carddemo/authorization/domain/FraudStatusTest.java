package com.carddemo.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** MARK-AUTH-FRAUD of COPAUS1C: the only transition is confirmed to removed, everything else to confirmed. */
class FraudStatusTest {

    @Test
    void anUnreportedAuthorizationBecomesConfirmedFraud() {
        assertThat(FraudStatus.toggle(FraudStatus.NONE)).isEqualTo(FraudStatus.CONFIRMED);
        assertThat(FraudStatus.toggle(null)).isEqualTo(FraudStatus.CONFIRMED);
        assertThat(FraudStatus.toggle("")).isEqualTo(FraudStatus.CONFIRMED);
    }

    @Test
    void aConfirmedFraudIsRemoved() {
        assertThat(FraudStatus.toggle(FraudStatus.CONFIRMED)).isEqualTo(FraudStatus.REMOVED);
    }

    /** A removed report is reported again: the source ELSE covers every value that is not F. */
    @Test
    void aRemovedReportIsConfirmedAgain() {
        assertThat(FraudStatus.toggle(FraudStatus.REMOVED)).isEqualTo(FraudStatus.CONFIRMED);
    }
}
