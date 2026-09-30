package com.carddemo.service;

import com.carddemo.api.dto.UserActionResponse;
import com.carddemo.api.dto.UserAddForm;
import com.carddemo.api.dto.UserDetail;
import com.carddemo.api.dto.UserUpdateForm;
import com.carddemo.cobol.CobolText;
import com.carddemo.domain.UserMessages;
import com.carddemo.domain.validation.FieldFlag;
import com.carddemo.exception.DuplicateUserException;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.exception.UpdateFailedException;
import com.carddemo.keycloak.DirectoryCompensation;
import com.carddemo.keycloak.DirectoryUser;
import com.carddemo.keycloak.UserDirectory;
import com.carddemo.persistence.entity.SecurityUserEntity;
import com.carddemo.persistence.repository.SecurityUserRepository;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of app/cbl/COUSR01C.cbl (add), app/cbl/COUSR02C.cbl (update) and app/cbl/COUSR03C.cbl
 * (delete).
 *
 * <p>The three programs share the same shape: check the required fields in the order of the map,
 * one message at a time; read USRSEC by the eight byte key; then WRITE, REWRITE or DELETE. Only the
 * update compares the keyed values with the stored record and refuses a rewrite when nothing
 * changed, which is the {@code Please modify to update ...} path.
 *
 * <p>None of the three uppercases its input; only the sign-on program does. A user id keyed in
 * lower case therefore creates a distinct record, and that behaviour is preserved.
 *
 * <p>Each program now writes twice: the USRSEC record, then the realm account the user signs on with.
 * The record is written first, so a realm failure rolls it back and the screen reports the failure
 * message of the source program; a realm write that survives a failed commit is undone by
 * {@link DirectoryCompensation}.
 */
@Service
public class UserAdminService {

    private final SecurityUserRepository users;
    private final UserDirectory directory;
    private final DirectoryCompensation compensation;

    public UserAdminService(SecurityUserRepository users, UserDirectory directory,
                           DirectoryCompensation compensation) {
        this.users = users;
        this.directory = directory;
        this.compensation = compensation;
    }

    /** READ-USER-SEC-FILE of COUSR02C/COUSR03C: loads the record the screen is about to change. */
    @Transactional(readOnly = true)
    public UserDetail find(String userId) {
        requireUserId(userId);
        return detail(read(userId));
    }

    /** PROCESS-ENTER-KEY and WRITE-USER-SEC-FILE of COUSR01C. */
    @Transactional
    public UserActionResponse add(UserAddForm form) {
        if (CobolText.isBlank(form.firstName())) {
            throw blank("firstName", UserMessages.FIRST_NAME_REQUIRED);
        }
        if (CobolText.isBlank(form.lastName())) {
            throw blank("lastName", UserMessages.LAST_NAME_REQUIRED);
        }
        if (CobolText.isBlank(form.userId())) {
            throw blank("userId", UserMessages.USER_ID_REQUIRED);
        }
        if (CobolText.isBlank(form.password())) {
            throw blank("password", UserMessages.PASSWORD_REQUIRED);
        }
        if (CobolText.isBlank(form.userType())) {
            throw blank("userType", UserMessages.USER_TYPE_REQUIRED);
        }

        String key = UserTypes.key(form.userId());
        if (users.existsById(key)) {
            throw new DuplicateUserException(UserMessages.USER_ALREADY_EXISTS);
        }

        SecurityUserEntity user = new SecurityUserEntity();
        user.setUserId(key);
        user.setFirstName(CobolText.trim(form.firstName()));
        user.setLastName(CobolText.trim(form.lastName()));
        user.setPassword(CobolText.trim(form.password()));
        user.setUserType(CobolText.trim(form.userType()));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException duplicate) {
            // The WRITE returned DUPREC because another task inserted the same key first.
            throw new DuplicateUserException(UserMessages.USER_ALREADY_EXISTS);
        }

