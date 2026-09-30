package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.api.dto.CardDetailResponse;
import com.carddemo.api.dto.CardUpdateRequest;
import com.carddemo.api.dto.CardUpdateResponse;
import com.carddemo.domain.CardScreenMessages;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.persistence.entity.CardEntity;
import com.carddemo.persistence.repository.CardRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** COCRDUPC, the Credit Card Update transaction. */
@ExtendWith(MockitoExtension.class)
class CardUpdateServiceTest {

    @Mock
    private CardRepository cardRepository;

    private CardUpdateService service;

    @BeforeEach
    void setUp() {
        CardKeyEditor keyEditor = new CardKeyEditor();
        service = new CardUpdateService(cardRepository, new CardDetailService(cardRepository, keyEditor),
                keyEditor);
    }

    @Test
    void theFirstTurnShowsTheRecordToBeChanged() {
        givenTheCardOnFile();

        CardDetailResponse response = service.fetch("11111111111", "4111111111111111");

        assertThat(response.embossedName()).isEqualTo("JOHN Q PUBLIC");
        assertThat(response.infoMessage()).isEqualTo(CardScreenMessages.UPDATE_DETAILS_SHOWN);
    }

    @Test
    void theEditTurnAsksForTheSaveConfirmationWithoutWriting() {
        givenTheCardOnFile();

        CardUpdateResponse response = service.validate(request("JOHN Q CITIZEN", "Y", "2026", "03", null));

        assertThat(response.updated()).isFalse();
        assertThat(response.message()).isEqualTo(CardScreenMessages.PROMPT_FOR_CONFIRMATION);
        verify(cardRepository, never()).saveAndFlush(any());
    }

    @Test
    void theSaveTurnRewritesTheRecord() {
        CardEntity card = givenTheCardOnFile();
        when(cardRepository.saveAndFlush(card)).thenReturn(card);

        CardUpdateResponse response = service.save(request("JANE DOE", "N", "2030", "12", fetched()));

        assertThat(response.updated()).isTrue();
        assertThat(response.message()).isEqualTo(CardScreenMessages.UPDATE_SUCCESS);
        assertThat(card.getCardActiveStatus()).isEqualTo("N");
        // Only the year and the month are keyed, so the day of the record is carried forward.
        assertThat(card.getCardExpiraionDate()).isEqualTo("2030-12-01");
        assertThat(card.getCardEmbossedName()).startsWith("JANE DOE");
        assertThat(card.getCardEmbossedName()).hasSize(50);
    }

    @Test
    void aTurnThatChangesNothingIsRefused() {
        givenTheCardOnFile();

        assertThatThrownBy(() -> service.save(request("JOHN Q PUBLIC", "Y", "2026", "03", fetched())))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(CardScreenMessages.NO_CHANGES_DETECTED);
    }

    @Test
    void aBlankCardNameIsPromptedFor() {
        givenTheCardOnFile();

        assertThatThrownBy(() -> service.validate(request(" ", "Y", "2026", "03", null)))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(CardScreenMessages.PROMPT_FOR_NAME);
    }

    @Test
    void aCardNameWithDigitsIsRefused() {
        givenTheCardOnFile();

        assertThatThrownBy(() -> service.validate(request("JOHN 3RD", "Y", "2026", "03", null)))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(CardScreenMessages.NAME_MUST_BE_ALPHA);
    }

    @Test
    void aStatusOtherThanYesOrNoIsRefused() {
        givenTheCardOnFile();

        assertThatThrownBy(() -> service.validate(request("JOHN Q PUBLIC", "X", "2026", "03", null)))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(CardScreenMessages.STATUS_MUST_BE_YES_NO);
    }

    @Test
    void anExpiryMonthOutsideOneToTwelveIsRefused() {
        givenTheCardOnFile();

        assertThatThrownBy(() -> service.validate(request("JOHN Q PUBLIC", "Y", "2026", "13", null)))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(CardScreenMessages.EXPIRY_MONTH_NOT_VALID);
    }

    @Test
    void aNonNumericExpiryMonthIsRefused() {
        givenTheCardOnFile();

        assertThatThrownBy(() -> service.validate(request("JOHN Q PUBLIC", "Y", "2026", "1A", null)))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(CardScreenMessages.EXPIRY_MONTH_NOT_VALID);
    }

    /** 88 VALID-YEAR VALUES 1950 THRU 2099. */
    @Test
    void anExpiryYearOutsideTheAcceptedRangeIsRefused() {
        givenTheCardOnFile();

        assertThatThrownBy(() -> service.validate(request("JOHN Q PUBLIC", "Y", "2199", "03", null)))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(CardScreenMessages.EXPIRY_YEAR_NOT_VALID);
    }

    /** 9600-WRITE-PROCESSING compares the re-read record with the one the map was filled from. */
    @Test
    void aRecordChangedSinceItWasFetchedIsNotOverwritten() {
        givenTheCardOnFile();
        CardDetailResponse stale = new CardDetailResponse("11111111111", "4111111111111111",
                "SOMEONE ELSE", "Y", "2026", "03", "01", "123", null);

        assertThatThrownBy(() -> service.save(request("JANE DOE", "Y", "2026", "03", stale)))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(CardScreenMessages.CHANGED_BY_SOMEONE_ELSE);
        verify(cardRepository, never()).saveAndFlush(any());
    }

    private CardEntity givenTheCardOnFile() {
        CardEntity card = CardDetailServiceTest.card();
        when(cardRepository.findByCardNumAndCardAcctId("4111111111111111", 11111111111L))
                .thenReturn(Optional.of(card));
        return card;
    }

    private static CardDetailResponse fetched() {
        return new CardDetailResponse("11111111111", "4111111111111111", "JOHN Q PUBLIC", "Y",
                "2026", "03", "01", "123", null);
    }

    private static CardUpdateRequest request(String name, String status, String year, String month,
                                             CardDetailResponse fetched) {
        return new CardUpdateRequest("11111111111", "4111111111111111", name, status, year, month, fetched);
    }
}
