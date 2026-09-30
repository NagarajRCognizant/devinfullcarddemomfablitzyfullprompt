package com.carddemo.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.domain.reference.LookupTables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Source-derived expectations for the generic edit paragraphs 1215 through 1280 of COACTUPC. */
class FieldEditorTest {

    private FieldEditor editor;
    private EditContext ctx;

    @BeforeEach
    void setUp() {
        editor = new FieldEditor(new LookupTables());
        ctx = new EditContext();
    }

    @Test
    void mandatoryFieldRejectsBlank() {
        editor.editMandatory(ctx, ScreenField.ADDRESS_LINE_1, "Address Line 1", "  ");

        assertThat(ctx.isInputError()).isTrue();
        assertThat(ctx.flag(ScreenField.ADDRESS_LINE_1)).isEqualTo(FieldFlag.BLANK);
        assertThat(ctx.getReturnMessage()).isEqualTo("Address Line 1 must be supplied.");
    }

    @Test
    void mandatoryFieldAcceptsAValue() {
        editor.editMandatory(ctx, ScreenField.ADDRESS_LINE_1, "Address Line 1", "1 Main St");

        assertThat(ctx.isInputError()).isFalse();
        assertThat(ctx.isValid(ScreenField.ADDRESS_LINE_1)).isTrue();
    }

    @Test
    void yesNoAcceptsOnlyYAndN() {
        editor.editYesNo(ctx, ScreenField.ACTIVE_STATUS, "Account Status", "Y");
        assertThat(ctx.isInputError()).isFalse();

        EditContext other = new EditContext();
        editor.editYesNo(other, ScreenField.ACTIVE_STATUS, "Account Status", "X");
        assertThat(other.flag(ScreenField.ACTIVE_STATUS)).isEqualTo(FieldFlag.NOT_OK);
        assertThat(other.getReturnMessage()).isEqualTo("Account Status must be Y or N.");
    }

    @Test
    void yesNoTreatsZeroesAsNotSupplied() {
        editor.editYesNo(ctx, ScreenField.ACTIVE_STATUS, "Account Status", "0");

        assertThat(ctx.flag(ScreenField.ACTIVE_STATUS)).isEqualTo(FieldFlag.BLANK);
        assertThat(ctx.getReturnMessage()).isEqualTo("Account Status must be supplied.");
    }

    @Test
    void alphaRequiredRejectsBlankAndDigits() {
        editor.editAlphaRequired(ctx, ScreenField.FIRST_NAME, "First Name", "");
        assertThat(ctx.flag(ScreenField.FIRST_NAME)).isEqualTo(FieldFlag.BLANK);

        EditContext digits = new EditContext();
        editor.editAlphaRequired(digits, ScreenField.FIRST_NAME, "First Name", "J0HN");
        assertThat(digits.getReturnMessage()).isEqualTo("First Name can have alphabets only.");
    }

    @Test
    void alphaOptionalAcceptsBlankButNotDigits() {
        editor.editAlphaOptional(ctx, ScreenField.MIDDLE_NAME, "Middle Name", "  ");
        assertThat(ctx.isInputError()).isFalse();

        EditContext digits = new EditContext();
        editor.editAlphaOptional(digits, ScreenField.MIDDLE_NAME, "Middle Name", "A1");
        assertThat(digits.getReturnMessage()).isEqualTo("Middle Name can have alphabets only.");
    }

    @Test
    void alphanumericEdits() {
        editor.editAlphanumRequired(ctx, ScreenField.GROUP_ID, "Group Id", "GRP1");
        assertThat(ctx.isInputError()).isFalse();

        EditContext blank = new EditContext();
        editor.editAlphanumRequired(blank, ScreenField.GROUP_ID, "Group Id", " ");
        assertThat(blank.getReturnMessage()).isEqualTo("Group Id must be supplied.");

        EditContext bad = new EditContext();
        editor.editAlphanumRequired(bad, ScreenField.GROUP_ID, "Group Id", "A-1");
        assertThat(bad.getReturnMessage()).isEqualTo("Group Id can have numbers or alphabets only.");

        EditContext optionalBlank = new EditContext();
        editor.editAlphanumOptional(optionalBlank, ScreenField.GROUP_ID, "Group Id", " ");
        assertThat(optionalBlank.isInputError()).isFalse();

        EditContext optionalBad = new EditContext();
        editor.editAlphanumOptional(optionalBad, ScreenField.GROUP_ID, "Group Id", "A-1");
        assertThat(optionalBad.getReturnMessage()).isEqualTo("Group Id can have numbers or alphabets only.");
    }

