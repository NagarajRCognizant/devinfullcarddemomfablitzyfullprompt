package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.FormFixtures;
import com.carddemo.TestFixtures;
import com.carddemo.api.dto.AccountUpdateForm;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** 1205-COMPARE-OLD-NEW. */
class AccountChangeDetectorTest {

    private final AccountChangeDetector detector = new AccountChangeDetector();

    @Test
    void anIdenticalSubmissionHasNoChanges() {
        assertThat(detector.hasChanges(FormFixtures.form(), FormFixtures.form())).isFalse();
    }

    @Test
    void aChangedAccountFieldIsDetected() {
        AccountUpdateForm keyed = new AccountUpdateForm(
                FormFixtures.form().accountId(), "N", FormFixtures.form().currentBalance(),
                FormFixtures.form().creditLimit(), FormFixtures.form().cashCreditLimit(),
                "2015", "03", "01", "2026", "03", "01", "2022", "03", "01",
                "120.00", "240.00", "GRP0000001", "100000001", "123", "45", "6789",
                "1980", "07", "04", "720", "John", "Q", "Public",
                "1 Main Street", "Apt 2", "New York", "NY", "10001", "USA",
                "212", "555", "1234", "646", "555", "9876",
                "NY-DL-12345", "1234567890", "Y");

        assertThat(detector.hasChanges(FormFixtures.form(), keyed)).isTrue();
    }

    @Test
    void aChangedCustomerFieldIsDetected() {
        AccountUpdateForm keyed = withLastName(FormFixtures.form(), "Private");

        assertThat(detector.hasChanges(FormFixtures.form(), keyed)).isTrue();
    }

    @Test
    void caseOnlyDifferencesInTheUpperCasedFieldsAreNotChanges() {
        AccountUpdateForm keyed = withLastName(FormFixtures.form(), " public ");

        assertThat(detector.hasChanges(FormFixtures.form(), keyed)).isFalse();
    }

    @Test
    void amountsAreComparedAsKeyedText() {
        AccountUpdateForm keyed = withCreditLimit(FormFixtures.form(), "5000.0");

        assertThat(detector.hasChanges(FormFixtures.form(), keyed)).isTrue();
    }


    /**
     * 1205-COMPARE-OLD-NEW compares every field of both blocks, so a change in any one of them has
     * to be reported. The record components are the fields of ACUP-NEW-DETAILS, which is why the
     * case is driven from them rather than from a hand written list that could fall behind.
     */
    @ParameterizedTest
    @MethodSource("comparedFields")
    void aChangeInAnyComparedFieldIsDetected(String field) throws ReflectiveOperationException {
        AccountUpdateForm keyed = withChangedField(FormFixtures.form(), field);

        assertThat(detector.hasChanges(FormFixtures.form(), keyed)).isTrue();
    }

    private static Stream<String> comparedFields() {
        return Arrays.stream(AccountUpdateForm.class.getRecordComponents())
                .map(RecordComponent::getName);
    }

    /** Rebuilds the block with one field given a value that differs however it is compared. */
    private static AccountUpdateForm withChangedField(AccountUpdateForm form, String field)
            throws ReflectiveOperationException {
        RecordComponent[] components = AccountUpdateForm.class.getRecordComponents();
        Object[] values = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        for (int index = 0; index < components.length; index++) {
            types[index] = components[index].getType();
            values[index] = components[index].getAccessor().invoke(form);
            if (components[index].getName().equals(field)) {
                values[index] = values[index] + "1";
            }
        }
        return AccountUpdateForm.class.getDeclaredConstructor(types).newInstance(values);
    }

    private static AccountUpdateForm withLastName(AccountUpdateForm form, String lastName) {
        return new AccountUpdateForm(form.accountId(), form.activeStatus(), form.currentBalance(),
                form.creditLimit(), form.cashCreditLimit(), form.openYear(), form.openMonth(),
                form.openDay(), form.expiryYear(), form.expiryMonth(), form.expiryDay(),
                form.reissueYear(), form.reissueMonth(), form.reissueDay(),
                form.currentCycleCredit(), form.currentCycleDebit(), form.groupId(),
                form.customerId(), form.ssnPart1(), form.ssnPart2(), form.ssnPart3(),
                form.dobYear(), form.dobMonth(), form.dobDay(), form.ficoScore(),
                form.firstName(), form.middleName(), lastName, form.addressLine1(),
                form.addressLine2(), form.city(), form.state(), form.zip(), form.country(),
                form.phone1Area(), form.phone1Prefix(), form.phone1Line(), form.phone2Area(),
                form.phone2Prefix(), form.phone2Line(), form.governmentIssuedId(),
                form.eftAccountId(), form.primaryCardHolder());
    }

    private static AccountUpdateForm withCreditLimit(AccountUpdateForm form, String creditLimit) {
        return new AccountUpdateForm(form.accountId(), form.activeStatus(), form.currentBalance(),
                creditLimit, form.cashCreditLimit(), form.openYear(), form.openMonth(),
                form.openDay(), form.expiryYear(), form.expiryMonth(), form.expiryDay(),
                form.reissueYear(), form.reissueMonth(), form.reissueDay(),
                form.currentCycleCredit(), form.currentCycleDebit(), form.groupId(),
                form.customerId(), form.ssnPart1(), form.ssnPart2(), form.ssnPart3(),
                form.dobYear(), form.dobMonth(), form.dobDay(), form.ficoScore(),
                form.firstName(), form.middleName(), form.lastName(), form.addressLine1(),
                form.addressLine2(), form.city(), form.state(), form.zip(), form.country(),
                form.phone1Area(), form.phone1Prefix(), form.phone1Line(), form.phone2Area(),
                form.phone2Prefix(), form.phone2Line(), form.governmentIssuedId(),
                form.eftAccountId(), form.primaryCardHolder());
    }
}