        String directoryUserId = directory.create(DirectoryUser.of(user));
        if (directoryUserId != null) {
            compensation.onRollback("the account of " + UserTypes.normaliseId(key),
                    () -> directory.delete(directoryUserId));
            user.setKeycloakUserId(directoryUserId);
            users.saveAndFlush(user);
        }
        return new UserActionResponse(UserMessages.userAdded(UserTypes.normaliseId(key)),
                detail(user));
    }

    /** PROCESS-ENTER-KEY, UPDATE-USER-INFO and UPDATE-USER-SEC-FILE of COUSR02C. */
    @Transactional
    public UserActionResponse update(String userId, UserUpdateForm form) {
        requireUserId(userId);
        if (CobolText.isBlank(form.firstName())) {
            throw blank("firstName", UserMessages.FIRST_NAME_REQUIRED);
        }
        if (CobolText.isBlank(form.lastName())) {
            throw blank("lastName", UserMessages.LAST_NAME_REQUIRED);
        }
        if (CobolText.isBlank(form.password())) {
            throw blank("password", UserMessages.PASSWORD_REQUIRED);
        }
        if (CobolText.isBlank(form.userType())) {
            throw blank("userType", UserMessages.USER_TYPE_REQUIRED);
        }

        SecurityUserEntity user = read(userId);
        boolean modified = false;
        // Each IF of UPDATE-USER-INFO compares the keyed value with the stored field and only then
        // moves it, setting USR-MODIFIED-YES.
        if (differs(form.firstName(), user.getFirstName())) {
            user.setFirstName(CobolText.trim(form.firstName()));
            modified = true;
        }
        if (differs(form.lastName(), user.getLastName())) {
            user.setLastName(CobolText.trim(form.lastName()));
            modified = true;
        }
        if (differs(form.password(), user.getPassword())) {
            user.setPassword(CobolText.trim(form.password()));
            modified = true;
        }
        if (differs(form.userType(), user.getUserType())) {
            user.setUserType(CobolText.trim(form.userType()));
            modified = true;
        }

        if (!modified) {
            throw new ScreenValidationException("NO_CHANGES", UserMessages.NOTHING_TO_UPDATE,
                    Map.of());
        }

        try {
            users.saveAndFlush(user);
            if (user.getKeycloakUserId() != null) {
                directory.update(user.getKeycloakUserId(), DirectoryUser.of(user));
            }
        } catch (RuntimeException failure) {
            throw new UpdateFailedException(UserMessages.UNABLE_TO_UPDATE_USER, failure);
        }
        return new UserActionResponse(
                UserMessages.userUpdated(UserTypes.normaliseId(user.getUserId())), detail(user));
    }

    /**
     * DELETE-USER-INFO of COUSR03C, reached by PF5 once the record is displayed.
     *
     * <p>The WHEN OTHER branch of that paragraph reports {@code Unable to Update User...}; the
     * literal belongs to the update program and is kept as the source text of the delete failure.
     */
    @Transactional
    public UserActionResponse delete(String userId) {
        requireUserId(userId);
        SecurityUserEntity user = read(userId);
        try {
            users.delete(user);
            users.flush();
            if (user.getKeycloakUserId() != null) {
                directory.delete(user.getKeycloakUserId());
            }
        } catch (RuntimeException failure) {
            throw new UpdateFailedException(UserMessages.UNABLE_TO_UPDATE_USER, failure);
        }
        return new UserActionResponse(
                UserMessages.userDeleted(UserTypes.normaliseId(user.getUserId())), null);
    }

    private SecurityUserEntity read(String userId) {
        return users.findById(UserTypes.key(userId)).orElseThrow(() -> new RecordNotFoundException(
                UserMessages.USER_ID_NOT_FOUND, Map.of("userId", FieldFlag.NOT_OK)));
    }

    private void requireUserId(String userId) {
        if (CobolText.isBlank(userId)) {
            throw blank("userId", UserMessages.USER_ID_REQUIRED);
        }
    }

    /** The comparison is between fixed length fields, so trailing spaces are not a difference. */
    private boolean differs(String keyed, String stored) {
        return !CobolText.trim(keyed).equals(CobolText.trim(stored));
    }

    private ScreenValidationException blank(String field, String message) {
        return new ScreenValidationException("VALIDATION_ERROR", message,
                Map.of(field, FieldFlag.BLANK));
    }

    private UserDetail detail(SecurityUserEntity user) {
        return new UserDetail(UserTypes.normaliseId(user.getUserId()),
                CobolText.trim(user.getFirstName()), CobolText.trim(user.getLastName()),
                CobolText.trim(user.getPassword()), CobolText.trim(user.getUserType()));
    }
}
