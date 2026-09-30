package com.carddemo.service;

import com.carddemo.cobol.CobolText;
import com.carddemo.domain.CardScreenMessages;
import com.carddemo.domain.ScreenMessages;
import com.carddemo.domain.validation.FieldFlag;
import com.carddemo.exception.ScreenValidationException;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The account and card key edits the three card screens share.
 *
 * <p>2210-EDIT-ACCOUNT and 2220-EDIT-CARD appear in COCRDLIC, COCRDSLC and COCRDUPC with the same
 * shape: an unkeyed field is blank, a keyed field must be all digits, and the '*' the map writes
 * back into an unkeyed field counts as blank. The list screen accepts a blank key as "no filter"
 * while the view and update screens report it as missing, so the caller says which of the two it
 * wants rather than the edit guessing.
 */
@Component
public class CardKeyEditor {

    /** Both maps key eleven characters for the account id. */
    public static final int ACCOUNT_ID_LENGTH = 11;
    /** Both maps key sixteen characters for the card number. */
    public static final int CARD_NUMBER_LENGTH = 16;

    /**
     * 2210-EDIT-ACCOUNT. Returns null for a blank filter, which is how COCRDLIC leaves
     * FLG-ACCTFILTER-BLANK and browses the whole file.
     */
    public Long editAccountFilter(String keyed) {
        if (isUnkeyed(keyed)) {
            return null;
        }
        if (!CobolText.isNumeric(CobolText.trim(keyed))) {
            throw new ScreenValidationException("ACCOUNT_FILTER_INVALID",
                    CardScreenMessages.LIST_ACCOUNT_FILTER_INVALID,
                    Map.of("accountId", FieldFlag.NOT_OK));
        }
        return Long.parseLong(CobolText.trim(keyed));
    }

    /** 2220-EDIT-CARD. Returns null for a blank filter. */
    public String editCardFilter(String keyed) {
        if (isUnkeyed(keyed)) {
            return null;
        }
        if (!CobolText.isNumeric(CobolText.trim(keyed))) {
            throw new ScreenValidationException("CARD_FILTER_INVALID",
                    CardScreenMessages.LIST_CARD_FILTER_INVALID,
                    Map.of("cardNumber", FieldFlag.NOT_OK));
        }
        return CobolText.padLeftZero(CobolText.trim(keyed), CARD_NUMBER_LENGTH);
    }

    /** 2210-EDIT-ACCOUNT of COCRDSLC and COCRDUPC, where the key must be supplied. */
    public long requireAccountId(String keyed) {
        Long accountId = accountOrBlank(keyed);
        if (accountId == null) {
            throw new ScreenValidationException("ACCOUNT_NOT_PROVIDED",
                    ScreenMessages.PROMPT_FOR_ACCT,
                    Map.of("accountId", FieldFlag.BLANK));
        }
        return accountId;
    }

    /** 2220-EDIT-CARD of COCRDSLC and COCRDUPC, where the key must be supplied. */
    public String requireCardNumber(String keyed) {
        if (isUnkeyed(keyed)) {
            throw new ScreenValidationException("CARD_NOT_PROVIDED",
                    CardScreenMessages.PROMPT_FOR_CARD,
                    Map.of("cardNumber", FieldFlag.BLANK));
        }
        return editCardFilter(keyed);
    }

    private Long accountOrBlank(String keyed) {
        Long accountId = editAccountFilter(keyed);
        // CC-ACCT-ID-N EQUAL ZEROS is the third blank condition of the source edit.
        return accountId == null || accountId == 0L ? null : accountId;
    }

    /** LOW-VALUES, SPACES or the '*' the map writes into a field the operator left alone. */
    private static boolean isUnkeyed(String keyed) {
        String value = CobolText.normaliseAsterisk(keyed);
        return CobolText.isBlank(value) || CobolText.isAllZeroes(value);
    }
}
