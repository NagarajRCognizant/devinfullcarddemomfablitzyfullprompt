package com.carddemo.service;

import com.carddemo.api.dto.MenuOption;
import com.carddemo.api.dto.MenuResponse;
import com.carddemo.cobol.CobolText;
import com.carddemo.domain.ScreenMessages;
import com.carddemo.exception.AdminOnlyOptionException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Port of the menu access rule of COMEN01C and of the option table in app/cpy/COMEN02Y.cpy,
 * covering the options whose programs are converted.
 *
 * <p>The option numbers are the ones of the supplied table, so an operator reads the same numbers
 * the terminal showed.
 *
 * <p>The rule itself is: a user whose type is not 'A' may not select an option whose table entry
 * carries user type 'A'. In the supplied table every option carries 'U', so the rule never fires;
 * that AS-IS state is preserved rather than "corrected", and both the unreachable branch and the
 * commented out "Transaction Add (Admin Only)" option name are recorded in the Unsupported
 * Construct Register.
 *
 * <p>The operator type comes from the sign-on token the identity service issued, in the place the
 * source read it from the COMMAREA that COSGN00C filled, so a caller cannot claim a type it was not
 * signed on with.
 */
@Service
public class MenuAccessService {

    /** SEC-USR-TYPE values of app/cpy/CSUSR01Y.cpy. */
    public static final String USER_TYPE_ADMIN = "A";
    public static final String USER_TYPE_REGULAR = "U";

    private static final List<MenuOption> OPTIONS = List.of(
            new MenuOption(1, "Account View", "COACTVWC", USER_TYPE_REGULAR, true),
            new MenuOption(2, "Account Update", "COACTUPC", USER_TYPE_REGULAR, true),
            new MenuOption(3, "Credit Card List", "COCRDLIC", USER_TYPE_REGULAR, true),
            new MenuOption(4, "Credit Card View", "COCRDSLC", USER_TYPE_REGULAR, true),
            new MenuOption(5, "Credit Card Update", "COCRDUPC", USER_TYPE_REGULAR, true),
            new MenuOption(6, "Transaction List", "COTRN00C", USER_TYPE_REGULAR, true),
            new MenuOption(7, "Transaction View", "COTRN01C", USER_TYPE_REGULAR, true),
            new MenuOption(8, "Transaction Add", "COTRN02C", USER_TYPE_REGULAR, true),
            new MenuOption(9, "Transaction Reports", "CORPT00C", USER_TYPE_REGULAR, true),
            new MenuOption(10, "Bill Payment", "COBIL00C", USER_TYPE_REGULAR, true),
            new MenuOption(11, "Pending Authorization View", "COPAUS0C", USER_TYPE_REGULAR, true));

    /** The options the given test user type may select, with the access rule already applied. */
    public MenuResponse describe(String userId, String userType) {
        String type = normaliseUserType(userType);
        List<MenuOption> options = OPTIONS.stream()
                .map(option -> new MenuOption(option.number(), option.name(), option.program(),
                        option.userType(), isAccessible(type, option.userType())))
                .toList();
        return new MenuResponse(CobolText.trim(userId), type, typeName(type), options);
    }

    /**
     * PROCESS-ENTER-KEY of COMEN01C: a regular user selecting an admin-only option is refused with
     * "No access - Admin Only option... " and the menu is redisplayed.
     */
    public void requireAccess(String userType, String program) {
        String type = normaliseUserType(userType);
        MenuOption option = OPTIONS.stream()
                .filter(candidate -> candidate.program().equals(program))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown menu program " + program));
        if (!isAccessible(type, option.userType())) {
            throw new AdminOnlyOptionException(ScreenMessages.NO_ACCESS_ADMIN_ONLY);
        }
    }

    private static boolean isAccessible(String userType, String optionUserType) {
        return USER_TYPE_ADMIN.equals(userType) || !USER_TYPE_ADMIN.equals(optionUserType);
    }

    private static String normaliseUserType(String userType) {
        String type = CobolText.upperTrim(userType);
        return type.isEmpty() ? USER_TYPE_REGULAR : type;
    }

    private static String typeName(String userType) {
        return USER_TYPE_ADMIN.equals(userType) ? "Administrator" : "Regular user";
    }
}
