package com.carddemo.cobol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CobdatftDateFormatterTest {

    private static final String EMPTY_AREA = " ".repeat(20);

    @Test
    void typeTwoInAndOutStripsTheSeparators() {
        CobdatftDateFormatter.Result result =
                CobdatftDateFormatter.convert('2', '2', "2020-01-31", EMPTY_AREA);

        assertThat(result.outputDate()).startsWith("20200131");
        assertThat(result.outputDate()).hasSize(20);
        assertThat(result.errorMessage()).isEmpty();
    }

    @Test
    void typeOneInAndTypeOneOutInsertsTheSeparators() {
        CobdatftDateFormatter.Result result =
                CobdatftDateFormatter.convert('1', '1', "20200131", EMPTY_AREA);

        assertThat(result.outputDate()).startsWith("2020-01-31");
        assertThat(result.errorMessage()).isEmpty();
    }

    @Test
    void mismatchedTypesLeaveTheOutputAreaUntouched() {
        String previous = "PREVIOUS CONTENT    ";

        assertThat(CobdatftDateFormatter.convert('2', '1', "2020-01-31", previous))
                .isEqualTo(new CobdatftDateFormatter.Result(previous, CobdatftDateFormatter.INVALID_INPUT));
        assertThat(CobdatftDateFormatter.convert('1', '2', "20200131", previous))
                .isEqualTo(new CobdatftDateFormatter.Result(previous, CobdatftDateFormatter.INVALID_INPUT));
        assertThat(CobdatftDateFormatter.convert('1', '1', "2020-01-31", previous))
                .isEqualTo(new CobdatftDateFormatter.Result(previous, CobdatftDateFormatter.INVALID_INPUT));
        assertThat(CobdatftDateFormatter.convert('3', '2', "20200131", previous))
                .isEqualTo(new CobdatftDateFormatter.Result(previous, CobdatftDateFormatter.INVALID_INPUT));
    }

    @Test
    void onlyTheFrontOfTheOutputAreaIsOverlaid() {
        CobdatftDateFormatter.Result result =
                CobdatftDateFormatter.convert('2', '2', "2020-01-31", "XXXXXXXXXXXXXXXXXXXX");

        assertThat(result.outputDate()).isEqualTo("20200131XXXXXXXXXXXX");
    }
}
