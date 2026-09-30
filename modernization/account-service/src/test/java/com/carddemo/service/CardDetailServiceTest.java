package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.carddemo.api.dto.CardDetailResponse;
import com.carddemo.domain.CardScreenMessages;
import com.carddemo.domain.ScreenMessages;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.persistence.entity.CardEntity;
import com.carddemo.persistence.repository.CardRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** COCRDSLC, the Credit Card View transaction. */
@ExtendWith(MockitoExtension.class)
class CardDetailServiceTest {

    @Mock
    private CardRepository cardRepository;

    private CardDetailService service;

    @BeforeEach
    void setUp() {
        service = new CardDetailService(cardRepository, new CardKeyEditor());
    }

    @Test
    void theRecordIsShownWhenBothKeysMatch() {
        when(cardRepository.findByCardNumAndCardAcctId("4111111111111111", 11111111111L))
                .thenReturn(Optional.of(card()));

        CardDetailResponse response = service.view("11111111111", "4111111111111111");

        assertThat(response.accountId()).isEqualTo("11111111111");
        assertThat(response.embossedName()).isEqualTo("JOHN Q PUBLIC");
        assertThat(response.expiryYear()).isEqualTo("2026");
        assertThat(response.expiryMonth()).isEqualTo("03");
        assertThat(response.cvvCode()).isEqualTo("123");
        assertThat(response.infoMessage()).isEqualTo(CardScreenMessages.VIEW_DISPLAYING_DETAILS);
    }

    @Test
    void aMissingAccountNumberIsPromptedFor() {
        assertThatThrownBy(() -> service.view("", "4111111111111111"))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(ScreenMessages.PROMPT_FOR_ACCT);
    }

    /** CC-ACCT-ID-N EQUAL ZEROS is one of the three "not supplied" conditions of the edit. */
    @Test
    void anAllZeroAccountNumberCountsAsMissing() {
        assertThatThrownBy(() -> service.view("00000000000", "4111111111111111"))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(ScreenMessages.PROMPT_FOR_ACCT);
    }

    @Test
    void aMissingCardNumberIsPromptedFor() {
        assertThatThrownBy(() -> service.view("11111111111", "*"))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(CardScreenMessages.PROMPT_FOR_CARD);
    }

    @Test
    void aCardThatDoesNotBelongToTheAccountIsNotFound() {
        when(cardRepository.findByCardNumAndCardAcctId("4111111111111111", 22222222222L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.view("22222222222", "4111111111111111"))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessage(CardScreenMessages.CARD_COMBINATION_NOT_FOUND);
    }

    static CardEntity card() {
        CardEntity card = new CardEntity();
        card.setCardNum("4111111111111111");
        card.setCardAcctId(11111111111L);
        card.setCardCvvCd(123);
        card.setCardEmbossedName("JOHN Q PUBLIC");
        card.setCardExpiraionDate("2026-03-01");
        card.setCardActiveStatus("Y");
        return card;
    }
}
