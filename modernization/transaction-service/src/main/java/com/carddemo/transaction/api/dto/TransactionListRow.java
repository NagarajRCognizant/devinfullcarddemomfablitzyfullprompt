package com.carddemo.transaction.api.dto;

/** One of the ten lines of the COTRN00C list, as POPULATE-TRAN-DATA fills it. */
public record TransactionListRow(
        String tranId,
        String date,
        String description,
        MoneyValue amount) {
}
