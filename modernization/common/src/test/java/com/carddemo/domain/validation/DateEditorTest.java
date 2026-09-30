package com.carddemo.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Source-derived expectations for the date edit paragraphs of app/cpy/CSUTLDPY.cpy. */
class DateEditorTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2024-06-15T00:00:00Z"), ZoneOffset.UTC);

    private DateEditor editor;
    private EditContext ctx;

    @BeforeEach
    void setUp() {
        editor = new DateEditor(new DateValidationService(), FIXED);
        ctx = new EditContext();
    }

    private DateEditResult edit(String year, String month, String day) {
        return editor.editDate(ctx, ScreenField.OPEN_YEAR, ScreenField.OPEN_MONTH, ScreenField.OPEN_DAY,
                "Open Date", year, month, day);
    }

    @Test
    void aValidDateIsConverted() {
        DateEditResult result = edit("2020", "02", "29");

        assertThat(result.valid()).isTrue();
        assertThat(result.date()).isEqualTo(LocalDate.of(2020, 2, 29));
        assertThat(ctx.isInputError()).isFalse();
    }

    @Test
    void everyPartIsReportedInOneCall() {
        DateEditResult result = edit("", "", "");

        assertThat(result.valid()).isFalse();
        assertThat(ctx.flag(ScreenField.OPEN_YEAR)).isEqualTo(FieldFlag.BLANK);
        assertThat(ctx.flag(ScreenField.OPEN_MONTH)).isEqualTo(FieldFlag.BLANK);
        assertThat(ctx.flag(ScreenField.OPEN_DAY)).isEqualTo(FieldFlag.BLANK);
        assertThat(ctx.getReturnMessage()).isEqualTo("Open Date : Year must be supplied.");
    }

    @Test
    void yearMustBeFourDigitsInThe19thOr20thCentury() {
        assertThat(edit("20A0", "01", "01").valid()).isFalse();
        assertThat(ctx.getReturnMessage()).isEqualTo("Open Date must be 4 digit number.");

        EditContext century = new EditContext();
        editor.editDate(century, ScreenField.OPEN_YEAR, ScreenField.OPEN_MONTH, ScreenField.OPEN_DAY,
                "Open Date", "1899", "01", "01");
        assertThat(century.getReturnMessage()).isEqualTo("Open Date : Century is not valid.");
    }

    @Test
    void monthAndDayRanges() {
        assertThat(edit("2020", "13", "01").valid()).isFalse();
        assertThat(ctx.getReturnMessage()).isEqualTo("Open Date: Month must be a number between 1 and 12.");

        EditContext nonNumericMonth = new EditContext();
        editor.editDate(nonNumericMonth, ScreenField.OPEN_YEAR, ScreenField.OPEN_MONTH,
                ScreenField.OPEN_DAY, "Open Date", "2020", "1X", "01");
        assertThat(nonNumericMonth.flag(ScreenField.OPEN_MONTH)).isEqualTo(FieldFlag.NOT_OK);

        EditContext day = new EditContext();
        editor.editDate(day, ScreenField.OPEN_YEAR, ScreenField.OPEN_MONTH, ScreenField.OPEN_DAY,
                "Open Date", "2020", "01", "32");
        assertThat(day.getReturnMessage()).isEqualTo("Open Date:day must be a number between 1 and 31.");

        EditContext blankDay = new EditContext();
        editor.editDate(blankDay, ScreenField.OPEN_YEAR, ScreenField.OPEN_MONTH, ScreenField.OPEN_DAY,
                "Open Date", "2020", "01", " ");
        assertThat(blankDay.getReturnMessage()).isEqualTo("Open Date : Day must be supplied.");

        EditContext nonNumericDay = new EditContext();
        editor.editDate(nonNumericDay, ScreenField.OPEN_YEAR, ScreenField.OPEN_MONTH, ScreenField.OPEN_DAY,
                "Open Date", "2020", "01", "X1");
        assertThat(nonNumericDay.flag(ScreenField.OPEN_DAY)).isEqualTo(FieldFlag.NOT_OK);

        EditContext blankMonth = new EditContext();
        editor.editDate(blankMonth, ScreenField.OPEN_YEAR, ScreenField.OPEN_MONTH, ScreenField.OPEN_DAY,
                "Open Date", "2020", " ", "01");
        assertThat(blankMonth.getReturnMessage()).isEqualTo("Open Date : Month must be supplied.");
    }

    @Test
    void dayMonthCombinations() {
        assertThat(edit("2020", "04", "31").valid()).isFalse();
        assertThat(ctx.getReturnMessage()).isEqualTo("Open Date:Cannot have 31 days in this month.");

        EditContext february30 = new EditContext();
        editor.editDate(february30, ScreenField.OPEN_YEAR, ScreenField.OPEN_MONTH, ScreenField.OPEN_DAY,
                "Open Date", "2020", "02", "30");
        assertThat(february30.getReturnMessage()).isEqualTo("Open Date:Cannot have 30 days in this month.");

        EditContext notALeapYear = new EditContext();
        editor.editDate(notALeapYear, ScreenField.OPEN_YEAR, ScreenField.OPEN_MONTH, ScreenField.OPEN_DAY,
                "Open Date", "2021", "02", "29");
        assertThat(notALeapYear.getReturnMessage())
                .isEqualTo("Open Date:Not a leap year.Cannot have 29 days in this month.");
        assertThat(notALeapYear.flag(ScreenField.OPEN_YEAR)).isEqualTo(FieldFlag.NOT_OK);
    }

    @Test
    void centuryYearsUseTheFourHundredYearRule() {
        assertThat(edit("2000", "02", "29").valid()).isTrue();

        EditContext nineteenHundred = new EditContext();
        DateEditResult result = editor.editDate(nineteenHundred, ScreenField.OPEN_YEAR,
                ScreenField.OPEN_MONTH, ScreenField.OPEN_DAY, "Open Date", "1900", "02", "29");
        assertThat(result.valid()).isFalse();
    }

    @Test
    void dateOfBirthCannotBeTodayOrLater() {
        editor.editDateOfBirth(ctx, ScreenField.DOB_YEAR, ScreenField.DOB_MONTH, ScreenField.DOB_DAY,
                "Date of Birth", LocalDate.of(2024, 6, 15));

        assertThat(ctx.getReturnMessage()).isEqualTo("Date of Birth:cannot be in the future ");
        assertThat(ctx.flag(ScreenField.DOB_DAY)).isEqualTo(FieldFlag.NOT_OK);

        EditContext past = new EditContext();
        editor.editDateOfBirth(past, ScreenField.DOB_YEAR, ScreenField.DOB_MONTH, ScreenField.DOB_DAY,
                "Date of Birth", LocalDate.of(2024, 6, 14));
        assertThat(past.isInputError()).isFalse();
    }
}
