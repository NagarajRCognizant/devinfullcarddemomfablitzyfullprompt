package com.carddemo.service;

import com.carddemo.api.dto.CurrentUserResponse;
import com.carddemo.cobol.CobolText;
import com.carddemo.exception.SignOnFailedException;
import com.carddemo.domain.UserMessages;
import com.carddemo.persistence.entity.SecurityUserEntity;
import com.carddemo.persistence.repository.SecurityUserRepository;
import com.carddemo.security.SignedOnUser;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * READ-USER-SEC-FILE of COSGN00C for a caller the realm already authenticated.
 *
 * <p>The password comparison of the program is gone - the realm performed it, with a second factor -
 * but the rest of the paragraph is unchanged: the row is read on the upper case eight character key,
 * and an administrator routes to COADM01C while everybody else routes to COMEN01C.
 *
 * <p>A token for a user with no USRSEC row is refused with the message of the program's NOTFND path,
 * because every screen behind the menus keys off that row.
 */
@Service
public class CurrentUserService {

    /** XCTL target of PROCESS-ENTER-KEY when CDEMO-USRTYP-ADMIN. */
    public static final String ADMIN_MENU_PROGRAM = "COADM01C";
    /** XCTL target of PROCESS-ENTER-KEY for every other user type. */
    public static final String MAIN_MENU_PROGRAM = "COMEN01C";

    private final SecurityUserRepository users;

    public CurrentUserService(SecurityUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse forToken(SignedOnUser signedOn) {
        Optional<SecurityUserEntity> found = users.findById(UserTypes.key(signedOn.userId()));
        if (found.isEmpty()) {
            throw new SignOnFailedException(UserMessages.SIGNON_USER_NOT_FOUND);
        }
        SecurityUserEntity user = found.get();
        // SEC-USR-TYPE decides what the user is, as it did in the source: the realm authenticates and
        // the record authorises. The role the realm holds is written from this column when the account
        // is provisioned, so the two disagreeing means the realm was changed behind the record, and a
        // sign-on that would take its authority from that change is refused rather than honoured.
        boolean administrator = UserTypes.isAdmin(user.getUserType());
        if (administrator != signedOn.administrator()) {
            throw new SignOnFailedException(UserMessages.SIGNON_UNABLE_TO_VERIFY);
        }
        return new CurrentUserResponse(UserTypes.normaliseId(user.getUserId()),
                CobolText.trim(user.getFirstName()), CobolText.trim(user.getLastName()),
                administrator ? UserTypes.ADMIN : UserTypes.REGULAR,
                administrator ? ADMIN_MENU_PROGRAM : MAIN_MENU_PROGRAM);
    }
}
