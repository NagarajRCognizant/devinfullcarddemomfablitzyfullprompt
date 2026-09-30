package com.carddemo.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.CardholderView;
import com.carddemo.transaction.domain.TransactionMessages;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** VALIDATE-INPUT-KEY-FIELDS of COTRN02C, which reads CXACAIX or CCXREF. */
@ExtendWith(MockitoExtension.class)
class CardholderKeysTest {

    @Mock
    private AccountServiceClient accounts;

    @InjectMocks
    private CardholderKeys keys;

    @Test
    void keyingNeitherKeyIsRejected() {
        assertThatThrownBy(() -> keys.resolve(" ", ""))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(TransactionMessages.ACCOUNT_OR_CARD_REQUIRED);
    }

    @Test
    void aNonNumericAccountNumberIsRejectedBeforeTheRead() {
        assertThatThrownBy(() -> keys.resolve("0000000000A", null))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(TransactionMessages.ACCOUNT_ID_NOT_NUMERIC);
    }

    @Test
    void aNonNumericCardNumberIsRejectedBeforeTheRead() {
        assertThatThrownBy(() -> keys.resolve(null, "48594526128770XX"))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(TransactionMessages.CARD_NUMBER_NOT_NUMERIC);
    }

    @Test
    void anAccountWithoutACrossReferenceIsReportedAsNotFound() {
        when(accounts.byAccountId(1L)).thenReturn(CardholderView.notFound("1"));

        assertThatThrownBy(() -> keys.resolve("00000000001", null))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessage(TransactionMessages.ACCOUNT_NOT_FOUND);
    }

    @Test
    void aCardWithoutACrossReferenceIsReportedWithTheCardMessage() {
        when(accounts.byCardNumber(TransactionFixtures.CARD_NUMBER))
                .thenReturn(CardholderView.notFound(TransactionFixtures.CARD_NUMBER));

        assertThatThrownBy(() -> keys.resolve(null, TransactionFixtures.CARD_NUMBER))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessage(TransactionMessages.CARD_NOT_FOUND);
    }

    /** The account number takes precedence: the source edits it first and stops there. */
    @Test
    void theAccountNumberIsUsedWhenBothAreKeyed() {
        CardholderView view = TransactionFixtures.cardholder(new BigDecimal("100.00"));
        when(accounts.byAccountId(1L)).thenReturn(view);

        assertThat(keys.resolve("00000000001", TransactionFixtures.CARD_NUMBER)).isSameAs(view);
    }
}
