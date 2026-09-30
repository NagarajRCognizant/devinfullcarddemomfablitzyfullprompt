package com.carddemo.api.dto;

import com.carddemo.cobol.CobolNumeric;
import java.math.BigDecimal;

/**
 * An {@code S9(10)V99} amount as the screens present it: the exact value plus the edited text
 * produced by the {@code PIC +ZZZ,ZZZ,ZZZ.99} output field of the BMS map.
 */
public record MoneyValue(BigDecimal amount, String display) {

    public static MoneyValue of(BigDecimal amount) {
        return new MoneyValue(CobolNumeric.scaled(amount), CobolNumeric.formatEdited(amount));
    }
}