    @Test
    void numericRequiredRejectsBlankNonNumericAndZero() {
        editor.editNumericRequired(ctx, ScreenField.ZIP, "Zip", "");
        assertThat(ctx.getReturnMessage()).isEqualTo("Zip must be supplied.");

        EditContext alpha = new EditContext();
        editor.editNumericRequired(alpha, ScreenField.ZIP, "Zip", "1A234");
        assertThat(alpha.getReturnMessage()).isEqualTo("Zip must be all numeric.");

        EditContext zero = new EditContext();
        editor.editNumericRequired(zero, ScreenField.ZIP, "Zip", "00000");
        assertThat(zero.getReturnMessage()).isEqualTo("Zip must not be zero.");

        EditContext ok = new EditContext();
        editor.editNumericRequired(ok, ScreenField.ZIP, "Zip", "01234");
        assertThat(ok.isInputError()).isFalse();
    }

    @Test
    void signedAmountReturnsTheConvertedValue() {
        assertThat(editor.editSignedAmount(ctx, ScreenField.CREDIT_LIMIT, "Credit Limit", "-1,250.75"))
                .isEqualByComparingTo("-1250.75");
        assertThat(ctx.isInputError()).isFalse();
    }

    @Test
    void signedAmountRejectsBlankAndInvalidText() {
        assertThat(editor.editSignedAmount(ctx, ScreenField.CREDIT_LIMIT, "Credit Limit", " ")).isNull();
        assertThat(ctx.getReturnMessage()).isEqualTo("Credit Limit must be supplied.");

        EditContext bad = new EditContext();
        assertThat(editor.editSignedAmount(bad, ScreenField.CREDIT_LIMIT, "Credit Limit", "12X.00")).isNull();
        assertThat(bad.getReturnMessage()).isEqualTo("Credit Limit is not valid");
    }

    @Test
    void phoneNumberIsOptionalWhenAreaCodeAndPrefixAreBlank() {
        editor.editUsPhoneNumber(ctx, ScreenField.PHONE_1_AREA, ScreenField.PHONE_1_PREFIX,
                ScreenField.PHONE_1_LINE, "Phone Number 1", "", "", "");

        assertThat(ctx.isInputError()).isFalse();
    }

    @Test
    void phoneNumberValidatesEachPart() {
        editor.editUsPhoneNumber(ctx, ScreenField.PHONE_1_AREA, ScreenField.PHONE_1_PREFIX,
                ScreenField.PHONE_1_LINE, "Phone Number 1", "212", "555", "1234");
        assertThat(ctx.isInputError()).isFalse();

        EditContext unknownArea = new EditContext();
        editor.editUsPhoneNumber(unknownArea, ScreenField.PHONE_1_AREA, ScreenField.PHONE_1_PREFIX,
                ScreenField.PHONE_1_LINE, "Phone Number 1", "199", "555", "1234");
        assertThat(unknownArea.getReturnMessage())
                .isEqualTo("Phone Number 1: Not valid North America general purpose area code");

        EditContext missingParts = new EditContext();
        editor.editUsPhoneNumber(missingParts, ScreenField.PHONE_1_AREA, ScreenField.PHONE_1_PREFIX,
                ScreenField.PHONE_1_LINE, "Phone Number 1", "212", "", "");
        assertThat(missingParts.getReturnMessage()).isEqualTo("Phone Number 1: Prefix code must be supplied.");
        assertThat(missingParts.flag(ScreenField.PHONE_1_LINE)).isEqualTo(FieldFlag.BLANK);
    }

