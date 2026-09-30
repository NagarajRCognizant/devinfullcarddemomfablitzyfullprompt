package com.carddemo.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.exception.ScreenValidationException;
import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.api.dto.TransactionAddRequest;
import com.carddemo.transaction.api.dto.TransactionAddResponse;
import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.domain.TransactionMessages;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** COTRN02C, the transaction add screen. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionAddServiceTest {

    @Mock
    private TransactionRepository transactions;
    @Mock
    private AccountServiceClient accounts;

    private TransactionAddService service;

    @BeforeEach
    void setUp() {
        service = new TransactionAddService(transactions, new CardholderKeys(accounts));
        when(accounts.byAccountId(TransactionFixtures.ACCOUNT_ID))
                .thenReturn(TransactionFixtures.cardholder(new BigDecimal("100.00")));
    }

    @Test
    void anUnconfirmedRequestOnlyAsksForTheConfirmation() {
        TransactionAddResponse response = service.add(TransactionFixtures.validAddRequest(""));

        assertThat(response.tranId()).isNull();
        assertThat(response.message()).isEqualTo(TransactionMessages.CONFIRM_ADD);
        verify(transactions, never()).save(any());
    }

    /** A confirmation of N clears the screen without a message and writes nothing. */
    @Test
    void aRefusedConfirmationWritesNothing() {
        TransactionAddResponse response = service.add(TransactionFixtures.validAddRequest("N"));

        assertThat(response.tranId()).isNull();
        assertThat(response.message()).isEmpty();
        verify(transactions, never()).save(any());
    }

    @Test
    void anythingOtherThanYesOrNoIsRejected() {
        assertThatThrownBy(() -> service.add(TransactionFixtures.validAddRequest("X")))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(TransactionMessages.INVALID_CONFIRMATION);
    }

    @Test
    void aConfirmedRequestIsWrittenWithTheKeyAfterTheHighestOnFile() {
        when(transactions.findHighestTransactionId())
                .thenReturn(Optional.of("0000000000000300"));
        when(transactions.existsById("0000000000000301")).thenReturn(false);

        TransactionAddResponse response = service.add(TransactionFixtures.validAddRequest("Y"));

        ArgumentCaptor<TransactionEntity> written = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactions).save(written.capture());
        assertThat(written.getValue().getTranId()).isEqualTo("0000000000000301");
        assertThat(written.getValue().getTranAmt()).isEqualByComparingTo(new BigDecimal("504.77"));
        assertThat(written.getValue().getTranCardNum()).isEqualTo(TransactionFixtures.CARD_NUMBER);
        assertThat(response.message())
                .isEqualTo(TransactionMessages.transactionAdded("0000000000000301"));
    }

    @Test
    void theFirstTransactionOnAnEmptyFileTakesKeyOne() {
        when(transactions.findHighestTransactionId()).thenReturn(Optional.empty());

        TransactionAddResponse response = service.add(TransactionFixtures.validAddRequest("Y"));

        assertThat(response.tranId()).isEqualTo("0000000000000001");
    }

    @Test
    void theDataFieldsAreEditedInTheOrderOfTheSource() {
        assertMessage(request("", "0001", "+00000504.77", "2022-06-10", "2022-06-10"),
                TransactionMessages.TYPE_CD_EMPTY);
        assertMessage(request("01", "", "+00000504.77", "2022-06-10", "2022-06-10"),
                TransactionMessages.CATEGORY_CD_EMPTY);
        assertMessage(request("0A", "0001", "+00000504.77", "2022-06-10", "2022-06-10"),
                TransactionMessages.TYPE_CD_NOT_NUMERIC);
        assertMessage(request("01", "00A1", "+00000504.77", "2022-06-10", "2022-06-10"),
                TransactionMessages.CATEGORY_CD_NOT_NUMERIC);
        assertMessage(request("01", "0001", "504.77", "2022-06-10", "2022-06-10"),
                TransactionMessages.AMOUNT_FORMAT);
        assertMessage(request("01", "0001", "+00000504.77", "10/06/2022", "2022-06-10"),
                TransactionMessages.ORIG_DATE_FORMAT);
        assertMessage(request("01", "0001", "+00000504.77", "2022-06-10", "10/06/2022"),
                TransactionMessages.PROC_DATE_FORMAT);
        assertMessage(request("01", "0001", "+00000504.77", "2022-02-30", "2022-06-10"),
                TransactionMessages.ORIG_DATE_INVALID);
        assertMessage(request("01", "0001", "+00000504.77", "2022-06-10", "2022-02-30"),
                TransactionMessages.PROC_DATE_INVALID);
    }

    @Test
    void theLastTransactionOnFileFillsTheMap() {
        when(transactions.findHighestTransactionId()).thenReturn(Optional.of("0000000000000300"));
        when(transactions.findById("0000000000000300"))
                .thenReturn(Optional.of(TransactionFixtures.transaction("0000000000000300")));

        TransactionAddRequest copied = service.copyLast("00000000001", null);

        assertThat(copied.typeCode()).isEqualTo("01");
        assertThat(copied.categoryCode()).isEqualTo("0001");
        assertThat(copied.amount()).isEqualTo("+00000504.77");
        assertThat(copied.merchantId()).isEqualTo("800000000");
        assertThat(copied.confirm()).isEmpty();
    }

    @Test
    void anEmptyFileLeavesTheCopiedMapBlank() {
        when(transactions.findHighestTransactionId()).thenReturn(Optional.empty());

        TransactionAddRequest copied = service.copyLast("00000000001", null);

        assertThat(copied.cardNumber()).isEqualTo(TransactionFixtures.CARD_NUMBER);
        assertThat(copied.typeCode()).isEmpty();
        assertThat(copied.amount()).isEmpty();
    }

    private void assertMessage(TransactionAddRequest request, String message) {
        assertThatThrownBy(() -> service.add(request))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(message);
    }

    private static TransactionAddRequest request(String typeCode, String categoryCode,
            String amount, String originalDate, String processedDate) {
        return new TransactionAddRequest("00000000001", null, typeCode, categoryCode, "POS TERM",
                "Purchase at Abshire-Lowe", amount, originalDate, processedDate, "800000000",
                "Abshire-Lowe", "North Enoshaven", "72112", "Y");
    }
}
