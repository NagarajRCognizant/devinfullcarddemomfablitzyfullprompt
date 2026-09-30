package com.carddemo.domain.validation;

import com.carddemo.cobol.CobolNumeric;
import com.carddemo.cobol.CobolText;
import com.carddemo.domain.reference.LookupTables;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Port of the generic edit paragraphs of COACTUPC (1215 through 1280).
 *
 * <p>Each method keeps the source order of its tests and the exact WS-RETURN-MSG text, because
 * the message and the highlighted field are part of the observable behaviour of the screen.
 */
@Component
public class FieldEditor {

    private final LookupTables lookupTables;

    public FieldEditor(LookupTables lookupTables) {
        this.lookupTables = lookupTables;
    }

    /** 1215-EDIT-MANDATORY. */
    public void editMandatory(EditContext ctx, String field, String label, String value) {
        if (CobolText.isBlank(value)) {
            ctx.reject(field, FieldFlag.BLANK, label + " must be supplied.");
            return;
        }
        ctx.accept(field);
    }

    /** 1220-EDIT-YESNO - the flag must be an upper case Y or N. */
    public void editYesNo(EditContext ctx, String field, String label, String value) {
        if (CobolText.isBlank(value) || CobolText.isAllZeroes(value)) {
            ctx.reject(field, FieldFlag.BLANK, label + " must be supplied.");
            return;
        }
        String trimmed = CobolText.trim(value);
        if (!trimmed.equals("Y") && !trimmed.equals("N")) {
            ctx.reject(field, FieldFlag.NOT_OK, label + " must be Y or N.");
            return;
        }
        ctx.accept(field);
    }

    /** 1225-EDIT-ALPHA-REQD. */
    public void editAlphaRequired(EditContext ctx, String field, String label, String value) {
        if (CobolText.isBlank(value)) {
            ctx.reject(field, FieldFlag.BLANK, label + " must be supplied.");
            return;
        }
        if (!CobolText.isAlphabeticOrSpace(value)) {
            ctx.reject(field, FieldFlag.NOT_OK, label + " can have alphabets only.");
            return;
        }
        ctx.accept(field);
    }

    /** 1235-EDIT-ALPHA-OPT - blank is accepted. */
    public void editAlphaOptional(EditContext ctx, String field, String label, String value) {
        if (CobolText.isBlank(value)) {
            ctx.accept(field);
            return;
        }
        if (!CobolText.isAlphabeticOrSpace(value)) {
            ctx.reject(field, FieldFlag.NOT_OK, label + " can have alphabets only.");
            return;
        }
        ctx.accept(field);
    }

    /** 1230-EDIT-ALPHANUM-REQD. */
    public void editAlphanumRequired(EditContext ctx, String field, String label, String value) {
        if (CobolText.isBlank(value)) {
            ctx.reject(field, FieldFlag.BLANK, label + " must be supplied.");
            return;
        }
        if (!CobolText.isAlphanumericOrSpace(value)) {
            ctx.reject(field, FieldFlag.NOT_OK, label + " can have numbers or alphabets only.");
            return;
        }
        ctx.accept(field);
    }

    /** 1240-EDIT-ALPHANUM-OPT - blank is accepted. */
    public void editAlphanumOptional(EditContext ctx, String field, String label, String value) {
        if (CobolText.isBlank(value)) {
            ctx.accept(field);
            return;
        }
        if (!CobolText.isAlphanumericOrSpace(value)) {
            ctx.reject(field, FieldFlag.NOT_OK, label + " can have numbers or alphabets only.");
            return;
        }
        ctx.accept(field);
    }

    /** 1245-EDIT-NUM-REQD - supplied, all numeric and not zero. */
    public void editNumericRequired(EditContext ctx, String field, String label, String value) {
        if (CobolText.isBlank(value)) {
            ctx.reject(field, FieldFlag.BLANK, label + " must be supplied.");
            return;
        }
        if (!CobolText.isNumeric(CobolText.trim(value))) {
            ctx.reject(field, FieldFlag.NOT_OK, label + " must be all numeric.");
            return;
        }
        if (new BigDecimal(CobolText.trim(value)).signum() == 0) {
            ctx.reject(field, FieldFlag.NOT_OK, label + " must not be zero.");
            return;
        }
        ctx.accept(field);
    }

    /**
     * 1250-EDIT-SIGNED-9V2 - the keyed amount must be a valid signed decimal.
     *
     * @return the converted amount, or {@code null} when the edit failed
     */
    public BigDecimal editSignedAmount(EditContext ctx, String field, String label, String value) {
        if (CobolText.isBlank(value)) {
            ctx.reject(field, FieldFlag.BLANK, label + " must be supplied.");
            return null;
        }
        BigDecimal amount = CobolNumeric.parseNumericWithCurrency(value);
        if (amount == null) {
            ctx.reject(field, FieldFlag.NOT_OK, label + " is not valid");
            return null;
        }
        ctx.accept(field);
        return amount;
    }