    @Test
    void phoneNumberPartsMustBeNumericAndNonZero() {
        editor.editUsPhoneNumber(ctx, ScreenField.PHONE_1_AREA, ScreenField.PHONE_1_PREFIX,
                ScreenField.PHONE_1_LINE, "Phone Number 1", "2A2", "5A5", "12X4");
        assertThat(ctx.getReturnMessage()).isEqualTo("Phone Number 1: Area code must be A 3 digit number.");
        assertThat(ctx.flag(ScreenField.PHONE_1_PREFIX)).isEqualTo(FieldFlag.NOT_OK);
        assertThat(ctx.flag(ScreenField.PHONE_1_LINE)).isEqualTo(FieldFlag.NOT_OK);

        EditContext zeroes = new EditContext();
        editor.editUsPhoneNumber(zeroes, ScreenField.PHONE_1_AREA, ScreenField.PHONE_1_PREFIX,
                ScreenField.PHONE_1_LINE, "Phone Number 1", "000", "000", "0000");
        assertThat(zeroes.getReturnMessage()).isEqualTo("Phone Number 1: Area code cannot be zero");
        assertThat(zeroes.flag(ScreenField.PHONE_1_PREFIX)).isEqualTo(FieldFlag.NOT_OK);
        assertThat(zeroes.flag(ScreenField.PHONE_1_LINE)).isEqualTo(FieldFlag.NOT_OK);
    }

    @Test
    void ssnFirstPartExcludesReservedRanges() {
        editor.editUsSsn(ctx, "666", "12", "3456");
        assertThat(ctx.getReturnMessage())
                .isEqualTo("SSN: First 3 chars: should not be 000, 666, or between 900 and 999");

        EditContext high = new EditContext();
        editor.editUsSsn(high, "900", "12", "3456");
        assertThat(high.flag(ScreenField.SSN_PART1)).isEqualTo(FieldFlag.NOT_OK);

        EditContext ok = new EditContext();
        editor.editUsSsn(ok, "123", "45", "6789");
        assertThat(ok.isInputError()).isFalse();
    }

    @Test
    void ssnPartsAreMandatory() {
        editor.editUsSsn(ctx, "123", "", "6789");

        assertThat(ctx.flag(ScreenField.SSN_PART2)).isEqualTo(FieldFlag.BLANK);
        assertThat(ctx.getReturnMessage()).isEqualTo("SSN 4th & 5th chars must be supplied.");
    }

    @Test
    void stateCodeMustBeKnown() {
        editor.editUsStateCode(ctx, "State", "NY");
        assertThat(ctx.isInputError()).isFalse();

        EditContext unknown = new EditContext();
        editor.editUsStateCode(unknown, "State", "XX");
        assertThat(unknown.getReturnMessage()).isEqualTo("State: is not a valid state code");
    }

    @Test
    void ficoScoreRange() {
        editor.editFicoScore(ctx, "FICO Score", "300");
        editor.editFicoScore(ctx, "FICO Score", "850");
        assertThat(ctx.isInputError()).isFalse();

        EditContext low = new EditContext();
        editor.editFicoScore(low, "FICO Score", "299");
        assertThat(low.getReturnMessage()).isEqualTo("FICO Score: should be between 300 and 850");

        EditContext high = new EditContext();
        editor.editFicoScore(high, "FICO Score", "851");
        assertThat(high.flag(ScreenField.FICO_SCORE)).isEqualTo(FieldFlag.NOT_OK);
    }

    @Test
    void stateAndZipMustBeAKnownCombination() {
        editor.editUsStateZipCombo(ctx, "NY", "10001");
        assertThat(ctx.isInputError()).isFalse();

        EditContext mismatch = new EditContext();
        editor.editUsStateZipCombo(mismatch, "NY", "99999");
        assertThat(mismatch.getReturnMessage()).isEqualTo("Invalid zip code for state");
        assertThat(mismatch.flag(ScreenField.STATE)).isEqualTo(FieldFlag.NOT_OK);
        assertThat(mismatch.flag(ScreenField.ZIP)).isEqualTo(FieldFlag.NOT_OK);
    }
}
