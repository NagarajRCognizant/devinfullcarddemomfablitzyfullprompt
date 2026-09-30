package com.carddemo.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.exception.ScreenValidationException;
import com.carddemo.exception.UpdateFailedException;
import com.carddemo.persistence.entity.BillPaymentEntity;
import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.persistence.repository.BillPaymentRepository;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.api.dto.BillPaymentRequest;
import com.carddemo.transaction.api.dto.BillPaymentResponse;
import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.AccountServiceException;
import com.carddemo.transaction.client.BalanceAdjustment;
import com.carddemo.transaction.client.BalanceAdjustmentResult;
import com.carddemo.transaction.domain.TransactionMessages;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** COBIL00C, the online bill payment of the whole balance. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillPaymentServiceTest {

    private static final String KEY = "bill-pay-0000000001-1";
    private static final BigDecimal BALANCE = new BigDecimal("1200.50");

    @Mock
    private TransactionRepository transactions;
    @Mock
    private BillPaymentRepository billPayments;
    @Mock
    private AccountServiceClient accounts;

    private BillPaymentService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T05:07:00Z"), ZoneOffset.UTC);
        service = new BillPaymentService(transactions, billPayments, accounts, clock);
        when(accounts.byAccountId(TransactionFixtures.ACCOUNT_ID))
                .thenReturn(TransactionFixtures.cardholder(BALANCE));
        when(transactions.findHighestTransactionId()).thenReturn(Optional.of("0000000000000300"));
        when(accounts.adjustBalance(anyLong(), any()))
                .thenReturn(new BalanceAdjustmentResult(TransactionFixtures.ACCOUNT_ID, true,
                        BigDecimal.ZERO, BigDecimal.ZERO, BALANCE));
    }

    @Test
    void anEmptyAccountNumberIsRejected() {
        assertThatThrownBy(() -> service.balance("  "))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(TransactionMessages.ACCT_ID_EMPTY);
    }

    @Test
    void theBalanceEnquiryAsksForAConfirmation() {
        BillPaymentResponse response = service.balance("00000000001");

        assertThat(response.paid()).isFalse();
        assertThat(response.currentBalance().amount()).isEqualByComparingTo(BALANCE);
        assertThat(response.message()).isEqualTo(TransactionMessages.CONFIRM_PAYMENT);
    }

    @Test
    void aBalanceThatIsNotPositiveHasNothingToPay() {
        when(accounts.byAccountId(TransactionFixtures.ACCOUNT_ID))
                .thenReturn(TransactionFixtures.cardholder(BigDecimal.ZERO));

        assertThatThrownBy(() -> service.balance("00000000001"))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(TransactionMessages.NOTHING_TO_PAY);
    }

    @Test
    void aRefusedConfirmationPaysNothing() {
        BillPaymentResponse response = service.pay(
                new BillPaymentRequest("00000000001", "N", KEY));

        assertThat(response.paid()).isFalse();
        assertThat(response.message()).isEmpty();
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void anythingOtherThanYesOrNoIsRejected() {
        assertThatThrownBy(() -> service.pay(new BillPaymentRequest("00000000001", "X", KEY)))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(TransactionMessages.INVALID_CONFIRMATION);
    }

    @Test
    void aConfirmedPaymentWritesTheFixedLiteralsOfTheSource() {
        BillPaymentResponse response = service.pay(
                new BillPaymentRequest("00000000001", "Y", KEY));

        ArgumentCaptor<TransactionEntity> written = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactions).saveAndFlush(written.capture());
        TransactionEntity transaction = written.getValue();
        assertThat(transaction.getTranId()).isEqualTo("0000000000000301");
        assertThat(transaction.getTranTypeCd()).isEqualTo(BillPaymentService.TRAN_TYPE_CD);
        assertThat(transaction.getTranCatCd()).isEqualTo(BillPaymentService.TRAN_CAT_CD);
        assertThat(transaction.getTranSource()).isEqualTo(BillPaymentService.TRAN_SOURCE);
        assertThat(transaction.getTranDesc()).isEqualTo(BillPaymentService.TRAN_DESC);
        assertThat(transaction.getTranMerchantId()).isEqualTo(BillPaymentService.MERCHANT_ID);
        assertThat(transaction.getTranMerchantName()).isEqualTo(BillPaymentService.MERCHANT_NAME);
        assertThat(transaction.getTranMerchantCity()).isEqualTo(BillPaymentService.MERCHANT_CITY);
        assertThat(transaction.getTranMerchantZip()).isEqualTo(BillPaymentService.MERCHANT_ZIP);
        assertThat(transaction.getTranAmt()).isEqualByComparingTo(BALANCE);
        assertThat(transaction.getTranOrigTs()).startsWith("2026-09-24-05.07.00");

        ArgumentCaptor<BalanceAdjustment> adjustment =
                ArgumentCaptor.forClass(BalanceAdjustment.class);
        verify(accounts).adjustBalance(anyLong(), adjustment.capture());
        assertThat(adjustment.getValue().idempotencyKey()).isEqualTo(KEY);
        assertThat(adjustment.getValue().amount()).isEqualByComparingTo(BALANCE);

        assertThat(response.paid()).isTrue();
        assertThat(response.message())
                .isEqualTo(TransactionMessages.paymentSuccessful("0000000000000301"));
    }

    /**
     * The account rewrite and the transaction write shared one CICS unit of recovery. They no
     * longer can, so a failed account call removes the transaction again and marks the payment
     * compensated: the pair still ends in one of the two states the source could reach.
     */
    @Test
    void aFailedAccountCallCompensatesTheTransaction() {
        when(accounts.adjustBalance(anyLong(), any()))
                .thenThrow(new AccountServiceException("the account service is unreachable"));

        assertThatThrownBy(() -> service.pay(new BillPaymentRequest("00000000001", "Y", KEY)))
                .isInstanceOf(UpdateFailedException.class)
                .hasMessage(TransactionMessages.UNABLE_TO_UPDATE_ACCOUNT);

        verify(transactions).delete(any(TransactionEntity.class));
        ArgumentCaptor<BillPaymentEntity> payment =
                ArgumentCaptor.forClass(BillPaymentEntity.class);
        verify(billPayments).save(payment.capture());
        assertThat(payment.getValue().getStatus()).isEqualTo(BillPaymentEntity.COMPENSATED);
    }

    /** The screen could be submitted twice; the key makes the second submission a replay. */
    @Test
    void aResubmittedPaymentIsNotPostedTwice() {
        when(billPayments.findById(KEY)).thenReturn(Optional.of(new BillPaymentEntity(KEY,
                TransactionFixtures.ACCOUNT_ID, "0000000000000301", BALANCE,
                BillPaymentEntity.POSTED, Instant.parse("2026-09-24T05:00:00Z"))));

        BillPaymentResponse response = service.pay(
                new BillPaymentRequest("00000000001", "Y", KEY));

        assertThat(response.paid()).isTrue();
        assertThat(response.tranId()).isEqualTo("0000000000000301");
        verify(transactions, never()).saveAndFlush(any());
        verify(accounts, never()).adjustBalance(anyLong(), any());
    }
}
