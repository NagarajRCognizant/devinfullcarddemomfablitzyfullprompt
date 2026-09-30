package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.carddemo.TestFixtures;
import com.carddemo.api.dto.AccountViewResponse;
import com.carddemo.domain.ScreenMessages;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.persistence.repository.AccountRepository;
import com.carddemo.persistence.repository.CardXrefRepository;
import com.carddemo.persistence.repository.CustomerRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** COACTVWC, the Account View transaction. */
@ExtendWith(MockitoExtension.class)
class AccountViewServiceTest {

    @Mock
    private CardXrefRepository cardXrefRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerRepository customerRepository;

    private AccountViewService service;

    @BeforeEach
    void setUp() {
        service = new AccountViewService(
                new AccountReadService(cardXrefRepository, accountRepository, customerRepository),
                new AccountScreenMapper());
    }

    @Test
    void theFirstTurnOnlyPrompts() {
        AccountViewResponse response = service.prompt();

        assertThat(response.details()).isNull();
        assertThat(response.infoMessage()).isEqualTo(ScreenMessages.VIEW_PROMPT_FOR_INPUT);
    }

    @Test
    void aKnownAccountIsDisplayedWithTheOutputMessage() {
        when(cardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumAsc(TestFixtures.ACCOUNT_ID))
                .thenReturn(Optional.of(TestFixtures.xref()));
        when(accountRepository.findById(TestFixtures.ACCOUNT_ID))
                .thenReturn(Optional.of(TestFixtures.account()));
        when(customerRepository.findById(TestFixtures.CUSTOMER_ID))
                .thenReturn(Optional.of(TestFixtures.customer()));

        AccountViewResponse response = service.view("11111111111");

        assertThat(response.infoMessage()).isEqualTo(ScreenMessages.VIEW_INFORM_OUTPUT);
        assertThat(response.details().accountId()).isEqualTo("11111111111");
        assertThat(response.details().cardNumber()).isEqualTo(TestFixtures.CARD_NUMBER);
    }

    @Test
    void aBlankAccountIdIsRejectedBeforeAnyRead() {
        assertThatThrownBy(() -> service.view(" "))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(ScreenMessages.PROMPT_FOR_ACCT);
    }

    @Test
    void anUnknownAccountIsNotFound() {
        when(cardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumAsc(99999999999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.view("99999999999"))
                .isInstanceOf(RecordNotFoundException.class);
    }
}
