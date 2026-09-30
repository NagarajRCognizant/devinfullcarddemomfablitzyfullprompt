package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.PostgresIntegrationTest;
import com.carddemo.api.dto.UserActionResponse;
import com.carddemo.api.dto.UserAddForm;
import com.carddemo.api.dto.UserDetail;
import com.carddemo.api.dto.UserUpdateForm;
import com.carddemo.domain.UserMessages;
import com.carddemo.exception.DuplicateUserException;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.persistence.repository.SecurityUserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * COUSR01C, COUSR02C and COUSR03C against the converted USRSEC table, so the fixed width key and
 * the padded comparisons are exercised as the file did them.
 */
class UserAdminServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private UserAdminService service;
    @Autowired
    private SecurityUserRepository users;

    @Test
    void theShippedFileIsLoadedWithItsFieldsUnpadded() {
        assertThat(users.findAllById(List.of("ADMIN001", "ADMIN005", "USER0001", "USER0004")))
                .hasSize(4);
        assertThat(service.find("ADMIN001").firstName()).isEqualTo("MARGARET");
        assertThat(service.find("ADMIN005").lastName()).isEqualTo("LACHAPELLE");
        assertThat(service.find("USER0001").userType()).isEqualTo("U");
    }

    @Test
    void aRecordIsWrittenAndReadBackWithoutItsPadding() {
        UserActionResponse response = service.add(
                new UserAddForm("JOHN", "SMITH", "USER0006", "SECRET", "U"));

        assertThat(response.message()).isEqualTo(UserMessages.userAdded("USER0006"));
        UserDetail stored = service.find("USER0006");
        assertThat(stored.firstName()).isEqualTo("JOHN");
        assertThat(stored.lastName()).isEqualTo("SMITH");
        assertThat(stored.password()).isEqualTo("SECRET");
        assertThat(stored.userType()).isEqualTo("U");
    }

    @Test
    void anExistingUserIdIsRefusedWithTheDuplicateText() {
        assertThatThrownBy(() -> service.add(
                new UserAddForm("JOHN", "SMITH", "ADMIN001", "SECRET", "A")))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessage(UserMessages.USER_ALREADY_EXISTS);
    }

    /** Each required field is checked in the order of the map, one message at a time. */
    @Test
    void theRequiredFieldsOfTheAddScreenAreCheckedInMapOrder() {
        assertThatThrownBy(() -> service.add(new UserAddForm(" ", " ", " ", " ", " ")))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(UserMessages.FIRST_NAME_REQUIRED);
        assertThatThrownBy(() -> service.add(new UserAddForm("JOHN", "", "", "", "")))
                .hasMessage(UserMessages.LAST_NAME_REQUIRED);
        assertThatThrownBy(() -> service.add(new UserAddForm("JOHN", "SMITH", "", "", "")))
                .hasMessage(UserMessages.USER_ID_REQUIRED);
        assertThatThrownBy(() -> service.add(new UserAddForm("JOHN", "SMITH", "USER0007", "", "")))
                .hasMessage(UserMessages.PASSWORD_REQUIRED);
        assertThatThrownBy(() -> service.add(
                new UserAddForm("JOHN", "SMITH", "USER0007", "SECRET", "")))
                .hasMessage(UserMessages.USER_TYPE_REQUIRED);
    }

    @Test
    void aChangedFieldIsRewrittenAndReported() {
        UserActionResponse response = service.update("USER0001",
                new UserUpdateForm("LAWRENCE", "THOMAS", "NEWPASS", "U"));

        assertThat(response.message()).isEqualTo(UserMessages.userUpdated("USER0001"));
        assertThat(service.find("USER0001").password()).isEqualTo("NEWPASS");
    }

    /** UPDATE-USER-INFO refuses to rewrite when no editable field differs. */
    @Test
    void anUnchangedSubmissionIsReportedRatherThanRewritten() {
        assertThatThrownBy(() -> service.update("USER0002",
                new UserUpdateForm("AJITH", "KUMAR", "PASSWORD", "U")))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(UserMessages.NOTHING_TO_UPDATE);
    }

    @Test
    void anUnknownUserIdIsReportedByTheUpdateAndTheDelete() {
        assertThatThrownBy(() -> service.update("NOSUCH",
                new UserUpdateForm("A", "B", "C", "U")))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessage(UserMessages.USER_ID_NOT_FOUND);
        assertThatThrownBy(() -> service.delete("NOSUCH"))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessage(UserMessages.USER_ID_NOT_FOUND);
    }

    @Test
    void theRequiredFieldsOfTheUpdateScreenAreCheckedInMapOrder() {
        assertThatThrownBy(() -> service.update(" ", new UserUpdateForm("A", "B", "C", "U")))
                .hasMessage(UserMessages.USER_ID_REQUIRED);
        assertThatThrownBy(() -> service.update("USER0001", new UserUpdateForm("", "B", "C", "U")))
                .hasMessage(UserMessages.FIRST_NAME_REQUIRED);
        assertThatThrownBy(() -> service.update("USER0001", new UserUpdateForm("A", "", "C", "U")))
                .hasMessage(UserMessages.LAST_NAME_REQUIRED);
        assertThatThrownBy(() -> service.update("USER0001", new UserUpdateForm("A", "B", "", "U")))
                .hasMessage(UserMessages.PASSWORD_REQUIRED);
        assertThatThrownBy(() -> service.update("USER0001", new UserUpdateForm("A", "B", "C", "")))
                .hasMessage(UserMessages.USER_TYPE_REQUIRED);
    }

    @Test
    void aDeletedRecordIsRemovedFromTheFile() {
        UserActionResponse response = service.delete("USER0005");

        assertThat(response.message()).isEqualTo(UserMessages.userDeleted("USER0005"));
        assertThat(response.user()).isNull();
        assertThat(users.existsById("USER0005")).isFalse();
    }

    /** The delete and the read take the same eight byte key, so a blank id never reaches the file. */
    @Test
    void aBlankUserIdIsRefusedByTheLookup() {
        assertThatThrownBy(() -> service.find(null))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(UserMessages.USER_ID_REQUIRED);
    }
}
