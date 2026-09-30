package com.carddemo.transaction.api.dto;

import com.carddemo.transaction.domain.TransactionAmountFormat;
import java.math.BigDecimal;

/**
 * An {@code S9(09)V99} amount as the transaction screens present it: the exact value plus the text
 * produced by their {@code PIC +99999999.99} output field.
 */
public record MoneyValue(BigDecimal amount, String display) {

    public static MoneyValue of(BigDecimal amount) {
        return new MoneyValue(amount, TransactionAmountFormat.format(amount));
    }
}
