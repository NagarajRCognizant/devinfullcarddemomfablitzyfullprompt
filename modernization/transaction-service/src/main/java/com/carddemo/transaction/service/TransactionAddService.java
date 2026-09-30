package com.carddemo.transaction.service;

import com.carddemo.cobol.CobolText;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.exception.UpdateFailedException;
import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.api.dto.TransactionAddRequest;
import com.carddemo.transaction.api.dto.TransactionAddResponse;
import com.carddemo.transaction.client.CardholderView;
import com.carddemo.transaction.domain.TransactionAmountFormat;
import com.carddemo.transaction.domain.TransactionDates;
import com.carddemo.transaction.domain.TransactionIdentifiers;
import com.carddemo.transaction.domain.TransactionMessages;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * COTRN02C, the transaction add screen.
 *
 * <p>The source edits the key fields, then the data fields in a fixed order, then asks for a
 * confirmation and only then writes TRANSACT with the key that follows the highest one on file.
 * The two-step confirmation is kept because it is what the screen did; the identifier arithmetic
 * is kept because COTRN00C's paging depends on the keys collating in that order.
 */
@Service
public class TransactionAddService {

    private final TransactionRepository transactions;
    private final CardholderKeys cardholderKeys;

    public TransactionAddService(TransactionRepository transactions, CardholderKeys cardholderKeys) {
        this.transactions = transactions;
        this.cardholderKeys = cardholderKeys;
    }

    /** PROCESS-ENTER-KEY: validate, confirm and, when confirmed, write the record. */
    @Transactional
    public TransactionAddResponse add(TransactionAddRequest request) {
        CardholderView cardholder = cardholderKeys.resolve(request.accountId(), request.cardNumber());
        validateDataFields(request);

        String confirm = CobolText.trim(request.confirm());
        if (confirm.isEmpty() || confirm.equalsIgnoreCase("N")) {
            String message = confirm.isEmpty() ? TransactionMessages.CONFIRM_ADD : "";
            return new TransactionAddResponse(null, accountId(cardholder), cardholder.cardNumber(),
                    message);
        }
        if (!confirm.equalsIgnoreCase("Y")) {
            throw new ScreenValidationException("INVALID_CONFIRMATION",
                    TransactionMessages.INVALID_CONFIRMATION, Map.of());
        }

        String tranId = TransactionIdentifiers.next(
                transactions.findHighestTransactionId().orElse(null));
        if (transactions.existsById(tranId)) {
            throw new UpdateFailedException(TransactionMessages.TRAN_ID_ALREADY_EXISTS);
        }

        TransactionEntity transaction = new TransactionEntity();
        transaction.setTranId(tranId);
        transaction.setTranTypeCd(CobolText.trim(request.typeCode()));
        transaction.setTranCatCd(Integer.parseInt(CobolText.trim(request.categoryCode())));
        transaction.setTranSource(CobolText.trim(request.source()));
        transaction.setTranDesc(CobolText.trim(request.description()));
        transaction.setTranAmt(TransactionAmountFormat.parse(request.amount()));
        transaction.setTranCardNum(cardholder.cardNumber());
        transaction.setTranMerchantId(Long.parseLong(CobolText.trim(request.merchantId())));
        transaction.setTranMerchantName(CobolText.trim(request.merchantName()));
        transaction.setTranMerchantCity(CobolText.trim(request.merchantCity()));
        transaction.setTranMerchantZip(CobolText.trim(request.merchantZip()));
        transaction.setTranOrigTs(CobolText.trim(request.originalDate()));
        transaction.setTranProcTs(CobolText.trim(request.processedDate()));
        transactions.save(transaction);

        return new TransactionAddResponse(tranId, accountId(cardholder), cardholder.cardNumber(),
                TransactionMessages.transactionAdded(tranId));
    }

