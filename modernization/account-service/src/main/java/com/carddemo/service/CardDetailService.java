package com.carddemo.service;

import com.carddemo.api.dto.CardDetailResponse;
import com.carddemo.cobol.CobolText;
import com.carddemo.domain.CardScreenMessages;
import com.carddemo.domain.validation.FieldFlag;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.persistence.entity.CardEntity;
import com.carddemo.persistence.repository.CardRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of COCRDSLC, the Credit Card View transaction (CCDL).
 *
 * <p>The program edits both keys, reads CARDDAT by card number in 9100-GETCARD-BYACCTCARD and
 * shows the record. The account id is part of the search condition even though the read is keyed
 * only on the card number, so a card that belongs to another account is reported with the same
 * "did not find cards for this search condition" line the NOTFND branch writes.
 */
@Service
public class CardDetailService {

    private final CardRepository cardRepository;
    private final CardKeyEditor keyEditor;

    public CardDetailService(CardRepository cardRepository, CardKeyEditor keyEditor) {
        this.cardRepository = cardRepository;
        this.keyEditor = keyEditor;
    }

    /** 2200-EDIT-MAP-INPUTS followed by 9000-READ-DATA. */
    @Transactional(readOnly = true)
    public CardDetailResponse view(String keyedAccountId, String keyedCardNumber) {
        long accountId = keyEditor.requireAccountId(keyedAccountId);
        String cardNumber = keyEditor.requireCardNumber(keyedCardNumber);
        CardEntity card = read(accountId, cardNumber);
        return toDetail(card, CardScreenMessages.VIEW_DISPLAYING_DETAILS);
    }

    CardEntity read(long accountId, String cardNumber) {
        return cardRepository.findByCardNumAndCardAcctId(cardNumber, accountId)
                .orElseThrow(() -> new RecordNotFoundException(
                        CardScreenMessages.CARD_COMBINATION_NOT_FOUND,
                        Map.of("accountId", FieldFlag.NOT_OK, "cardNumber", FieldFlag.NOT_OK)));
    }

    static CardDetailResponse toDetail(CardEntity card, String infoMessage) {
        String expiry = CobolText.padRight(card.getCardExpiraionDate(), 10);
        return new CardDetailResponse(
                CobolText.padLeftZero(Long.toString(card.getCardAcctId()), CardKeyEditor.ACCOUNT_ID_LENGTH),
                card.getCardNum(),
                CobolText.trim(card.getCardEmbossedName()),
                CobolText.trim(card.getCardActiveStatus()),
                expiry.substring(0, 4),
                expiry.substring(5, 7),
                expiry.substring(8, 10),
                CobolText.padLeftZero(Integer.toString(card.getCardCvvCd()), 3),
                infoMessage);
    }
}
