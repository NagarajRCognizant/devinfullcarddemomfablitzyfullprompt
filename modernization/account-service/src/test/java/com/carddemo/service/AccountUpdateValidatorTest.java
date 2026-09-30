package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.FormFixtures;
import com.carddemo.TestFixtures;
import com.carddemo.api.dto.AccountUpdateForm;
import com.carddemo.domain.reference.LookupTables;
import com.carddemo.domain.validation.DateEditor;
import com.carddemo.domain.validation.DateValidationService;
import com.carddemo.domain.validation.EditContext;
import com.carddemo.domain.validation.FieldEditor;
import com.carddemo.domain.validation.FieldFlag;
import com.carddemo.domain.validation.ScreenField;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** 1200-EDIT-MAP-INPUTS. */
class AccountUpdateValidatorTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2024-06-15T00:00:00Z"), ZoneOffset.UTC);

    private final AccountUpdateValidator validator = new AccountUpdateValidator(
            new FieldEditor(new LookupTables()), new DateEditor(new DateValidationService(), FIXED));

    @Test
    void theFixtureFormPassesEveryEdit() {
        EditContext ctx = new EditContext();

        ValidatedAccountUpdate validated = validator.validate(ctx, TestFixtures.ACCOUNT_ID,
                TestFixtures.CUSTOMER_ID, FormFixtures.form());

        assertThat(ctx.isInputError()).isFalse();
        assertThat(validated).isNotNull();
        assertThat(validated.creditLimit()).isEqualByComparingTo("5000.00");
        assertThat(validated.openDate()).isEqualTo("2015-03-01");
        assertThat(validated.dateOfBirth()).isEqualTo("1980-07-04");
        assertThat(validated.phoneNum1()).isEqualTo("(212)555-1234");
        assertThat(validated.phoneNum2()).isEqualTo("(646)555-9876");
        assertThat(validated.ssn()).isEqualTo("123456789");
        assertThat(validated.ficoScore()).isEqualTo(720);
        assertThat(validated.accountId()).isEqualTo(TestFixtures.ACCOUNT_ID);
        assertThat(validated.customerId()).isEqualTo(TestFixtures.CUSTOMER_ID);
    }

    @Test
    void everyFailingFieldIsFlaggedButOnlyTheFirstMessageIsReturned() {
        EditContext ctx = new EditContext();
        AccountUpdateForm keyed = everyFieldSetTo("");

        assertThat(validator.validate(ctx, TestFixtures.ACCOUNT_ID, TestFixtures.CUSTOMER_ID, keyed))
                .isNull();
        assertThat(ctx.isInputError()).isTrue();
        assertThat(ctx.getReturnMessage()).isEqualTo("Account Status must be supplied.");
        assertThat(ctx.getFailedFields()).containsKeys(ScreenField.ACTIVE_STATUS,
                ScreenField.OPEN_YEAR, ScreenField.CREDIT_LIMIT, ScreenField.FIRST_NAME,
                ScreenField.LAST_NAME, ScreenField.ADDRESS_LINE_1, ScreenField.STATE,
                ScreenField.ZIP, ScreenField.CITY, ScreenField.COUNTRY,
                ScreenField.EFT_ACCOUNT_ID, ScreenField.PRIMARY_CARD_HOLDER,
                ScreenField.FICO_SCORE);
        assertThat(ctx.flag(ScreenField.ACTIVE_STATUS)).isEqualTo(FieldFlag.BLANK);
    }

    @Test
    void aSingleAsteriskBlanksTheFieldOut() {
        EditContext ctx = new EditContext();
        AccountUpdateForm keyed = everyFieldSetTo("*");

        assertThat(validator.validate(ctx, TestFixtures.ACCOUNT_ID, TestFixtures.CUSTOMER_ID, keyed))
                .isNull();
        assertThat(ctx.flag(ScreenField.ACTIVE_STATUS)).isEqualTo(FieldFlag.BLANK);
    }

    @Test
    void aFicoScoreOutsideTheRangeIsRejectedAfterTheNumericEdit() {
        EditContext ctx = new EditContext();
        AccountUpdateForm keyed = withFico(FormFixtures.form(), "900");

        assertThat(validator.validate(ctx, TestFixtures.ACCOUNT_ID, TestFixtures.CUSTOMER_ID, keyed))
                .isNull();
        assertThat(ctx.getReturnMessage()).isEqualTo("FICO Score: should be between 300 and 850");
    }

    @Test
    void aStateZipMismatchIsOnlyCheckedWhenBothFieldsAreValid() {
        EditContext ctx = new EditContext();
        AccountUpdateForm keyed = withZip(FormFixtures.form(), "99999");

        assertThat(validator.validate(ctx, TestFixtures.ACCOUNT_ID, TestFixtures.CUSTOMER_ID, keyed))
                .isNull();
        assertThat(ctx.getReturnMessage()).isEqualTo("Invalid zip code for state");
    }

    private static AccountUpdateForm everyFieldSetTo(String value) {
        return new AccountUpdateForm(value, value, value, value, value, value, value, value, value,
                value, value, value, value, value, value, value, value, value, value, value, value,
                value, value, value, value, value, value, value, value, value, value, value, value,
                value, value, value, value, value, value, value, value, value, value);
    }

    private static AccountUpdateForm withFico(AccountUpdateForm form, String fico) {
        return new AccountUpdateForm(form.accountId(), form.activeStatus(), form.currentBalance(),
                form.creditLimit(), form.cashCreditLimit(), form.openYear(), form.openMonth(),
                form.openDay(), form.expiryYear(), form.expiryMonth(), form.expiryDay(),
                form.reissueYear(), form.reissueMonth(), form.reissueDay(),
                form.currentCycleCredit(), form.currentCycleDebit(), form.groupId(),
                form.customerId(), form.ssnPart1(), form.ssnPart2(), form.ssnPart3(),
                form.dobYear(), form.dobMonth(), form.dobDay(), fico,
                form.firstName(), form.middleName(), form.lastName(), form.addressLine1(),
                form.addressLine2(), form.city(), form.state(), form.zip(), form.country(),
                form.phone1Area(), form.phone1Prefix(), form.phone1Line(), form.phone2Area(),
                form.phone2Prefix(), form.phone2Line(), form.governmentIssuedId(),
                form.eftAccountId(), form.primaryCardHolder());
    }

    private static AccountUpdateForm withZip(AccountUpdateForm form, String zip) {
        return new AccountUpdateForm(form.accountId(), form.activeStatus(), form.currentBalance(),
                form.creditLimit(), form.cashCreditLimit(), form.openYear(), form.openMonth(),
                form.openDay(), form.expiryYear(), form.expiryMonth(), form.expiryDay(),
                form.reissueYear(), form.reissueMonth(), form.reissueDay(),
                form.currentCycleCredit(), form.currentCycleDebit(), form.groupId(),
                form.customerId(), form.ssnPart1(), form.ssnPart2(), form.ssnPart3(),
                form.dobYear(), form.dobMonth(), form.dobDay(), form.ficoScore(),
                form.firstName(), form.middleName(), form.lastName(), form.addressLine1(),
                form.addressLine2(), form.city(), form.state(), zip, form.country(),
                form.phone1Area(), form.phone1Prefix(), form.phone1Line(), form.phone2Area(),
                form.phone2Prefix(), form.phone2Line(), form.governmentIssuedId(),
                form.eftAccountId(), form.primaryCardHolder());
    }
}
