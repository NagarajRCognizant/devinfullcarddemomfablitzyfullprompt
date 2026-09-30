package com.carddemo.service;

import com.carddemo.api.dto.SignOnRequest;
import com.carddemo.api.dto.SignOnResponse;
import com.carddemo.cobol.CobolText;
import com.carddemo.config.TokenIssuer;
import com.carddemo.domain.UserMessages;
import com.carddemo.domain.validation.FieldFlag;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.exception.SignOnFailedException;
import com.carddemo.persistence.entity.SecurityUserEntity;
import com.carddemo.persistence.repository.SecurityUserRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of PROCESS-ENTER-KEY and READ-USER-SEC-FILE of app/cbl/COSGN00C.cbl.
 *
 * <p>The program checks the user id first and the password second, reporting one message at a time,
 * uppercases both fields before the read and compares the stored password with the uppercased
 * value. A match routes an administrator to COADM01C and everybody else to COMEN01C.
 */
@Service
@ConditionalOnProperty(prefix = "carddemo.security", name = "mode", havingValue = "legacy")
public class SignOnService {

    /** XCTL target of PROCESS-ENTER-KEY when CDEMO-USRTYP-ADMIN. */
    public static final String ADMIN_MENU_PROGRAM = "COADM01C";
    /** XCTL target of PROCESS-ENTER-KEY for every other user type. */
    public static final String MAIN_MENU_PROGRAM = "COMEN01C";
    /** SEC-USR-PWD PIC X(08). */
    private static final int PASSWORD_LENGTH = 8;

    private final SecurityUserRepository users;
    private final TokenIssuer tokenIssuer;

    public SignOnService(SecurityUserRepository users, TokenIssuer tokenIssuer) {
        this.users = users;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional(readOnly = true)
    public SignOnResponse signOn(SignOnRequest request) {
        if (CobolText.isBlank(request.userId())) {
            throw new ScreenValidationException("VALIDATION_ERROR", UserMessages.SIGNON_USER_ID_REQUIRED,
                    Map.of("userId", FieldFlag.BLANK));
        }
        if (CobolText.isBlank(request.password())) {
            throw new ScreenValidationException("VALIDATION_ERROR",
                    UserMessages.SIGNON_PASSWORD_REQUIRED, Map.of("password", FieldFlag.BLANK));
        }

        // MOVE FUNCTION UPPER-CASE(USERIDI) TO WS-USER-ID / same for the password.
        String userId = CobolText.upperTrim(request.userId());
        String password = CobolText.upperTrim(request.password());

        Optional<SecurityUserEntity> found = users.findById(UserTypes.key(userId));
        if (found.isEmpty()) {
            throw new SignOnFailedException(UserMessages.SIGNON_USER_NOT_FOUND);
        }

        SecurityUserEntity user = found.get();
        if (!passwordMatches(user.getPassword(), password)) {
            throw new SignOnFailedException(UserMessages.SIGNON_WRONG_PASSWORD);
        }

        String userType = CobolText.trim(user.getUserType());
        return new SignOnResponse(UserTypes.normaliseId(user.getUserId()),
                CobolText.trim(user.getFirstName()), CobolText.trim(user.getLastName()), userType,
                UserTypes.isAdmin(userType) ? ADMIN_MENU_PROGRAM : MAIN_MENU_PROGRAM,
                tokenIssuer.issue(user, userType), tokenIssuer.ttlSeconds());
    }

    /**
     * {@code IF SEC-USR-PWD = WS-USER-PWD}: both operands are PIC X(08), so the comparison is on the
     * space padded values, and a keyed password longer than eight characters was truncated by the
     * MOVE into WS-USER-PWD before the comparison.
     */
    private boolean passwordMatches(String stored, String supplied) {
        return fixedLength(stored).equals(fixedLength(supplied));
    }

    private String fixedLength(String value) {
        String trimmed = CobolText.trim(value);
        if (trimmed.length() > PASSWORD_LENGTH) {
            trimmed = trimmed.substring(0, PASSWORD_LENGTH);
        }
        return CobolText.padRight(trimmed, PASSWORD_LENGTH);
    }
}
