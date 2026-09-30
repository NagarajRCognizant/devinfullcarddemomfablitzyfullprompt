package com.carddemo.cobol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CobolTextTest {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void blankCoversLowValuesAndSpaces(String value) {
        assertThat(CobolText.isBlank(value)).isTrue();
    }

    @Test
    void nullIsBlank() {
        assertThat(CobolText.isBlank(null)).isTrue();
        assertThat(CobolText.isBlank("A")).isFalse();
    }

    @Test
    void asteriskMeansNothingSupplied() {
        assertThat(CobolText.normaliseAsterisk(" * ")).isEmpty();
        assertThat(CobolText.normaliseAsterisk("*A")).isEqualTo("*A");
        assertThat(CobolText.normaliseAsterisk(null)).isNull();
    }

    @Test
    void trimAndUpperTrimHandleNull() {
        assertThat(CobolText.trim(null)).isEmpty();
        assertThat(CobolText.trim("  ab  ")).isEqualTo("ab");
        assertThat(CobolText.upperTrim(" ab ")).isEqualTo("AB");
    }

    @ParameterizedTest
    @CsvSource({"12345,true", "0000,true", "12a,false", "'',false", "' 12',false", "12.3,false"})
    void isNumericMatchesDisplayFieldTest(String value, boolean expected) {
        assertThat(CobolText.isNumeric(value)).isEqualTo(expected);
    }

    @Test
    void isNumericRejectsNull() {
        assertThat(CobolText.isNumeric(null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"AB cd,true", "AB1,false", "A-B,false"})
    void alphabeticEdit(String value, boolean expected) {
        assertThat(CobolText.isAlphabeticOrSpace(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"AB cd,true", "AB1,true", "A-B,false"})
    void alphanumericEdit(String value, boolean expected) {
        assertThat(CobolText.isAlphanumericOrSpace(value)).isEqualTo(expected);
    }

    @Test
    void alphaEditsRejectNull() {
        assertThat(CobolText.isAlphabeticOrSpace(null)).isFalse();
        assertThat(CobolText.isAlphanumericOrSpace(null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"000,true", "' 0 ',true", "001,false", "'',false", "'   ',false"})
    void allZeroesIsTheEqualZerosTest(String value, boolean expected) {
        assertThat(CobolText.isAllZeroes(value)).isEqualTo(expected);
    }

    @Test
    void fixedLengthMoves() {
        assertThat(CobolText.padRight("AB", 4)).isEqualTo("AB  ");
        assertThat(CobolText.padRight("ABCDE", 4)).isEqualTo("ABCD");
        assertThat(CobolText.padRight(null, 2)).isEqualTo("  ");
        assertThat(CobolText.padLeft("7", 3)).isEqualTo("  7");
        assertThat(CobolText.padLeft("1234", 3)).isEqualTo("1234");
        assertThat(CobolText.padLeft(null, 2)).isEqualTo("  ");
        assertThat(CobolText.padLeftZero("7", 3)).isEqualTo("007");
        assertThat(CobolText.padLeftZero("123456", 5)).isEqualTo("23456");
    }
}
