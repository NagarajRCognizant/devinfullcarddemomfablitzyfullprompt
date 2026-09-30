package com.carddemo.transaction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The positional date edit of COTRN02C and the list date of COTRN00C. */
class TransactionDatesTest {

    @Test
    void thePositionalEditAcceptsOnlyTheHyphenatedShape() {
        assertThat(TransactionDates.isKeyedFormat("2022-06-10")).isTrue();
        assertThat(TransactionDates.isKeyedFormat("2022-06-10-19.27.53.000000")).isTrue();
        assertThat(TransactionDates.isKeyedFormat("2022/06/10")).isFalse();
        assertThat(TransactionDates.isKeyedFormat("20220610")).isFalse();
        assertThat(TransactionDates.isKeyedFormat("2022-06-1")).isFalse();
        assertThat(TransactionDates.isKeyedFormat(null)).isFalse();
    }

    /** CSUTLDTC rejects a date that passes the picture but does not exist. */
    @Test
    void theDateItselfIsCheckedAfterTheEdit() {
        assertThat(TransactionDates.isRealDate("2022-06-10")).isTrue();
        assertThat(TransactionDates.isRealDate("2022-02-30")).isFalse();
        assertThat(TransactionDates.isRealDate("2022-13-01")).isFalse();
        assertThat(TransactionDates.isRealDate("2024-02-29")).isTrue();
        assertThat(TransactionDates.isRealDate("2023-02-29")).isFalse();
    }

    @Test
    void theListShowsTheTimestampDateAsMonthDayYear() {
        assertThat(TransactionDates.listDate("2022-06-10-19.27.53.000000")).isEqualTo("06/10/22");
        assertThat(TransactionDates.listDate("2022-06-10 19:27:53.000000")).isEqualTo("06/10/22");
        assertThat(TransactionDates.listDate("   ")).isEmpty();
        assertThat(TransactionDates.listDate(null)).isEmpty();
    }
}
