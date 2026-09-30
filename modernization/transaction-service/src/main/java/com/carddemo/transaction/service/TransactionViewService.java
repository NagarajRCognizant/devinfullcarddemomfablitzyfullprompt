package com.carddemo.transaction.service;

import com.carddemo.cobol.CobolText;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.api.dto.MoneyValue;
import com.carddemo.transaction.api.dto.TransactionDetailResponse;
import com.carddemo.transaction.domain.TransactionIdentifiers;
import com.carddemo.transaction.domain.TransactionMessages;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * COTRN01C, the transaction view.
 *
 * <p>The source edits the keyed identifier, reads TRANSACT by key and shows either the record or
 * one of its two failure messages; the empty and not-found outcomes are kept apart because the
 * screen reported them differently.
 */
@Service
public class TransactionViewService {

    private final TransactionRepository transactions;

    public TransactionViewService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    /** PROCESS-ENTER-KEY followed by READ-TRANSACT-FILE. */
    @Transactional(readOnly = true)
    public TransactionDetailResponse view(String tranId) {
        if (CobolText.isBlank(tranId)) {
            throw new ScreenValidationException("TRAN_ID_REQUIRED",
                    TransactionMessages.TRAN_ID_EMPTY, Map.of());
        }
        String key = TransactionIdentifiers.normalise(tranId);
        TransactionEntity transaction = transactions.findById(key)
                .orElseThrow(() -> new RecordNotFoundException(
                        TransactionMessages.TRANSACTION_NOT_FOUND, Map.of()));
        return detail(transaction);
    }

    static TransactionDetailResponse detail(TransactionEntity transaction) {
        return new TransactionDetailResponse(
                CobolText.trim(transaction.getTranId()),
                CobolText.trim(transaction.getTranCardNum()),
                CobolText.trim(transaction.getTranTypeCd()),
                String.format("%04d", transaction.getTranCatCd()),
                CobolText.trim(transaction.getTranSource()),
                CobolText.trim(transaction.getTranDesc()),
                MoneyValue.of(transaction.getTranAmt()),
                CobolText.trim(transaction.getTranOrigTs()),
                CobolText.trim(transaction.getTranProcTs()),
                String.format("%09d", transaction.getTranMerchantId()),
                CobolText.trim(transaction.getTranMerchantName()),
                CobolText.trim(transaction.getTranMerchantCity()),
                CobolText.trim(transaction.getTranMerchantZip()));
    }
}
