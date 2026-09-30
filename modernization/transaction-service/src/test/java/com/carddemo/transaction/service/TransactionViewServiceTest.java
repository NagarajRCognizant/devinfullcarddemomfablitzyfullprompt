package com.carddemo.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.api.dto.TransactionDetailResponse;
import com.carddemo.transaction.domain.TransactionMessages;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** COTRN01C, the transaction view. */
@ExtendWith(MockitoExtension.class)
class TransactionViewServiceTest {

    @Mock
    private TransactionRepository transactions;

    @InjectMocks
    private TransactionViewService service;

    @Test
    void anEmptyIdentifierIsRejectedBeforeTheRead() {
        assertThatThrownBy(() -> service.view("  "))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(TransactionMessages.TRAN_ID_EMPTY);
    }

    @Test
    void aMissingRecordIsReportedAsNotFound() {
        when(transactions.findById("0000000000000999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.view("999"))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessage(TransactionMessages.TRANSACTION_NOT_FOUND);
    }

    @Test
    void theRecordIsShownWithTheEditedFieldsOfTheScreen() {
        when(transactions.findById("0000000000000001"))
                .thenReturn(Optional.of(TransactionFixtures.transaction("0000000000000001")));

        TransactionDetailResponse detail = service.view("1");

        assertThat(detail.tranId()).isEqualTo("0000000000000001");
        assertThat(detail.categoryCode()).isEqualTo("0001");
        assertThat(detail.merchantId()).isEqualTo("800000000");
        assertThat(detail.amount().display()).isEqualTo("+00000504.77");
        assertThat(detail.description()).isEqualTo("Purchase at Abshire-Lowe");
    }
}
