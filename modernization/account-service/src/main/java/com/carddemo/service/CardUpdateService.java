package com.carddemo.service;

import com.carddemo.api.dto.CardDetailResponse;
import com.carddemo.api.dto.CardUpdateRequest;
import com.carddemo.api.dto.CardUpdateResponse;
import com.carddemo.cobol.CobolText;
import com.carddemo.domain.CardScreenMessages;
import com.carddemo.domain.validation.FieldFlag;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.exception.UpdateFailedException;
import com.carddemo.persistence.entity.CardEntity;
import com.carddemo.persistence.repository.CardRepository;
import jakarta.persistence.OptimisticLockException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of COCRDUPC, the Credit Card Update transaction (CCUP).
 *
 * <p>The program shows the record, edits the keyed changes in 1220-EDIT-CARD through
 * 1260-EDIT-EXPIRY-YEAR, refuses a turn that changed nothing, and on PF5 re-reads the record
 * under a lock before rewriting it. The re-read comparison of 9600-WRITE-PROCESSING is kept: the
 * request carries the record as it was fetched, and a row that no longer matches it is reported
 * with "Record changed by some one else" rather than overwritten.
 */
@Service
public class CardUpdateService {

    /** 88 VALID-YEAR VALUES 1950 THRU 2099. */
    private static final int MIN_YEAR = 1950;
    private static final int MAX_YEAR = 2099;

    private final CardRepository cardRepository;
    private final CardDetailService cardDetailService;
    private final CardKeyEditor keyEditor;

    public CardUpdateService(CardRepository cardRepository, CardDetailService cardDetailService,
                             CardKeyEditor keyEditor) {
        this.cardRepository = cardRepository;
        this.cardDetailService = cardDetailService;
        this.keyEditor = keyEditor;
    }

    /** The first turn: 9000-READ-DATA, which fills the map with the record to be changed. */
    @Transactional(readOnly = true)
    public CardDetailResponse fetch(String keyedAccountId, String keyedCardNumber) {
        long accountId = keyEditor.requireAccountId(keyedAccountId);
        String cardNumber = keyEditor.requireCardNumber(keyedCardNumber);
        return CardDetailService.toDetail(cardDetailService.read(accountId, cardNumber),
                CardScreenMessages.UPDATE_DETAILS_SHOWN);
    }

    /**
     * The edit-only turn of 2000-DECIDE-ACTION: the changes are validated and the map comes back
     * asking for the PF5 confirmation, without touching the file.
     */
    @Transactional(readOnly = true)
    public CardUpdateResponse validate(CardUpdateRequest request) {
        long accountId = keyEditor.requireAccountId(request.accountId());
        String cardNumber = keyEditor.requireCardNumber(request.cardNumber());
        CardEntity card = cardDetailService.read(accountId, cardNumber);
        EditedCard edited = edit(request);
        requireChanges(card, edited);
        return new CardUpdateResponse(CardDetailService.toDetail(card, null), false,
                CardScreenMessages.PROMPT_FOR_CONFIRMATION);
    }

    /** The PF5 turn of 2000-DECIDE-ACTION followed by 9600-WRITE-PROCESSING. */
    @Transactional
    public CardUpdateResponse save(CardUpdateRequest request) {
        long accountId = keyEditor.requireAccountId(request.accountId());
        String cardNumber = keyEditor.requireCardNumber(request.cardNumber());
        CardEntity card = cardDetailService.read(accountId, cardNumber);
        EditedCard edited = edit(request);
        requireChanges(card, edited);
        requireUnchangedSinceFetch(card, request.fetched());

        card.setCardEmbossedName(CobolText.padRight(edited.embossedName(), 50));
        card.setCardActiveStatus(edited.activeStatus());
        card.setCardExpiraionDate(edited.expiryDate(dayOnFile(card)));
        try {
            CardEntity saved = cardRepository.saveAndFlush(card);
            return new CardUpdateResponse(CardDetailService.toDetail(saved, null), true,
                    CardScreenMessages.UPDATE_SUCCESS);
        } catch (OptimisticLockException | OptimisticLockingFailureException e) {
            // LOCKED-BUT-UPDATE-FAILED: the lock was held but the rewrite did not take.
            throw new UpdateFailedException(CardScreenMessages.UPDATE_OF_RECORD_FAILED, e);
        }
    }

