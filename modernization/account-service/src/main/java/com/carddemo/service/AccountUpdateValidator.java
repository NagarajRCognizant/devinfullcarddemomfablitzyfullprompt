package com.carddemo.service;

import com.carddemo.api.dto.AccountUpdateForm;
import com.carddemo.cobol.CobolText;
import com.carddemo.domain.validation.DateEditResult;
import com.carddemo.domain.validation.DateEditor;
import com.carddemo.domain.validation.EditContext;
import com.carddemo.domain.validation.FieldEditor;
import com.carddemo.domain.validation.ScreenField;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Port of COACTUPC paragraph 1200-EDIT-MAP-INPUTS.
 *
 * <p>The paragraph runs every field edit unconditionally and in a fixed order, collecting one
 * flag per field and keeping only the first message, so a single submission reports every failing
 * field and the message of the first failure. That is why the edits below are not short circuited
 * and why {@link EditContext} keeps the first message.
 *
 * <p>The variable name literals ('Account Status', 'Open Date', ...) are part of the messages the
 * screen shows, so they are passed through as labels rather than being reworded.
 */
@Component
public class AccountUpdateValidator {

    private final FieldEditor fieldEditor;
    private final DateEditor dateEditor;

    public AccountUpdateValidator(FieldEditor fieldEditor, DateEditor dateEditor) {
        this.fieldEditor = fieldEditor;
        this.dateEditor = dateEditor;
    }

