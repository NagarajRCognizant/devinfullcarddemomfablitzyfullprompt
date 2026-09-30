package com.carddemo.authorization.api.dto;

import com.carddemo.cobol.CobolNumeric;
import java.math.BigDecimal;

/**
 * An {@code S9(10)V99} amount as the authorization maps present it: the exact value plus the edited
 * text the {@code PIC} output field produced.
 *
 * <p>The account service has the same shape in its own API package. It is deliberately not shared:
 * a common DTO library would couple the two bounded contexts through their contracts, while the
 * formatting rule itself is shared through {@link CobolNumeric} in the common module.
 */
public record MoneyValue(BigDecimal amount, String display) {

    public static MoneyValue of(BigDecimal amount) {
        return new MoneyValue(CobolNumeric.scaled(amount), CobolNumeric.formatEdited(amount));
    }
}
