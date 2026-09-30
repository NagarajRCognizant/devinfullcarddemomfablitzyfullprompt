package com.carddemo.service;

import com.carddemo.api.dto.MenuOptionResponse;
import com.carddemo.cobol.CobolText;
import com.carddemo.domain.UserMessages;
import com.carddemo.domain.validation.FieldFlag;
import com.carddemo.exception.ScreenValidationException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Port of the option table of app/cpy/COADM02Y.cpy and of PROCESS-ENTER-KEY of
 * app/cbl/COADM01C.cbl.
 *
 * <p>The menu holds CDEMO-ADMIN-OPT-COUNT entries; the program rejects an option that is not
 * numeric, is zero or exceeds the count, with one message for all three cases. No option of the
 * admin menu is restricted further, so there is no counterpart to the admin-only check of COMEN01C.
 */
@Service
public class AdminMenuService {

    /** CDEMO-ADMIN-OPT-COUNT and the CDEMO-ADMIN-OPT-NAME/PGMNAME values of COADM02Y. */
    private static final List<MenuOptionResponse> OPTIONS = List.of(
            new MenuOptionResponse(1, "User List (Security)", "COUSR00C"),
            new MenuOptionResponse(2, "User Add (Security)", "COUSR01C"),
            new MenuOptionResponse(3, "User Update (Security)", "COUSR02C"),
            new MenuOptionResponse(4, "User Delete (Security)", "COUSR03C"),
            new MenuOptionResponse(5, "Transaction Type List/Update (Db2)", "COTRTLIC"),
            new MenuOptionResponse(6, "Transaction Type Maintenance (Db2)", "COTRTUPC"));

    public List<MenuOptionResponse> options() {
        return OPTIONS;
    }

    /**
     * The three rejections of PROCESS-ENTER-KEY: a non numeric option, option zero and an option
     * beyond the table, all reported with the same message.
     */
    public MenuOptionResponse select(String option) {
        String trimmed = CobolText.trim(option);
        if (!CobolText.isNumeric(trimmed)) {
            throw invalidOption();
        }
        int number = Integer.parseInt(trimmed);
        if (number == 0 || number > OPTIONS.size()) {
            throw invalidOption();
        }
        return OPTIONS.get(number - 1);
    }

    private ScreenValidationException invalidOption() {
        return new ScreenValidationException("VALIDATION_ERROR", UserMessages.INVALID_MENU_OPTION,
                Map.of("option", FieldFlag.NOT_OK));
    }
}