    /**
     * Runs the edits of 1200-EDIT-MAP-INPUTS on the keyed values.
     *
     * @return the converted values, or {@code null} when at least one edit failed
     */
    public ValidatedAccountUpdate validate(EditContext ctx, long accountId, long customerId,
                                           AccountUpdateForm keyed) {
        AccountUpdateForm form = normalise(keyed);

        fieldEditor.editYesNo(ctx, ScreenField.ACTIVE_STATUS, "Account Status", form.activeStatus());

        DateEditResult openDate = dateEditor.editDate(ctx, ScreenField.OPEN_YEAR, ScreenField.OPEN_MONTH,
                ScreenField.OPEN_DAY, "Open Date", form.openYear(), form.openMonth(), form.openDay());

        BigDecimal creditLimit = fieldEditor.editSignedAmount(ctx, ScreenField.CREDIT_LIMIT,
                "Credit Limit", form.creditLimit());

        DateEditResult expiryDate = dateEditor.editDate(ctx, ScreenField.EXPIRY_YEAR,
                ScreenField.EXPIRY_MONTH, ScreenField.EXPIRY_DAY, "Expiry Date",
                form.expiryYear(), form.expiryMonth(), form.expiryDay());

        BigDecimal cashCreditLimit = fieldEditor.editSignedAmount(ctx, ScreenField.CASH_CREDIT_LIMIT,
                "Cash Credit Limit", form.cashCreditLimit());

        DateEditResult reissueDate = dateEditor.editDate(ctx, ScreenField.REISSUE_YEAR,
                ScreenField.REISSUE_MONTH, ScreenField.REISSUE_DAY, "Reissue Date",
                form.reissueYear(), form.reissueMonth(), form.reissueDay());

        BigDecimal currentBalance = fieldEditor.editSignedAmount(ctx, ScreenField.CURRENT_BALANCE,
                "Current Balance", form.currentBalance());

        BigDecimal currentCycleCredit = fieldEditor.editSignedAmount(ctx, ScreenField.CURRENT_CYCLE_CREDIT,
                "Current Cycle Credit Limit", form.currentCycleCredit());

        BigDecimal currentCycleDebit = fieldEditor.editSignedAmount(ctx, ScreenField.CURRENT_CYCLE_DEBIT,
                "Current Cycle Debit Limit", form.currentCycleDebit());

        fieldEditor.editUsSsn(ctx, form.ssnPart1(), form.ssnPart2(), form.ssnPart3());

        DateEditResult dateOfBirth = dateEditor.editDate(ctx, ScreenField.DOB_YEAR, ScreenField.DOB_MONTH,
                ScreenField.DOB_DAY, "Date of Birth", form.dobYear(), form.dobMonth(), form.dobDay());
        if (dateOfBirth.valid()) {
            dateEditor.editDateOfBirth(ctx, ScreenField.DOB_YEAR, ScreenField.DOB_MONTH,
                    ScreenField.DOB_DAY, "Date of Birth", dateOfBirth.date());
        }

        fieldEditor.editNumericRequired(ctx, ScreenField.FICO_SCORE, "FICO Score", form.ficoScore());
        if (ctx.isValid(ScreenField.FICO_SCORE)) {
            fieldEditor.editFicoScore(ctx, "FICO Score", form.ficoScore());
        }

        fieldEditor.editAlphaRequired(ctx, ScreenField.FIRST_NAME, "First Name", form.firstName());
        fieldEditor.editAlphaOptional(ctx, ScreenField.MIDDLE_NAME, "Middle Name", form.middleName());
        fieldEditor.editAlphaRequired(ctx, ScreenField.LAST_NAME, "Last Name", form.lastName());
        fieldEditor.editMandatory(ctx, ScreenField.ADDRESS_LINE_1, "Address Line 1", form.addressLine1());

        fieldEditor.editAlphaRequired(ctx, ScreenField.STATE, "State", form.state());
        if (ctx.isValid(ScreenField.STATE)) {
            fieldEditor.editUsStateCode(ctx, "State", form.state());
        }

        fieldEditor.editNumericRequired(ctx, ScreenField.ZIP, "Zip", form.zip());

        // Address Line 2 is deliberately not edited; the COBOL comments the edit out because the
        // field is optional. Recorded as dead code in the Unsupported Construct Register.
        fieldEditor.editAlphaRequired(ctx, ScreenField.CITY, "City", form.city());
        fieldEditor.editAlphaRequired(ctx, ScreenField.COUNTRY, "Country", form.country());

        fieldEditor.editUsPhoneNumber(ctx, ScreenField.PHONE_1_AREA, ScreenField.PHONE_1_PREFIX,
                ScreenField.PHONE_1_LINE, "Phone Number 1",
                form.phone1Area(), form.phone1Prefix(), form.phone1Line());
        fieldEditor.editUsPhoneNumber(ctx, ScreenField.PHONE_2_AREA, ScreenField.PHONE_2_PREFIX,
                ScreenField.PHONE_2_LINE, "Phone Number 2",
                form.phone2Area(), form.phone2Prefix(), form.phone2Line());

        fieldEditor.editNumericRequired(ctx, ScreenField.EFT_ACCOUNT_ID, "EFT Account Id",
                form.eftAccountId());
        fieldEditor.editYesNo(ctx, ScreenField.PRIMARY_CARD_HOLDER, "Primary Card Holder",
                form.primaryCardHolder());

        if (ctx.isValid(ScreenField.STATE) && ctx.isValid(ScreenField.ZIP)) {
            fieldEditor.editUsStateZipCombo(ctx, form.state(), form.zip());
        }

        if (ctx.isInputError()) {
            return null;
        }

        return new ValidatedAccountUpdate(
                accountId,
                CobolText.upperTrim(form.activeStatus()),
                currentBalance,
                creditLimit,
                cashCreditLimit,
                currentCycleCredit,
                currentCycleDebit,
                isoDate(openDate),
                isoDate(expiryDate),
                isoDate(reissueDate),
                CobolText.trim(form.groupId()),
                customerId,
                CobolText.trim(form.firstName()),
                CobolText.trim(form.middleName()),
                CobolText.trim(form.lastName()),
                CobolText.trim(form.addressLine1()),
                CobolText.trim(form.addressLine2()),
                CobolText.trim(form.city()),
                CobolText.upperTrim(form.state()),
                CobolText.upperTrim(form.country()),
                CobolText.trim(form.zip()),
                phoneNumber(form.phone1Area(), form.phone1Prefix(), form.phone1Line()),
                phoneNumber(form.phone2Area(), form.phone2Prefix(), form.phone2Line()),
                CobolText.padLeftZero(CobolText.trim(form.ssnPart1()), 3)
                        + CobolText.padLeftZero(CobolText.trim(form.ssnPart2()), 2)
                        + CobolText.padLeftZero(CobolText.trim(form.ssnPart3()), 4),
                CobolText.trim(form.governmentIssuedId()),
                isoDate(dateOfBirth),
                CobolText.trim(form.eftAccountId()),
                CobolText.upperTrim(form.primaryCardHolder()),
                Integer.parseInt(CobolText.trim(form.ficoScore())));
    }

