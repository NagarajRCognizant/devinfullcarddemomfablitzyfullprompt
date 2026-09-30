package com.carddemo.service;

import com.carddemo.api.dto.CardListResponse;
import com.carddemo.api.dto.CardListResponse.CardListRow;
import com.carddemo.cobol.CobolText;
import com.carddemo.domain.CardScreenMessages;
import com.carddemo.persistence.entity.CardEntity;
import com.carddemo.persistence.repository.CardRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of COCRDLIC, the Credit Card List transaction (CCLI).
 *
 * <p>The program browses CARDDAT in card number order, applies the account and card filters of
 * 9500-FILTER-RECORDS to every record it reads, and fills a map of seven rows. PF8 restarts the
 * browse after the last card number of the page and PF7 restarts it backwards before the first,
 * which is why the page keys travel in the response and come back on the next request instead of
 * an offset: an offset would skip or repeat rows when the file changes between turns, exactly as
 * the browse did not.
 *
 * <p>The extra READNEXT the source performs once a page is full, only to learn whether a further
 * record exists, becomes a request for one row more than the page holds.
 */
@Service
public class CardListService {

    /** WS-MAX-SCREEN-LINES. */
    public static final int PAGE_SIZE = 7;

    private final CardRepository cardRepository;
    private final CardKeyEditor keyEditor;

    public CardListService(CardRepository cardRepository, CardKeyEditor keyEditor) {
        this.cardRepository = cardRepository;
        this.keyEditor = keyEditor;
    }

    /**
     * One turn of the transaction.
     *
     * @param direction FIRST for the initial map, NEXT for PF8 and PREVIOUS for PF7
     */
    @Transactional(readOnly = true)
    public CardListResponse list(String accountFilter, String cardFilter, String direction,
                                 String firstCardNumber, String lastCardNumber, int pageNumber) {
        Long accountId = keyEditor.editAccountFilter(accountFilter);
        String cardNumber = keyEditor.editCardFilter(cardFilter);
        return "PREVIOUS".equals(direction)
                ? readBackwards(accountId, cardNumber, firstCardNumber, pageNumber)
                : readForward(accountId, cardNumber, direction, lastCardNumber, pageNumber);
    }

    /** 9000-READ-FORWARD. */
    private CardListResponse readForward(Long accountId, String cardNumber, String direction,
                                         String lastCardNumber, int pageNumber) {
        boolean paging = "NEXT".equals(direction) && !CobolText.isBlank(lastCardNumber);
        // One row more than the map holds is the extra READNEXT that sets CA-NEXT-PAGE-EXISTS.
        Limit limit = Limit.of(PAGE_SIZE + 1);
        List<CardEntity> read = paging
                ? cardRepository.browseForwardAfter(lastCardNumber, accountId, limit)
                : cardRepository.browseForwardFrom(startKey(cardNumber), accountId, limit);
        List<CardEntity> filtered = filter(read, cardNumber);
        boolean nextPageExists = filtered.size() > PAGE_SIZE;
        List<CardEntity> page = filtered.subList(0, Math.min(PAGE_SIZE, filtered.size()));
        int shownPage = page.isEmpty() ? Math.max(pageNumber, 1) : (paging ? pageNumber + 1 : 1);
        if (page.isEmpty()) {
            // 1400-SETUP-MESSAGE: an empty first page has no records at all, a later one has no more.
            String message = paging ? CardScreenMessages.NO_MORE_PAGES : CardScreenMessages.NO_RECORDS_FOUND;
            return new CardListResponse(List.of(), shownPage, null, null, false, message, null);
        }
        return page(page, shownPage, nextPageExists);
    }

    /** 9100-READ-BACKWARDS: PF7 reads backwards from the first card number of the page shown. */
    private CardListResponse readBackwards(Long accountId, String cardNumber, String firstCardNumber,
                                           int pageNumber) {
        if (pageNumber <= 1 || CobolText.isBlank(firstCardNumber)) {
            return new CardListResponse(List.of(), Math.max(pageNumber, 1), firstCardNumber, null, true,
                    CardScreenMessages.NO_PREVIOUS_PAGES, null);
        }
        List<CardEntity> read = cardRepository.browseBackwardBefore(firstCardNumber, accountId,
                Limit.of(PAGE_SIZE + 1));
        List<CardEntity> filtered = filter(read, cardNumber);
        List<CardEntity> page = new ArrayList<>(filtered.subList(0, Math.min(PAGE_SIZE, filtered.size())));
        if (page.isEmpty()) {
            return new CardListResponse(List.of(), pageNumber, firstCardNumber, null, true,
                    CardScreenMessages.NO_PREVIOUS_PAGES, null);
        }
        // The backwards browse delivers descending keys; the map always shows ascending ones.
        Collections.reverse(page);
        // A page reached by PF7 always has the page it came from ahead of it.
        return page(page, pageNumber - 1, true);
    }

    private CardListResponse page(List<CardEntity> page, int pageNumber, boolean nextPageExists) {
        List<CardListRow> rows = page.stream().map(CardListService::row).toList();
        // 1400-SETUP-MESSAGE sets WS-INFORM-REC-ACTIONS whenever rows are shown; the end of the
        // file is reported at the same time, which is why both lines can travel together.
        String error = nextPageExists ? null : CardScreenMessages.NO_MORE_RECORDS;
        return new CardListResponse(rows, pageNumber,
                page.get(0).getCardNum(), page.get(page.size() - 1).getCardNum(),
                nextPageExists, error, CardScreenMessages.LIST_ACTIONS);
    }

    /**
     * 9500-FILTER-RECORDS. The account filter is pushed into the query because the source reads
     * the account alternate index for it; the card filter is applied here, as the source does,
     * because it excludes records the browse has already delivered.
     */
    private static List<CardEntity> filter(List<CardEntity> read, String cardNumber) {
        return cardNumber == null
                ? read
                : read.stream().filter(card -> cardNumber.equals(card.getCardNum())).toList();
    }

    /** STARTBR RIDFLD: the browse starts at the card filter, or at the start of the file. */
    private static String startKey(String cardNumber) {
        return cardNumber == null ? " ".repeat(CardKeyEditor.CARD_NUMBER_LENGTH) : cardNumber;
    }

    private static CardListRow row(CardEntity card) {
        return new CardListRow(card.getCardNum(),
                CobolText.padLeftZero(Long.toString(card.getCardAcctId()), CardKeyEditor.ACCOUNT_ID_LENGTH),
                card.getCardActiveStatus());
    }
}
