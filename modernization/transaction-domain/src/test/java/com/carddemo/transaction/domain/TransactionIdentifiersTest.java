package com.carddemo.transaction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The key arithmetic of COTRN02C STARTBR/READPREV plus one. */
class TransactionIdentifiersTest {

    @Test
    void anEmptyFileStartsTheCounterAtOne() {
        assertThat(TransactionIdentifiers.next(null)).isEqualTo("0000000000000001");
        assertThat(TransactionIdentifiers.next("   ")).isEqualTo("0000000000000001");
    }

    @Test
    void theNextKeyFollowsTheHighestOneOnFile() {
        assertThat(TransactionIdentifiers.next("0000000000000009"))
                .isEqualTo("0000000000000010");
        assertThat(TransactionIdentifiers.next("0000000009999999"))
                .isEqualTo("0000000010000000");
    }

    @Test
    void aKeyedIdentifierIsMovedIntoTheStoredWidth() {
        assertThat(TransactionIdentifiers.normalise("15")).isEqualTo("0000000000000015");
        assertThat(TransactionIdentifiers.normalise(" 0000000000000015 "))
                .isEqualTo("0000000000000015");
    }

    /** A MOVE into PIC 9(16) truncates on the left, which is what the source counter does. */
    @Test
    void aCounterWiderThanSixteenDigitsKeepsItsLowOrderDigits() {
        assertThat(TransactionIdentifiers.next("9999999999999999"))
                .isEqualTo("0000000000000000");
    }
}