    /** 9600-WRITE-PROCESSING: {@code STRING year '-' month '-' day}. */
    private static String isoDate(DateEditResult result) {
        return result.date().toString();
    }

    /** 9600-WRITE-PROCESSING: {@code STRING '(' area ')' prefix '-' line}. */
    private static String phoneNumber(String area, String prefix, String line) {
        return "(" + CobolText.padLeftZero(CobolText.trim(area), 3) + ")"
                + CobolText.padLeftZero(CobolText.trim(prefix), 3) + "-"
                + CobolText.padLeftZero(CobolText.trim(line), 4);
    }

    /**
     * RECEIVE-MAP of COACTUPC replaces a single asterisk with LOW-VALUES for every input field,
     * which is how the map lets the user blank a value out.
     */
    private static AccountUpdateForm normalise(AccountUpdateForm form) {
        return new AccountUpdateForm(
                CobolText.normaliseAsterisk(form.accountId()),
                CobolText.normaliseAsterisk(form.activeStatus()),
                CobolText.normaliseAsterisk(form.currentBalance()),
                CobolText.normaliseAsterisk(form.creditLimit()),
                CobolText.normaliseAsterisk(form.cashCreditLimit()),
                CobolText.normaliseAsterisk(form.openYear()),
                CobolText.normaliseAsterisk(form.openMonth()),
                CobolText.normaliseAsterisk(form.openDay()),
                CobolText.normaliseAsterisk(form.expiryYear()),
                CobolText.normaliseAsterisk(form.expiryMonth()),
                CobolText.normaliseAsterisk(form.expiryDay()),
                CobolText.normaliseAsterisk(form.reissueYear()),
                CobolText.normaliseAsterisk(form.reissueMonth()),
                CobolText.normaliseAsterisk(form.reissueDay()),
                CobolText.normaliseAsterisk(form.currentCycleCredit()),
                CobolText.normaliseAsterisk(form.currentCycleDebit()),
                CobolText.normaliseAsterisk(form.groupId()),
                CobolText.normaliseAsterisk(form.customerId()),
                CobolText.normaliseAsterisk(form.ssnPart1()),
                CobolText.normaliseAsterisk(form.ssnPart2()),
                CobolText.normaliseAsterisk(form.ssnPart3()),
                CobolText.normaliseAsterisk(form.dobYear()),
                CobolText.normaliseAsterisk(form.dobMonth()),
                CobolText.normaliseAsterisk(form.dobDay()),
                CobolText.normaliseAsterisk(form.ficoScore()),
                CobolText.normaliseAsterisk(form.firstName()),
                CobolText.normaliseAsterisk(form.middleName()),
                CobolText.normaliseAsterisk(form.lastName()),
                CobolText.normaliseAsterisk(form.addressLine1()),
                CobolText.normaliseAsterisk(form.addressLine2()),
                CobolText.normaliseAsterisk(form.city()),
                CobolText.normaliseAsterisk(form.state()),
                CobolText.normaliseAsterisk(form.zip()),
                CobolText.normaliseAsterisk(form.country()),
                CobolText.normaliseAsterisk(form.phone1Area()),
                CobolText.normaliseAsterisk(form.phone1Prefix()),
                CobolText.normaliseAsterisk(form.phone1Line()),
                CobolText.normaliseAsterisk(form.phone2Area()),
                CobolText.normaliseAsterisk(form.phone2Prefix()),
                CobolText.normaliseAsterisk(form.phone2Line()),
                CobolText.normaliseAsterisk(form.governmentIssuedId()),
                CobolText.normaliseAsterisk(form.eftAccountId()),
                CobolText.normaliseAsterisk(form.primaryCardHolder()));
    }
}
