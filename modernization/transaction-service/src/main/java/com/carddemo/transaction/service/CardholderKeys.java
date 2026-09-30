package com.carddemo.transaction.service;

import com.carddemo.cobol.CobolText;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.CardholderView;
import com.carddemo.transaction.domain.TransactionMessages;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * VALIDATE-INPUT-KEY-FIELDS of COTRN02C.
 *
 * <p>Either an account number or a card number identifies the card the transaction is written
 * against: keying the account number reads CXACAIX to find the card, keying the card number reads
 * CCXREF to find the account, and keying neither is rejected. The order of the edits and the
 * message of each outcome are the source's.
 */
@Service
public class CardholderKeys {

    private final AccountServiceClient accounts;

    public CardholderKeys(AccountServiceClient accounts) {
        this.accounts = accounts;
    }

    /** Resolves the keyed account or card number into the cross reference pair behind it. */
    public CardholderView resolve(String accountId, String cardNumber) {
        if (!CobolText.isBlank(accountId)) {
            String keyed = CobolText.trim(accountId);
            if (!CobolText.isNumeric(keyed)) {
                throw new ScreenValidationException("ACCOUNT_ID_NOT_NUMERIC",
                        TransactionMessages.ACCOUNT_ID_NOT_NUMERIC, Map.of());
            }
            CardholderView view = accounts.byAccountId(Long.parseLong(keyed));
            if (!view.cardFound()) {
                throw new RecordNotFoundException(TransactionMessages.ACCOUNT_NOT_FOUND, Map.of());
            }
            return view;
        }
        if (!CobolText.isBlank(cardNumber)) {
            String keyed = CobolText.trim(cardNumber);
            if (!CobolText.isNumeric(keyed)) {
                throw new ScreenValidationException("CARD_NUMBER_NOT_NUMERIC",
                        TransactionMessages.CARD_NUMBER_NOT_NUMERIC, Map.of());
            }
            CardholderView view = accounts.byCardNumber(keyed);
            if (!view.cardFound()) {
                throw new RecordNotFoundException(TransactionMessages.CARD_NOT_FOUND, Map.of());
            }
            return view;
        }
        throw new ScreenValidationException("ACCOUNT_OR_CARD_REQUIRED",
                TransactionMessages.ACCOUNT_OR_CARD_REQUIRED, Map.of());
    }
}
