package com.carddemo.transaction.service;

import com.carddemo.cobol.CobolText;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.api.dto.MoneyValue;
import com.carddemo.transaction.api.dto.TransactionListResponse;
import com.carddemo.transaction.api.dto.TransactionListRow;
import com.carddemo.transaction.domain.TransactionDates;
import com.carddemo.transaction.domain.TransactionIdentifiers;
import com.carddemo.transaction.domain.TransactionMessages;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * COTRN00C, the transaction list.
 *
 * <p>The source browses TRANSACT with STARTBR/READNEXT/READPREV and keeps the first and last key
 * of the page in the commarea between turns. That is reproduced with keyset reads from those same
 * keys rather than with offset paging, so a transaction added while a user pages does not shift
 * the rows already seen, and the paging messages are the source's own.
 */
@Service
public class TransactionListService {

    /** The map has ten lines, and both paging paragraphs loop over exactly ten. */
    public static final int PAGE_SIZE = 10;

    private final TransactionRepository transactions;

    public TransactionListService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    /** PROCESS-ENTER-KEY: browse from the keyed identifier, or from the start when none was keyed. */
    @Transactional(readOnly = true)
    public TransactionListResponse first(String tranIdFilter) {
        String from = "";
        if (!CobolText.isBlank(tranIdFilter)) {
            if (!CobolText.isNumeric(CobolText.trim(tranIdFilter))) {
                throw new ScreenValidationException("INVALID_TRAN_ID",
                        TransactionMessages.TRAN_ID_NOT_NUMERIC, Map.of());
            }
            from = TransactionIdentifiers.normalise(tranIdFilter);
        }
        List<TransactionEntity> rows = transactions.browseForwardFrom(from, Limit.of(PAGE_SIZE));
        return page(rows, 1, "");
    }

    /** PROCESS-PF8-KEY: the next screenful after the last key of the current page. */
    @Transactional(readOnly = true)
    public TransactionListResponse next(String lastTranId, int pageNumber) {
        if (CobolText.isBlank(lastTranId)) {
            return first(null);
        }
        String last = TransactionIdentifiers.normalise(lastTranId);
        List<TransactionEntity> rows = transactions.browseForwardAfter(last, Limit.of(PAGE_SIZE));
        if (rows.isEmpty()) {
            return reread(last, pageNumber, TransactionMessages.ALREADY_AT_BOTTOM);
        }
        return page(rows, pageNumber + 1, "");
    }

    /** PROCESS-PF7-KEY: the screenful before the first key of the current page. */
    @Transactional(readOnly = true)
    public TransactionListResponse previous(String firstTranId, int pageNumber) {
        if (pageNumber <= 1 || CobolText.isBlank(firstTranId)) {
            return rereadForward(firstTranId, Math.max(pageNumber, 1),
                    TransactionMessages.ALREADY_AT_TOP);
        }
        String first = TransactionIdentifiers.normalise(firstTranId);
        List<TransactionEntity> rows =
                new ArrayList<>(transactions.browseBackwardBefore(first, Limit.of(PAGE_SIZE)));
        if (rows.isEmpty()) {
            return rereadForward(first, pageNumber, TransactionMessages.ALREADY_AT_TOP);
        }
        Collections.reverse(rows);
        int previousPage = pageNumber - 1;
        String message = previousPage == 1 ? TransactionMessages.REACHED_TOP : "";
        return page(rows, previousPage, message);
    }

    /**
     * PROCESS-ENTER-KEY of the selection column: the source accepts only S, and any other
     * character redisplays the list with "Invalid selection. Valid value is S".
     */
    public String selected(String selectionFlag, String tranId) {
        String flag = CobolText.trim(selectionFlag);
        if (!"S".equalsIgnoreCase(flag)) {
            throw new ScreenValidationException("INVALID_SELECTION",
                    TransactionMessages.INVALID_SELECTION, Map.of());
        }
        return TransactionIdentifiers.normalise(tranId);
    }

    /** The redisplay of the current page that accompanies an already-at-the-bottom message. */
    private TransactionListResponse reread(String lastId, int pageNumber, String message) {
        List<TransactionEntity> rows =
                new ArrayList<>(transactions.browseBackwardBefore(lastId, Limit.of(PAGE_SIZE - 1)));
        Collections.reverse(rows);
        transactions.findById(lastId).ifPresent(rows::add);
        return page(rows, pageNumber, message);
    }

    /** The redisplay of the current page that accompanies an already-at-the-top message. */
    private TransactionListResponse rereadForward(String firstId, int pageNumber, String message) {
        String from = CobolText.isBlank(firstId) ? "" : TransactionIdentifiers.normalise(firstId);
        List<TransactionEntity> rows = transactions.browseForwardFrom(from, Limit.of(PAGE_SIZE));
        return page(rows, pageNumber, message);
    }

    private TransactionListResponse page(List<TransactionEntity> entities, int pageNumber,
            String message) {
        List<TransactionListRow> rows = entities.stream()
                .map(TransactionListService::row)
                .toList();
        String firstId = entities.isEmpty() ? "" : CobolText.trim(entities.get(0).getTranId());
        String lastId = entities.isEmpty() ? ""
                : CobolText.trim(entities.get(entities.size() - 1).getTranId());
        boolean nextPage = !entities.isEmpty()
                && !transactions.browseForwardAfter(lastId, Limit.of(1)).isEmpty();
        String line = message;
        if (line.isEmpty() && !nextPage && !entities.isEmpty()) {
            line = TransactionMessages.REACHED_BOTTOM;
        }
        return new TransactionListResponse(rows, pageNumber, firstId, lastId, nextPage, line);
    }

    private static TransactionListRow row(TransactionEntity transaction) {
        return new TransactionListRow(
                CobolText.trim(transaction.getTranId()),
                TransactionDates.listDate(transaction.getTranOrigTs()),
                CobolText.trim(transaction.getTranDesc()),
                MoneyValue.of(transaction.getTranAmt()));
    }
}
