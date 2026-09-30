package com.carddemo.authorization.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The decline reason table of COPAUS1C ({@code WS-DECLINE-REASON-TABLE}) and its
 * {@code SEARCH ALL ... AT END} fallback.
 *
 * <p>The table is reference data held in the program, so it is converted as an in-memory lookup
 * rather than a database table: it is read only, ten entries long and versioned with the code that
 * uses it. The descriptions are the source literals, including the abbreviations and the
 * misspelling of INSUFFICNT, because they are shown to the user.
 */
public final class DeclineReason {

    private static final Map<String, String> DESCRIPTIONS = buildTable();

    /** The {@code AT END} branch: a reason the table does not hold is shown as 9999-ERROR. */
    public static final String UNKNOWN_CODE = "9999";
    public static final String UNKNOWN_DESCRIPTION = "ERROR";

    private DeclineReason() {
    }

    private static Map<String, String> buildTable() {
        Map<String, String> table = new LinkedHashMap<>();
        table.put("0000", "APPROVED");
        table.put("3100", "INVALID CARD");
        table.put("4100", "INSUFFICNT FUND");
        table.put("4200", "CARD NOT ACTIVE");
        table.put("4300", "ACCOUNT CLOSED");
        table.put("4400", "EXCED DAILY LMT");
        table.put("5100", "CARD FRAUD");
        table.put("5200", "MERCHANT FRAUD");
        table.put("5300", "LOST CARD");
        table.put("9000", "UNKNOWN");
        return Map.copyOf(table);
    }

    /**
     * Returns the AUTHRSNO field of map COPAU01: the reason code, a hyphen and the description,
     * or {@code 9999-ERROR} when the code is not in the table.
     */
    public static String describe(String reasonCode) {
        String code = reasonCode == null ? "" : reasonCode.trim();
        String description = DESCRIPTIONS.get(code);
        if (description == null) {
            return UNKNOWN_CODE + "-" + UNKNOWN_DESCRIPTION;
        }
        return code + "-" + description;
    }
}