    /** 1220-EDIT-CARD through 1260-EDIT-EXPIRY-YEAR, in the order the source performs them. */
    private EditedCard edit(CardUpdateRequest request) {
        Map<String, FieldFlag> flags = new LinkedHashMap<>();
        String name = CobolText.trim(CobolText.normaliseAsterisk(request.embossedName()));
        if (name.isEmpty() || CobolText.isAllZeroes(name)) {
            flags.put("embossedName", FieldFlag.BLANK);
            throw new ScreenValidationException("CARD_NAME_BLANK", CardScreenMessages.PROMPT_FOR_NAME, flags);
        }
        // 1230-EDIT-NAME removes every alphabetic character and space, then requires an empty rest.
        if (!name.chars().allMatch(c -> Character.isLetter(c) || c == ' ')) {
            flags.put("embossedName", FieldFlag.NOT_OK);
            throw new ScreenValidationException("CARD_NAME_NOT_ALPHA",
                    CardScreenMessages.NAME_MUST_BE_ALPHA, flags);
        }
        String status = CobolText.upperTrim(CobolText.normaliseAsterisk(request.activeStatus()));
        if (!"Y".equals(status) && !"N".equals(status)) {
            flags.put("activeStatus", status.isEmpty() ? FieldFlag.BLANK : FieldFlag.NOT_OK);
            throw new ScreenValidationException("CARD_STATUS_INVALID",
                    CardScreenMessages.STATUS_MUST_BE_YES_NO, flags);
        }
        int month = editedNumber(request.expiryMonth(), "expiryMonth", "CARD_EXPIRY_MONTH_INVALID",
                CardScreenMessages.EXPIRY_MONTH_NOT_VALID);
        if (month < 1 || month > 12) {
            flags.put("expiryMonth", FieldFlag.NOT_OK);
            throw new ScreenValidationException("CARD_EXPIRY_MONTH_INVALID",
                    CardScreenMessages.EXPIRY_MONTH_NOT_VALID, flags);
        }
        int year = editedNumber(request.expiryYear(), "expiryYear", "CARD_EXPIRY_YEAR_INVALID",
                CardScreenMessages.EXPIRY_YEAR_NOT_VALID);
        if (year < MIN_YEAR || year > MAX_YEAR) {
            flags.put("expiryYear", FieldFlag.NOT_OK);
            throw new ScreenValidationException("CARD_EXPIRY_YEAR_INVALID",
                    CardScreenMessages.EXPIRY_YEAR_NOT_VALID, flags);
        }
        return new EditedCard(name, status, year, month);
    }

    private static int editedNumber(String keyed, String field, String status, String message) {
        String value = CobolText.trim(CobolText.normaliseAsterisk(keyed));
        if (value.isEmpty() || !CobolText.isNumeric(value)) {
            throw new ScreenValidationException(status, message,
                    Map.of(field, value.isEmpty() ? FieldFlag.BLANK : FieldFlag.NOT_OK));
        }
        return Integer.parseInt(value);
    }

    /** NO-CHANGES-DETECTED: a turn that keys the values already on file is refused. */
    private static void requireChanges(CardEntity card, EditedCard edited) {
        if (CobolText.trim(card.getCardEmbossedName()).equals(edited.embossedName())
                && CobolText.trim(card.getCardActiveStatus()).equals(edited.activeStatus())
                && CobolText.trim(card.getCardExpiraionDate()).equals(edited.expiryDate(dayOnFile(card)))) {
            throw new ScreenValidationException("NO_CHANGES_DETECTED",
                    CardScreenMessages.NO_CHANGES_DETECTED, Map.of());
        }
    }

    /** The map edits only the year and the month, so the day of the record is carried forward. */
    private static String dayOnFile(CardEntity card) {
        return CobolText.padRight(card.getCardExpiraionDate(), 10).substring(8, 10);
    }

    /** The re-read comparison of 9600-WRITE-PROCESSING. */
    private static void requireUnchangedSinceFetch(CardEntity card, CardDetailResponse fetched) {
        if (fetched == null) {
            return;
        }
        CardDetailResponse current = CardDetailService.toDetail(card, null);
        boolean same = current.embossedName().equals(CobolText.trim(fetched.embossedName()))
                && current.activeStatus().equals(CobolText.trim(fetched.activeStatus()))
                && current.expiryYear().equals(CobolText.trim(fetched.expiryYear()))
                && current.expiryMonth().equals(CobolText.trim(fetched.expiryMonth()));
        if (!same) {
            throw new ScreenValidationException("DATA_CHANGED_BEFORE_UPDATE",
                    CardScreenMessages.CHANGED_BY_SOMEONE_ELSE, Map.of());
        }
    }

    /** The keyed values once the edits have accepted them. */
    private record EditedCard(String embossedName, String activeStatus, int expiryYear, int expiryMonth) {

        /** CARD-EXPIRAION-DATE is the ten character yyyy-mm-dd the file carries. */
        String expiryDate(String day) {
            return "%04d-%02d-%s".formatted(expiryYear, expiryMonth, day);
        }
    }
}
