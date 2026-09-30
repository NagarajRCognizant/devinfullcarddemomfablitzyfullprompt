package com.carddemo.domain.validation;

import com.carddemo.cobol.CobolText;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Port of the shared date edit paragraphs in app/cpy/CSUTLDPY.cpy.
 *
 * <p>The COBOL paragraphs fall through each other (year, month, day, cross field combination,
 * Language Environment check), and each individual failure only leaves its own paragraph, so
 * the year, month and day flags are all reported after a single call. That control flow, the
 * flag combinations and the message texts are reproduced exactly.
 */
@Component
public class DateEditor {

    private final DateValidationService dateValidationService;
    private final Clock clock;

    public DateEditor(DateValidationService dateValidationService, Clock clock) {
        this.dateValidationService = dateValidationService;
        this.clock = clock;
    }

    /**
     * EDIT-DATE-CCYYMMDD.
     *
     * @return the edited date parts and whether all of them passed
     */
    public DateEditResult editDate(EditContext ctx, String yearField, String monthField, String dayField,
                                   String label, String year, String month, String day) {
        boolean yearOk = editYear(ctx, yearField, label, year);
        boolean monthOk = editMonth(ctx, monthField, label, month);
        boolean dayOk = editDay(ctx, dayField, label, day);

        if (!(yearOk && monthOk && dayOk)) {
            return new DateEditResult(false, null);
        }

        int monthValue = Integer.parseInt(CobolText.trim(month));
        int dayValue = Integer.parseInt(CobolText.trim(day));
        int yearValue = Integer.parseInt(CobolText.trim(year));

        // EDIT-DAY-MONTH-YEAR
        if (!isThirtyOneDayMonth(monthValue) && dayValue == 31) {
            reject(ctx, label + ":Cannot have 31 days in this month.", dayField, FieldFlag.NOT_OK,
                    monthField, FieldFlag.NOT_OK);
            return new DateEditResult(false, null);
        }
        if (monthValue == 2 && dayValue == 30) {
            reject(ctx, label + ":Cannot have 30 days in this month.", dayField, FieldFlag.NOT_OK,
                    monthField, FieldFlag.NOT_OK);
            return new DateEditResult(false, null);
        }
        if (monthValue == 2 && dayValue == 29 && !isLeapYear(yearValue)) {
            ctx.reject(dayField, FieldFlag.NOT_OK,
                    label + ":Not a leap year.Cannot have 29 days in this month.");
            ctx.reject(monthField, FieldFlag.NOT_OK, null);
            ctx.reject(yearField, FieldFlag.NOT_OK, null);
            return new DateEditResult(false, null);
        }

        // EDIT-DATE-LE
        String ccyymmdd = String.format("%04d%02d%02d", yearValue, monthValue, dayValue);
        DateValidationResult result = dateValidationService.validateYyyyMmDd(ccyymmdd);
        if (!result.isValid()) {
            ctx.flagInputError();
            ctx.message(label + " validation error Sev code: " + result.severityCode()
                    + " Message code: " + result.messageCode());
            // AS-IS: CSUTLDPY falls through the paragraph exit and resets the three date flags to
            // valid after an LE failure, so the message is shown without highlighting the fields.
            ctx.accept(yearField);
            ctx.accept(monthField);
            ctx.accept(dayField);
            return new DateEditResult(false, null);
        }
        return new DateEditResult(true, LocalDate.of(yearValue, monthValue, dayValue));
    }

    /**
     * EDIT-DATE-OF-BIRTH - the date of birth must be strictly before today, so a date of birth
     * of today is rejected as being in the future.
     */
    public void editDateOfBirth(EditContext ctx, String yearField, String monthField, String dayField,
                                String label, LocalDate dateOfBirth) {
        LocalDate today = LocalDate.now(clock);
        if (today.isAfter(dateOfBirth)) {
            return;
        }
        ctx.reject(dayField, FieldFlag.NOT_OK, label + ":cannot be in the future ");
        ctx.reject(monthField, FieldFlag.NOT_OK, null);
        ctx.reject(yearField, FieldFlag.NOT_OK, null);
    }

    /** EDIT-YEAR-CCYY - supplied, four digits and a 19xx or 20xx century. */
    private boolean editYear(EditContext ctx, String field, String label, String year) {
        if (CobolText.isBlank(year)) {
            ctx.reject(field, FieldFlag.BLANK, label + " : Year must be supplied.");
            return false;
        }
        String value = CobolText.trim(year);
        if (!CobolText.isNumeric(value) || value.length() != 4) {
            ctx.reject(field, FieldFlag.NOT_OK, label + " must be 4 digit number.");
            return false;
        }
        int century = Integer.parseInt(value.substring(0, 2));
        if (century != 19 && century != 20) {
            ctx.reject(field, FieldFlag.NOT_OK, label + " : Century is not valid.");
            return false;
        }
        ctx.accept(field);
        return true;
    }

    /** EDIT-MONTH. */
    private boolean editMonth(EditContext ctx, String field, String label, String month) {
        if (CobolText.isBlank(month)) {
            ctx.reject(field, FieldFlag.BLANK, label + " : Month must be supplied.");
            return false;
        }
        String value = CobolText.trim(month);
        if (!CobolText.isNumeric(value)) {
            ctx.reject(field, FieldFlag.NOT_OK, label + ": Month must be a number between 1 and 12.");
            return false;
        }
        int monthValue = Integer.parseInt(value);
        if (monthValue < 1 || monthValue > 12) {
            ctx.reject(field, FieldFlag.NOT_OK, label + ": Month must be a number between 1 and 12.");
            return false;
        }
        ctx.accept(field);
        return true;
    }

    /** EDIT-DAY. */
    private boolean editDay(EditContext ctx, String field, String label, String day) {
        if (CobolText.isBlank(day)) {
            ctx.reject(field, FieldFlag.BLANK, label + " : Day must be supplied.");
            return false;
        }
        String value = CobolText.trim(day);
        if (!CobolText.isNumeric(value)) {
            ctx.reject(field, FieldFlag.NOT_OK, label + ":day must be a number between 1 and 31.");
            return false;
        }
        int dayValue = Integer.parseInt(value);
        if (dayValue < 1 || dayValue > 31) {
            ctx.reject(field, FieldFlag.NOT_OK, label + ":day must be a number between 1 and 31.");
            return false;
        }
        ctx.accept(field);
        return true;
    }

    private static boolean isThirtyOneDayMonth(int month) {
        return switch (month) {
            case 1, 3, 5, 7, 8, 10, 12 -> true;
            default -> false;
        };
    }

    /**
     * The COBOL leap year test divides by 400 when the last two digits of the year are zero and
     * by 4 otherwise, which is the century rule expressed as a single division.
     */
    private static boolean isLeapYear(int year) {
        int divisor = year % 100 == 0 ? 400 : 4;
        return year % divisor == 0;
    }

    private static void reject(EditContext ctx, String message, String firstField, FieldFlag firstFlag,
                               String secondField, FieldFlag secondFlag) {
        ctx.reject(firstField, firstFlag, message);
        ctx.reject(secondField, secondFlag, null);
    }
}
