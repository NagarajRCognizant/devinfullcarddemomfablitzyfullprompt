package com.carddemo.cobol;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CobolNumericTest {

    @ParameterizedTest
    @CsvSource({
            "1234.56,1234.56",
            "'-1234.56',-1234.56",
            "'+1234.56',1234.56",
            "1234.56-,-1234.56",
            "1234.56+,1234.56",
            "(1234.56),-1234.56",
            "'$1,234.56',1234.56",
            "'1,234',1234.00",
            "0,0.00",
            "12.567,12.56"})
    void numvalCConvertsKeyedAmounts(String keyed, String expected) {
        assertThat(CobolNumeric.parseNumericWithCurrency(keyed)).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12A", "1.2.3", ".", "-", "12345678901.00", "abc"})
    void testNumvalCRejectsInvalidAmounts(String keyed) {
        assertThat(CobolNumeric.isValidNumericWithCurrency(keyed)).isFalse();
        assertThat(CobolNumeric.parseNumericWithCurrency(keyed)).isNull();
    }

    @Test
    void blankIsNotANumber() {
        assertThat(CobolNumeric.parseNumericWithCurrency("   ")).isNull();
        assertThat(CobolNumeric.parseNumericWithCurrency(null)).isNull();
    }

    @Test
    void tenIntegerDigitsAreTheLimitOfS9x10V99() {
        assertThat(CobolNumeric.parseNumericWithCurrency("9999999999.99"))
                .isEqualByComparingTo("9999999999.99");
        assertThat(CobolNumeric.parseNumericWithCurrency("10000000000.00")).isNull();
    }

    @Test
    void formatKeepsTwoDecimalPlaces() {
        assertThat(CobolNumeric.format(new BigDecimal("-25.00"))).isEqualTo("-25.00");
        assertThat(CobolNumeric.format(null)).isEmpty();
    }

    @Test
    void editedFormatMatchesThePlusZzzZzzZzzMask() {
        assertThat(CobolNumeric.formatEdited(new BigDecimal("1025.00")))
                .isEqualTo("+" + " ".repeat(6) + "1,025.00");
        assertThat(CobolNumeric.formatEdited(new BigDecimal("-1025.00")))
                .isEqualTo("-" + " ".repeat(6) + "1,025.00");
        assertThat(CobolNumeric.formatEdited(null)).isEqualTo("+          0.00");
        // The mask holds nine digits, so an eleventh digit of S9(10)V99 is truncated on the MOVE.
        assertThat(CobolNumeric.formatEdited(new BigDecimal("1234567890.12")))
                .isEqualTo("+234,567,890.12")
                .hasSize(15);
    }

    @Test
    void numericComparisonIgnoresScale() {
        assertThat(CobolNumeric.equalsNumeric(new BigDecimal("0.00"), BigDecimal.ZERO)).isTrue();
        assertThat(CobolNumeric.equalsNumeric(new BigDecimal("1.00"), null)).isFalse();
        assertThat(CobolNumeric.equalsNumeric(null, null)).isTrue();
        assertThat(CobolNumeric.scaled(new BigDecimal("1"))).isEqualTo(new BigDecimal("1.00"));
        assertThat(CobolNumeric.scaled(null)).isNull();
    }
}
