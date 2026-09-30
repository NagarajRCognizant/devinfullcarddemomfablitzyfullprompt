package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.FormFixtures;
import com.carddemo.TestFixtures;
import com.carddemo.api.dto.AccountUpdateForm;
import com.carddemo.api.dto.AccountUpdateRequest;
import com.carddemo.api.dto.AccountUpdateResponse;
import com.carddemo.api.dto.AccountUpdateStatus;
import com.carddemo.domain.ScreenMessages;
import com.carddemo.domain.reference.LookupTables;
import com.carddemo.domain.validation.DateEditor;
import com.carddemo.domain.validation.DateValidationService;
import com.carddemo.domain.validation.FieldEditor;
import com.carddemo.exception.UpdateFailedException;
import com.carddemo.persistence.entity.AccountEntity;
import com.carddemo.persistence.entity.CustomerEntity;
import com.carddemo.persistence.repository.AccountRepository;
import com.carddemo.persistence.repository.CardXrefRepository;
import com.carddemo.persistence.repository.CustomerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** The edit / confirm / rewrite lifecycle of COACTUPC. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountUpdateServiceTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2024-06-15T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private CardXrefRepository cardXrefRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerRepository customerRepository;

    private final AccountScreenMapper mapper = new AccountScreenMapper();
    private AccountUpdateService service;
    private AccountEntity storedAccount;
    private CustomerEntity storedCustomer;

    @BeforeEach
    void setUp() {
        storedAccount = TestFixtures.account();
        storedCustomer = TestFixtures.customer();
        AccountReadService readService =
                new AccountReadService(cardXrefRepository, accountRepository, customerRepository);
        service = new AccountUpdateService(readService, mapper, new AccountChangeDetector(),
                new AccountUpdateValidator(new FieldEditor(new LookupTables()),
                        new DateEditor(new DateValidationService(), FIXED)),
                accountRepository, customerRepository);

        when(cardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumAsc(TestFixtures.ACCOUNT_ID))
                .thenReturn(Optional.of(TestFixtures.xref()));
        when(accountRepository.findById(TestFixtures.ACCOUNT_ID))
                .thenReturn(Optional.of(storedAccount));
        when(customerRepository.findById(TestFixtures.CUSTOMER_ID))
                .thenReturn(Optional.of(storedCustomer));
        when(accountRepository.lockByAcctId(TestFixtures.ACCOUNT_ID))
                .thenReturn(Optional.of(storedAccount));
        when(customerRepository.lockByCustId(TestFixtures.CUSTOMER_ID))
                .thenReturn(Optional.of(storedCustomer));
    }

    @Test
    void fetchReturnsTheStoredValuesInBothBlocks() {
        AccountUpdateResponse response = service.fetch("11111111111");

        assertThat(response.status()).isEqualTo(AccountUpdateStatus.DETAILS_FETCHED);
        assertThat(response.original()).isEqualTo(response.updated()).isEqualTo(FormFixtures.form());
        assertThat(response.details()).isNotNull();
        assertThat(response.infoMessage()).isEqualTo(ScreenMessages.PROMPT_FOR_CHANGES);
    }

    @Test
    void anUnchangedSubmissionNeverReachesTheEdits() {
        AccountUpdateResponse response = service.validate(request(FormFixtures.form()));

        assertThat(response.status()).isEqualTo(AccountUpdateStatus.NO_CHANGES);
        assertThat(response.errorMessage()).isEqualTo(ScreenMessages.NO_CHANGES_DETECTED);
    }

    @Test
    void aFailingEditIsReportedWithItsFieldFlags() {
        AccountUpdateResponse response = service.validate(request(withActiveStatus("X")));

        assertThat(response.status()).isEqualTo(AccountUpdateStatus.VALIDATION_ERROR);
        assertThat(response.errorMessage()).isEqualTo("Account Status must be Y or N.");
        assertThat(response.fieldFlags()).isNotEmpty();
    }

    @Test
    void validatedChangesAskForConfirmation() {
        AccountUpdateResponse response = service.validate(request(withActiveStatus("N")));

        assertThat(response.status()).isEqualTo(AccountUpdateStatus.CHANGES_VALIDATED);
        assertThat(response.infoMessage()).isEqualTo(ScreenMessages.PROMPT_FOR_CONFIRMATION);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmRewritesTheAccountBeforeTheCustomer() {
        AccountUpdateResponse response = service.confirm(request(withActiveStatus("N")));

        assertThat(response.status()).isEqualTo(AccountUpdateStatus.CHANGES_COMMITTED);
        assertThat(response.infoMessage()).isEqualTo(ScreenMessages.CONFIRM_UPDATE_SUCCESS);
        assertThat(storedAccount.getActiveStatus()).isEqualTo("N");
        assertThat(storedCustomer.getPhoneNum1()).isEqualTo("(212)555-1234");

        InOrder order = inOrder(accountRepository, customerRepository);
        order.verify(accountRepository).lockByAcctId(TestFixtures.ACCOUNT_ID);
        order.verify(customerRepository).lockByCustId(TestFixtures.CUSTOMER_ID);
        order.verify(accountRepository).saveAndFlush(storedAccount);
        order.verify(customerRepository).saveAndFlush(storedCustomer);
    }

    @Test
    void confirmRerunsTheEditsSoAnInvalidSubmissionIsNeverWritten() {
        AccountUpdateResponse response = service.confirm(request(withActiveStatus("X")));

        assertThat(response.status()).isEqualTo(AccountUpdateStatus.VALIDATION_ERROR);
        verify(accountRepository, never()).lockByAcctId(TestFixtures.ACCOUNT_ID);
    }

    @Test
    void confirmReportsAnUnchangedSubmission() {
        assertThat(service.confirm(request(FormFixtures.form())).status())
                .isEqualTo(AccountUpdateStatus.NO_CHANGES);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void aRecordThatMovedSinceItWasFetchedIsNotOverwritten() {
        storedAccount.setCreditLimit(new java.math.BigDecimal("7500.00"));

        AccountUpdateResponse response = service.confirm(request(withActiveStatus("N")));

        assertThat(response.status()).isEqualTo(AccountUpdateStatus.RECORD_CHANGED);
        assertThat(response.errorMessage()).isEqualTo(ScreenMessages.DATA_WAS_CHANGED_BEFORE_UPDATE);
        assertThat(response.updated().creditLimit()).isEqualTo("7500.00");
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void anUnavailableAccountLockStopsBeforeTheCustomerLock() {
        when(accountRepository.lockByAcctId(TestFixtures.ACCOUNT_ID)).thenReturn(Optional.empty());

        AccountUpdateResponse response = service.confirm(request(withActiveStatus("N")));

        assertThat(response.status()).isEqualTo(AccountUpdateStatus.LOCK_ERROR);
        assertThat(response.errorMessage()).isEqualTo(ScreenMessages.COULD_NOT_LOCK_ACCT_FOR_UPDATE);
        verify(customerRepository, never()).lockByCustId(TestFixtures.CUSTOMER_ID);
    }

    @Test
    void anUnavailableCustomerLockStopsBeforeAnyRewrite() {
        when(customerRepository.lockByCustId(TestFixtures.CUSTOMER_ID)).thenReturn(Optional.empty());

        AccountUpdateResponse response = service.confirm(request(withActiveStatus("N")));

        assertThat(response.status()).isEqualTo(AccountUpdateStatus.LOCK_ERROR);
        assertThat(response.errorMessage()).isEqualTo(ScreenMessages.COULD_NOT_LOCK_CUST_FOR_UPDATE);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void aFailingCustomerRewriteRollsTheUnitOfWorkBack() {
        when(customerRepository.saveAndFlush(storedCustomer))
                .thenThrow(new IllegalStateException("rewrite rejected"));

        assertThatThrownBy(() -> service.confirm(request(withActiveStatus("N"))))
                .isInstanceOf(UpdateFailedException.class)
                .hasMessage(ScreenMessages.LOCKED_BUT_UPDATE_FAILED)
                .hasRootCauseMessage("rewrite rejected");
    }

    private AccountUpdateRequest request(AccountUpdateForm updated) {
        return new AccountUpdateRequest(FormFixtures.form(), updated);
    }

    private static AccountUpdateForm withActiveStatus(String activeStatus) {
        AccountUpdateForm form = FormFixtures.form();
        return new AccountUpdateForm(form.accountId(), activeStatus, form.currentBalance(),
                form.creditLimit(), form.cashCreditLimit(), form.openYear(), form.openMonth(),
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