    /**
     * 1260-EDIT-US-PHONE-NUM.
     *
     * <p>AS-IS behaviour preserved: the "phone number not supplied" test at COACTUPC line 2234
     * checks the area code twice instead of checking the line number, so a phone number whose
     * area code and prefix are both blank is treated as absent even when a line number was
     * keyed. Recorded in the Unsupported Construct Register as REVIEW REQUIRED.
     */
    public void editUsPhoneNumber(EditContext ctx, String areaField, String prefixField, String lineField,
                                  String label, String area, String prefix, String line) {
        if (CobolText.isBlank(area) && CobolText.isBlank(prefix)
                && (CobolText.isBlank(area) || CobolText.isBlank(line))) {
            ctx.accept(areaField);
            ctx.accept(prefixField);
            ctx.accept(lineField);
            return;
        }
        editPhoneAreaCode(ctx, areaField, label, area);
        editPhonePrefix(ctx, prefixField, label, prefix);
        editPhoneLineNumber(ctx, lineField, label, line);
    }

    /** EDIT-AREA-CODE. */
    private void editPhoneAreaCode(EditContext ctx, String field, String label, String area) {
        if (CobolText.isBlank(area)) {
            ctx.reject(field, FieldFlag.BLANK, label + ": Area code must be supplied.");
            return;
        }
        if (!CobolText.isNumeric(CobolText.trim(area))) {
            ctx.reject(field, FieldFlag.NOT_OK, label + ": Area code must be A 3 digit number.");
            return;
        }
        if (Integer.parseInt(CobolText.trim(area)) == 0) {
            ctx.reject(field, FieldFlag.NOT_OK, label + ": Area code cannot be zero");
            return;
        }
        if (!lookupTables.isGeneralPurposeAreaCode(area)) {
            ctx.reject(field, FieldFlag.NOT_OK,
                    label + ": Not valid North America general purpose area code");
            return;
        }
        ctx.accept(field);
    }

    /** EDIT-US-PHONE-PREFIX. */
    private void editPhonePrefix(EditContext ctx, String field, String label, String prefix) {
        if (CobolText.isBlank(prefix)) {
            ctx.reject(field, FieldFlag.BLANK, label + ": Prefix code must be supplied.");
            return;
        }
        if (!CobolText.isNumeric(CobolText.trim(prefix))) {
            ctx.reject(field, FieldFlag.NOT_OK, label + ": Prefix code must be A 3 digit number.");
            return;
        }
        if (Integer.parseInt(CobolText.trim(prefix)) == 0) {
            ctx.reject(field, FieldFlag.NOT_OK, label + ": Prefix code cannot be zero");
            return;
        }
        ctx.accept(field);
    }

    /** EDIT-US-PHONE-LINENUM. */
    private void editPhoneLineNumber(EditContext ctx, String field, String label, String line) {
        if (CobolText.isBlank(line)) {
            ctx.reject(field, FieldFlag.BLANK, label + ": Line number code must be supplied.");
            return;
        }
        if (!CobolText.isNumeric(CobolText.trim(line))) {
            ctx.reject(field, FieldFlag.NOT_OK, label + ": Line number code must be A 4 digit number.");
            return;
        }
        if (Integer.parseInt(CobolText.trim(line)) == 0) {
            ctx.reject(field, FieldFlag.NOT_OK, label + ": Line number code cannot be zero");
            return;
        }
        ctx.accept(field);
    }

    /** 1265-EDIT-US-SSN - each part is a non zero number, part 1 also excludes 000, 666 and 900-999. */
    public void editUsSsn(EditContext ctx, String part1, String part2, String part3) {
        editNumericRequired(ctx, ScreenField.SSN_PART1, "SSN: First 3 chars", part1);
        if (ctx.isValid(ScreenField.SSN_PART1)) {
            int value = Integer.parseInt(CobolText.trim(part1));
            if (value == 0 || value == 666 || (value >= 900 && value <= 999)) {
                ctx.reject(ScreenField.SSN_PART1, FieldFlag.NOT_OK,
                        "SSN: First 3 chars: should not be 000, 666, or between 900 and 999");
            }
        }
        editNumericRequired(ctx, ScreenField.SSN_PART2, "SSN 4th & 5th chars", part2);
        editNumericRequired(ctx, ScreenField.SSN_PART3, "SSN Last 4 chars", part3);
    }

    /** 1270-EDIT-US-STATE-CD. */
    public void editUsStateCode(EditContext ctx, String label, String stateCode) {
        if (!lookupTables.isStateCode(stateCode)) {
            ctx.reject(ScreenField.STATE, FieldFlag.NOT_OK, label + ": is not a valid state code");
        }
    }

    /** 1275-EDIT-FICO-SCORE - 300 through 850. */
    public void editFicoScore(EditContext ctx, String label, String ficoScore) {
        int score = Integer.parseInt(CobolText.trim(ficoScore));
        if (score < 300 || score > 850) {
            ctx.reject(ScreenField.FICO_SCORE, FieldFlag.NOT_OK, label + ": should be between 300 and 850");
        }
    }

    /** 1280-EDIT-US-STATE-ZIP-CD - state code plus the first two zip digits must be a known pair. */
    public void editUsStateZipCombo(EditContext ctx, String stateCode, String zip) {
        String zipPrefix = CobolText.padRight(CobolText.trim(zip), 2).substring(0, 2);
        if (!lookupTables.isStateZipCombo(stateCode, zipPrefix)) {
            ctx.reject(ScreenField.STATE, FieldFlag.NOT_OK, "Invalid zip code for state");
            ctx.reject(ScreenField.ZIP, FieldFlag.NOT_OK, "Invalid zip code for state");
        }
    }
}