    /**
     * COPY-LAST-TRAN-DATA: the PF5 key fills the map from the last record on file, leaving the
     * keyed account or card number in place, and then runs the same edits as the enter key.
     */
    @Transactional(readOnly = true)
    public TransactionAddRequest copyLast(String accountId, String cardNumber) {
        CardholderView cardholder = cardholderKeys.resolve(accountId, cardNumber);
        TransactionEntity last = transactions.findHighestTransactionId()
                .flatMap(transactions::findById)
                .orElse(null);
        if (last == null) {
            return new TransactionAddRequest(accountId(cardholder), cardholder.cardNumber(),
                    "", "", "", "", "", "", "", "", "", "", "", "");
        }
        return new TransactionAddRequest(
                accountId(cardholder),
                cardholder.cardNumber(),
                CobolText.trim(last.getTranTypeCd()),
                String.format("%04d", last.getTranCatCd()),
                CobolText.trim(last.getTranSource()),
                CobolText.trim(last.getTranDesc()),
                TransactionAmountFormat.format(last.getTranAmt()),
                CobolText.trim(last.getTranOrigTs()),
                CobolText.trim(last.getTranProcTs()),
                String.format("%09d", last.getTranMerchantId()),
                CobolText.trim(last.getTranMerchantName()),
                CobolText.trim(last.getTranMerchantCity()),
                CobolText.trim(last.getTranMerchantZip()),
                "");
    }

    /** VALIDATE-INPUT-DATA-FIELDS, in the order the source performs the edits. */
    private void validateDataFields(TransactionAddRequest request) {
        requirePresent(request.typeCode(), TransactionMessages.TYPE_CD_EMPTY);
        requirePresent(request.categoryCode(), TransactionMessages.CATEGORY_CD_EMPTY);
        requirePresent(request.source(), TransactionMessages.SOURCE_EMPTY);
        requirePresent(request.description(), TransactionMessages.DESCRIPTION_EMPTY);
        requirePresent(request.amount(), TransactionMessages.AMOUNT_EMPTY);
        requirePresent(request.originalDate(), TransactionMessages.ORIG_DATE_EMPTY);
        requirePresent(request.processedDate(), TransactionMessages.PROC_DATE_EMPTY);
        requirePresent(request.merchantId(), TransactionMessages.MERCHANT_ID_EMPTY);
        requirePresent(request.merchantName(), TransactionMessages.MERCHANT_NAME_EMPTY);
        requirePresent(request.merchantCity(), TransactionMessages.MERCHANT_CITY_EMPTY);
        requirePresent(request.merchantZip(), TransactionMessages.MERCHANT_ZIP_EMPTY);

        requireNumeric(request.typeCode(), TransactionMessages.TYPE_CD_NOT_NUMERIC);
        requireNumeric(request.categoryCode(), TransactionMessages.CATEGORY_CD_NOT_NUMERIC);
        requireNumeric(request.merchantId(), TransactionMessages.MERCHANT_ID_NOT_NUMERIC);

        if (!TransactionAmountFormat.isKeyedFormat(request.amount())) {
            throw new ScreenValidationException("AMOUNT_FORMAT",
                    TransactionMessages.AMOUNT_FORMAT, Map.of());
        }
        BigDecimal amount = TransactionAmountFormat.parse(request.amount());
        if (amount == null) {
            throw new ScreenValidationException("AMOUNT_FORMAT",
                    TransactionMessages.AMOUNT_FORMAT, Map.of());
        }
        if (!TransactionDates.isKeyedFormat(request.originalDate())) {
            throw new ScreenValidationException("ORIG_DATE_FORMAT",
                    TransactionMessages.ORIG_DATE_FORMAT, Map.of());
        }
        if (!TransactionDates.isKeyedFormat(request.processedDate())) {
            throw new ScreenValidationException("PROC_DATE_FORMAT",
                    TransactionMessages.PROC_DATE_FORMAT, Map.of());
        }
        if (!TransactionDates.isRealDate(request.originalDate())) {
            throw new ScreenValidationException("ORIG_DATE_INVALID",
                    TransactionMessages.ORIG_DATE_INVALID, Map.of());
        }
        if (!TransactionDates.isRealDate(request.processedDate())) {
            throw new ScreenValidationException("PROC_DATE_INVALID",
                    TransactionMessages.PROC_DATE_INVALID, Map.of());
        }
    }

    private static void requirePresent(String value, String message) {
        if (CobolText.isBlank(value)) {
            throw new ScreenValidationException("FIELD_REQUIRED", message, Map.of());
        }
    }

    private static void requireNumeric(String value, String message) {
        if (!CobolText.isNumeric(CobolText.trim(value))) {
            throw new ScreenValidationException("FIELD_NOT_NUMERIC", message, Map.of());
        }
    }

    private static String accountId(CardholderView cardholder) {
        return cardholder.accountId() == null ? "" : String.format("%011d", cardholder.accountId());
    }
}
