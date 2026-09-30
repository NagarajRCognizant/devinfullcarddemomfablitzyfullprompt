package com.carddemo.transaction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** The PIC +99999999.99 amount edit of COTRN00C and COTRN02C. */
class TransactionAmountFormatTest {

    @Test
    void anAmountIsEditedWithItsSignAndEightIntegerDigits() {
        assertThat(TransactionAmountFormat.format(new BigDecimal("504.77")))
                .isEqualTo("+00000504.77");
        assertThat(TransactionAmountFormat.format(new BigDecimal("-919.00")))
                .isEqualTo("-00000919.00");
        assertThat(TransactionAmountFormat.format(BigDecimal.ZERO)).isEqualTo("+00000000.00");
    }

    @Test
    void anAbsentAmountEditsToSpaces() {
        assertThat(TransactionAmountFormat.format(null)).isEmpty();
    }

    @Test
    void onlyTheExactPictureIsAccepted() {
        assertThat(TransactionAmountFormat.isKeyedFormat("-00000919.00")).isTrue();
        assertThat(TransactionAmountFormat.isKeyedFormat("+00000504.77")).isTrue();
        assertThat(TransactionAmountFormat.isKeyedFormat("504.77")).isFalse();
        assertThat(TransactionAmountFormat.isKeyedFormat("+0000504.77")).isFalse();
        assertThat(TransactionAmountFormat.isKeyedFormat("+00000504,77")).isFalse();
        assertThat(TransactionAmountFormat.isKeyedFormat("+0000050A.77")).isFalse();
        assertThat(TransactionAmountFormat.isKeyedFormat(null)).isFalse();
    }

    @Test
    void theKeyedAmountKeepsItsScaleAndSign() {
        assertThat(TransactionAmountFormat.parse("-00000919.00"))
                .isEqualByComparingTo(new BigDecimal("-919.00"));
        assertThat(TransactionAmountFormat.parse("+00000504.77"))
                .isEqualByComparingTo(new BigDecimal("504.77"));
    }
}
