package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.carddemo.api.dto.CardListResponse;
import com.carddemo.api.dto.CardListResponse.CardListRow;
import com.carddemo.domain.CardScreenMessages;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.persistence.entity.CardEntity;
import com.carddemo.persistence.repository.CardRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** COCRDLIC, the Credit Card List transaction. */
@ExtendWith(MockitoExtension.class)
class CardListServiceTest {

    @Mock
    private CardRepository cardRepository;

    private CardListService service;

    @BeforeEach
    void setUp() {
        service = new CardListService(cardRepository, new CardKeyEditor());
    }

    @Test
    void theFirstPageShowsSevenRowsAndReportsThatMoreExist() {
        when(cardRepository.browseForwardFrom(any(), eq(null), any())).thenReturn(cards(8));

        CardListResponse response = service.list("", "", "FIRST", null, null, 1);

        assertThat(response.rows()).hasSize(CardListService.PAGE_SIZE);
        assertThat(response.pageNumber()).isEqualTo(1);
        assertThat(response.firstCardNumber()).isEqualTo("4111111111110001");
        assertThat(response.lastCardNumber()).isEqualTo("4111111111110007");
        assertThat(response.nextPageAvailable()).isTrue();
        assertThat(response.infoMessage()).isEqualTo(CardScreenMessages.LIST_ACTIONS);
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void theLastPageReportsThatNoFurtherRecordExists() {
        when(cardRepository.browseForwardFrom(any(), eq(null), any())).thenReturn(cards(3));

        CardListResponse response = service.list("", "", "FIRST", null, null, 1);

        assertThat(response.rows()).hasSize(3);
        assertThat(response.nextPageAvailable()).isFalse();
        assertThat(response.errorMessage()).isEqualTo(CardScreenMessages.NO_MORE_RECORDS);
    }

    @Test
    void anEmptyFirstPageReportsThatTheSearchFoundNothing() {
        when(cardRepository.browseForwardFrom(any(), eq(11111111111L), any())).thenReturn(List.of());

        CardListResponse response = service.list("11111111111", "", "FIRST", null, null, 1);

        assertThat(response.rows()).isEmpty();
        assertThat(response.errorMessage()).isEqualTo(CardScreenMessages.NO_RECORDS_FOUND);
    }

    /** PF8 restarts the browse after the last card number of the page that was shown. */
    @Test
    void theForwardKeyContinuesAfterTheLastCardNumberOfThePage() {
        when(cardRepository.browseForwardAfter(eq("4111111111110007"), eq(null), any()))
                .thenReturn(cards(2));

        CardListResponse response = service.list("", "", "NEXT", "4111111111110001",
                "4111111111110007", 1);

        assertThat(response.pageNumber()).isEqualTo(2);
        assertThat(response.rows()).hasSize(2);
    }

    @Test
    void theForwardKeyOnTheLastPageReportsThatNoPageFollows() {
        when(cardRepository.browseForwardAfter(eq("4111111111110007"), eq(null), any()))
                .thenReturn(List.of());

        CardListResponse response = service.list("", "", "NEXT", "4111111111110001",
                "4111111111110007", 1);

        assertThat(response.errorMessage()).isEqualTo(CardScreenMessages.NO_MORE_PAGES);
    }

    /** PF7 reads backwards and the map still shows the rows in ascending key order. */
    @Test
    void theBackwardKeyShowsThePreviousPageInKeyOrder() {
        when(cardRepository.browseBackwardBefore(eq("4111111111110008"), eq(null), any()))
                .thenReturn(cards(3).reversed());

        CardListResponse response = service.list("", "", "PREVIOUS", "4111111111110008", null, 2);

        assertThat(response.pageNumber()).isEqualTo(1);
        assertThat(response.rows()).extracting(CardListRow::cardNumber)
                .containsExactly("4111111111110001", "4111111111110002", "4111111111110003");
        assertThat(response.nextPageAvailable()).isTrue();
    }

    @Test
    void theBackwardKeyOnTheFirstPageReportsThatNoPagePrecedesIt() {
        CardListResponse response = service.list("", "", "PREVIOUS", "4111111111110001", null, 1);

        assertThat(response.rows()).isEmpty();
        assertThat(response.errorMessage()).isEqualTo(CardScreenMessages.NO_PREVIOUS_PAGES);
    }

    @Test
    void aNonNumericAccountFilterIsRefused() {
        assertThatThrownBy(() -> service.list("1111111111A", "", "FIRST", null, null, 1))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(CardScreenMessages.LIST_ACCOUNT_FILTER_INVALID);
    }

    @Test
    void aNonNumericCardFilterIsRefused() {
        assertThatThrownBy(() -> service.list("", "411111111111000X", "FIRST", null, null, 1))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(CardScreenMessages.LIST_CARD_FILTER_INVALID);
    }

    /** 9500-FILTER-RECORDS drops every record whose card number is not the one keyed. */
    @Test
    void theCardFilterKeepsOnlyTheCardThatWasKeyed() {
        when(cardRepository.browseForwardFrom(eq("4111111111110002"), eq(null), any()))
                .thenReturn(cards(4));

        CardListResponse response = service.list("", "4111111111110002", "FIRST", null, null, 1);

        assertThat(response.rows()).extracting(CardListRow::cardNumber)
                .containsExactly("4111111111110002");
    }

    private static List<CardEntity> cards(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(index -> {
            CardEntity card = new CardEntity();
            card.setCardNum("41111111111100%02d".formatted(index));
            card.setCardAcctId(11111111111L);
            card.setCardCvvCd(123);
            card.setCardEmbossedName("JOHN Q PUBLIC");
            card.setCardExpiraionDate("2026-03-01");
            card.setCardActiveStatus("Y");
            return card;
        }).toList();
    }
}
