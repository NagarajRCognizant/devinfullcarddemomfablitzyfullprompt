package com.carddemo.transaction.api.dto;

import java.util.List;

/**
 * One screenful of the transaction list.
 *
 * <p>The first and last identifiers and the next-page flag are CDEMO-CT00-TRNID-FIRST,
 * CDEMO-CT00-TRNID-LAST and CDEMO-CT00-NEXT-PAGE-FLG of the commarea: the source kept the browse
 * position there between pseudo-conversational turns, and the client sends them back the same way
 * so that paging stays positional instead of counting rows.
 *
 * @param message the line the source would have shown, such as the already-at-the-top notice;
 *     empty when the page was produced without one
 */
public record TransactionListResponse(
        List<TransactionListRow> rows,
        int pageNumber,
        String firstTranId,
        String lastTranId,
        boolean nextPageAvailable,
        String message) {
}
