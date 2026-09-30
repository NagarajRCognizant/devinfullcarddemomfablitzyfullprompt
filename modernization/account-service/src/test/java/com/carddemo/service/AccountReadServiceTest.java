package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.TestFixtures;
import com.carddemo.domain.ScreenMessages;
import com.carddemo.domain.validation.FieldFlag;
import com.carddemo.domain.validation.ScreenField;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.persistence.repository.AccountRepository;
import com.carddemo.persistence.repository.CardXrefRepository;
import com.carddemo.persistence.repository.CustomerRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 2210-EDIT-ACCOUNT / 1210-EDIT-ACCOUNT and the 9000-READ-ACCT sequence. */
@ExtendWith(MockitoExtension.class)
class AccountReadServiceTest {

    @Mock
    private CardXrefRepository cardXrefRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerRepository customerRepository;
    @InjectMocks
    private AccountReadService service;

    @Test
    void anElevenDigitAccountIdPassesTheEdit() {
        assertThat(service.editAccountId("11111111111", false)).isEqualTo(TestFixtures.ACCOUNT_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "*", " * "})
    void anAbsentAccountIdIsItsOwnError(String keyed) {
        assertThatThrownBy(() -> service.editAccountId(keyed, false))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(ScreenMessages.PROMPT_FOR_ACCT)
                .extracting(failure -> ((ScreenValidationException) failure).getFieldFlags())
                .isEqualTo(java.util.Map.of(ScreenField.ACCOUNT_ID, FieldFlag.BLANK));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "123456789012", "1111111111A", "00000000000"})
    void aNonNumericShortOrZeroAccountIdIsRejected(String keyed) {
        assertThatThrownBy(() -> service.editAccountId(keyed, false))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(ScreenMessages.VIEW_ACCT_FILTER_NOT_VALID);
    }

    @Test
    void eachProgramUsesItsOwnMessage() {
        assertThatThrownBy(() -> service.editAccountId("abc", true))
                .hasMessage(ScreenMessages.UPDATE_ACCT_FILTER_NOT_VALID);
    }

    @Test
    void theReadSequenceIsCrossReferenceThenAccountThenCustomer() {
        when(cardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumAsc(TestFixtures.ACCOUNT_ID))
                .thenReturn(Optional.of(TestFixtures.xref()));
        when(accountRepository.findById(TestFixtures.ACCOUNT_ID))
                .thenReturn(Optional.of(TestFixtures.account()));
        when(customerRepository.findById(TestFixtures.CUSTOMER_ID))
                .thenReturn(Optional.of(TestFixtures.customer()));

        AccountRecords records = service.readAccount(TestFixtures.ACCOUNT_ID);

        assertThat(records.xref().getXrefCardNum()).isEqualTo(TestFixtures.CARD_NUMBER);
        assertThat(records.account().getAcctId()).isEqualTo(TestFixtures.ACCOUNT_ID);
        assertThat(records.customer().getCustId()).isEqualTo(TestFixtures.CUSTOMER_ID);
    }

    @Test
    void aMissingCrossReferenceStopsBeforeTheAccountRead() {
        when(cardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumAsc(TestFixtures.ACCOUNT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.readAccount(TestFixtures.ACCOUNT_ID))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessage("Account:11111111111 not found in Cross ref file.")
                .asInstanceOf(throwable(RecordNotFoundException.class))
                .extracting(RecordNotFoundException::getFieldFlags)
                .isEqualTo(Map.of(ScreenField.ACCOUNT_ID, FieldFlag.NOT_OK));
        verify(accountRepository, never()).findById(anyLong());
        verify(customerRepository, never()).findById(anyLong());
    }

    @Test
    void aMissingAccountStopsBeforeTheCustomerRead() {
        when(cardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumAsc(TestFixtures.ACCOUNT_ID))
                .thenReturn(Optional.of(TestFixtures.xref()));
        when(accountRepository.findById(TestFixtures.ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.readAccount(TestFixtures.ACCOUNT_ID))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessage("Account:11111111111 not found in Acct Master file.")
                .asInstanceOf(throwable(RecordNotFoundException.class))
                .extracting(RecordNotFoundException::getFieldFlags)
                .isEqualTo(Map.of(ScreenField.ACCOUNT_ID, FieldFlag.NOT_OK));
        verify(customerRepository, never()).findById(anyLong());
    }

    @Test
    void aMissingCustomerIsReportedWithTheCustomerIdFromTheCrossReference() {
        when(cardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumAsc(TestFixtures.ACCOUNT_ID))
                .thenReturn(Optional.of(TestFixtures.xref()));
        when(accountRepository.findById(TestFixtures.ACCOUNT_ID))
                .thenReturn(Optional.of(TestFixtures.account()));
        when(customerRepository.findById(TestFixtures.CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.readAccount(TestFixtures.ACCOUNT_ID))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessage("CustId:100000001 not found in customer master.")
                .asInstanceOf(throwable(RecordNotFoundException.class))
                .extracting(RecordNotFoundException::getFieldFlags)
                .isEqualTo(Map.of(ScreenField.CUSTOMER_ID, FieldFlag.NOT_OK));
    }
}
