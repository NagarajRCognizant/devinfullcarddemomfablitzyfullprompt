package com.carddemo.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The ten entries of WS-DECLINE-REASON-TABLE and the {@code AT END} branch of the SEARCH ALL in
 * COPAUS1C, asserted literally because the text appears on the detail screen.
 */
class DeclineReasonTest {

    @ParameterizedTest
    @CsvSource({
            "0000,0000-APPROVED",
            "3100,3100-INVALID CARD",
            "4100,4100-INSUFFICNT FUND",
            "4200,4200-CARD NOT ACTIVE",
            "4300,4300-ACCOUNT CLOSED",
            "4400,4400-EXCED DAILY LMT",
            "5100,5100-CARD FRAUD",
            "5200,5200-MERCHANT FRAUD",
            "5300,5300-LOST CARD",
            "9000,9000-UNKNOWN"
    })
    void eachTableEntryIsShownWithItsSourceDescription(String code, String expected) {
        assertThat(DeclineReason.describe(code)).isEqualTo(expected);
    }

    @Test
    void aReasonTheTableDoesNotHoldIsShownAsTheErrorEntry() {
        assertThat(DeclineReason.describe("7777")).isEqualTo("9999-ERROR");
        assertThat(DeclineReason.describe("")).isEqualTo("9999-ERROR");
        assertThat(DeclineReason.describe(null)).isEqualTo("9999-ERROR");
    }

    /** The reason codes the decision flags produce all resolve in the table. */
    @Test
    void everyDecisionFlagResolvesToATableEntry() {
        for (DeclineReasonFlag flag : DeclineReasonFlag.values()) {
            assertThat(DeclineReason.describe(flag.reasonCode())).doesNotContain("9999-ERROR");
        }
    }
}
