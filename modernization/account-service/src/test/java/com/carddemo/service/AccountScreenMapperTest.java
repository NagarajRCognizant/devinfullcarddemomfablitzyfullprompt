package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.FormFixtures;
import com.carddemo.TestFixtures;
import com.carddemo.api.dto.AccountDetails;
import com.carddemo.api.dto.AccountUpdateForm;
import com.carddemo.persistence.entity.CustomerEntity;
import org.junit.jupiter.api.Test;

/** 1300-SETUP-SCREEN-VARS of COACTVWC and 9500-STORE-FETCHED-DATA of COACTUPC. */
class AccountScreenMapperTest {

    private final AccountScreenMapper mapper = new AccountScreenMapper();

    @Test
    void detailsKeepTheFixedWidthKeysAndTheEditedAmounts() {
        AccountDetails details = mapper.toDetails(TestFixtures.account(), TestFixtures.customer(),
                TestFixtures.CARD_NUMBER);

        assertThat(details.accountId()).isEqualTo("11111111111");
        assertThat(details.customerId()).isEqualTo("100000001");
        assertThat(details.currentBalance().amount()).isEqualByComparingTo("1025.50");
        assertThat(details.currentBalance().display()).isEqualTo("+" + " ".repeat(6) + "1,025.50");
        assertThat(details.ssn()).isEqualTo("123456789");
        assertThat(details.ssnFormatted()).isEqualTo("123-45-6789");
        assertThat(details.cardNumber()).isEqualTo(TestFixtures.CARD_NUMBER);
    }

    @Test
    void aLeadingZeroSsnKeepsAllNineDigits() {
        CustomerEntity customer = TestFixtures.customer();
        customer.setSsn("000123456");

        assertThat(mapper.toDetails(TestFixtures.account(), customer, TestFixtures.CARD_NUMBER)
                .ssnFormatted()).isEqualTo("000-12-3456");
    }

    @Test
    void theFormSplitsDatesPhonesAndTheSsnIntoMapFields() {
        AccountUpdateForm form = mapper.toForm(TestFixtures.account(), TestFixtures.customer());

        assertThat(form).isEqualTo(FormFixtures.form());
    }

    @Test
    void missingStoredValuesProduceBlankMapFields() {
        CustomerEntity customer = TestFixtures.customer();
        customer.setDobYyyyMmDd(null);
        customer.setPhoneNum2(null);
        customer.setFicoCreditScore(null);

        AccountUpdateForm form = mapper.toForm(TestFixtures.account(), customer);

        assertThat(form.dobYear()).isBlank();
        assertThat(form.phone2Area()).isBlank();
        assertThat(form.ficoScore()).isEmpty();
    }

    @Test
    void keysAreZeroFilledToTheirPictureLength() {
        assertThat(AccountScreenMapper.formatAccountId(1L)).isEqualTo("00000000001");
        assertThat(AccountScreenMapper.formatAccountId(null)).isEmpty();
        assertThat(AccountScreenMapper.formatCustomerId(1L)).isEqualTo("000000001");
        assertThat(AccountScreenMapper.formatCustomerId(null)).isEmpty();
    }
}
