package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.api.dto.UserAddForm;
import com.carddemo.api.dto.UserUpdateForm;
import com.carddemo.domain.UserMessages;
import com.carddemo.exception.UpdateFailedException;
import com.carddemo.keycloak.DirectoryAccessException;
import com.carddemo.keycloak.DirectoryUser;
import com.carddemo.keycloak.UserDirectory;
import com.carddemo.persistence.repository.SecurityUserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * COUSR01C, COUSR02C and COUSR03C in the mode the application is deployed in: each of the three now
 * writes the USRSEC record and the realm account the user signs on with.
 *
 * <p>The record is written first, so a realm that refuses the write leaves no record behind and the
 * screen reports the failure message of the source program; the record keeps the id of its account, so
 * the later screens do not have to search the realm for it.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "carddemo.security.mode=keycloak")
class UserAdminProvisioningIntegrationTest {

    @Autowired
    private UserAdminService service;
    @Autowired
    private SecurityUserRepository users;
    @Autowired
    private Flyway flyway;
    @Autowired
    private RecordingDirectory directory;

    @BeforeEach
    void reloadTheSecurityFileAndTheRealm() {
        flyway.clean();
        flyway.migrate();
        directory.calls.clear();
        directory.failing = false;
    }

    @Test
    void anAddedRecordIsGivenAnAccountThatHasToEnrolAnAuthenticator() {
        service.add(new UserAddForm("JOHN", "SMITH", "USER0006", "SECRET", "U"));

        assertThat(directory.calls).containsExactly("create USER0006 as USER");
        assertThat(users.findById("USER0006").orElseThrow().getKeycloakUserId())
                .isEqualTo("account-user0006");
    }

    @Test
    void aRealmThatRefusesTheAccountLeavesNoRecordBehind() {
        directory.failing = true;

        assertThatThrownBy(() -> service.add(
                new UserAddForm("JOHN", "SMITH", "USER0006", "SECRET", "U")))
                .isInstanceOf(DirectoryAccessException.class);

        assertThat(users.findById("USER0006")).isEmpty();
    }

    @Test
    void anUpdatedRecordUpdatesTheAccountItNames() {
        service.add(new UserAddForm("JOHN", "SMITH", "USER0006", "SECRET", "U"));
        directory.calls.clear();

        service.update("USER0006", new UserUpdateForm("JOHN", "SMITH", "SECRET", "A"));

        assertThat(directory.calls).containsExactly("update account-user0006 as ADMIN");
    }

    @Test
    void aRealmThatRefusesTheUpdateReportsTheMessageOfTheProgram() {
        service.add(new UserAddForm("JOHN", "SMITH", "USER0006", "SECRET", "U"));
        directory.failing = true;

        assertThatThrownBy(() -> service.update("USER0006",
                new UserUpdateForm("JANE", "SMITH", "SECRET", "U")))
                .isInstanceOf(UpdateFailedException.class)
                .hasMessage(UserMessages.UNABLE_TO_UPDATE_USER);

        assertThat(users.findById("USER0006").orElseThrow().getFirstName().trim())
                .isEqualTo("JOHN");
    }

    @Test
    void aDeletedRecordTakesItsAccountWithIt() {
        service.add(new UserAddForm("JOHN", "SMITH", "USER0006", "SECRET", "U"));
        directory.calls.clear();

        service.delete("USER0006");

        assertThat(directory.calls).containsExactly("delete account-user0006");
        assertThat(users.findById("USER0006")).isEmpty();
    }

    @Test
    void aRecordShippedWithTheFileHasNoAccountToUpdateYet() {
        // The ten records predate the realm; reconciliation provisions them, and until it has run an
        // update of one of them is the USRSEC write of the source program and nothing more.
        service.update("ADMIN001", new UserUpdateForm("MARGARET", "BROWNE", "PASSWORD", "A"));

        assertThat(directory.calls).isEmpty();
    }

    /** The realm as a recording of what it was asked, so the wiring is what is under test. */
    static final class RecordingDirectory implements UserDirectory {

        private final List<String> calls = new ArrayList<>();
        private boolean failing;

        @Override
        public String create(DirectoryUser user) {
            refuseWhenFailing("create " + user.userId());
            calls.add("create " + user.userId() + " as " + user.realmRole());
            return "account-" + user.username();
        }

        @Override
        public void update(String directoryUserId, DirectoryUser user) {
            refuseWhenFailing("update " + directoryUserId);
            calls.add("update " + directoryUserId + " as " + user.realmRole());
        }

        @Override
        public void delete(String directoryUserId) {
            refuseWhenFailing("delete " + directoryUserId);
            calls.add("delete " + directoryUserId);
        }

        @Override
        public Optional<String> findId(String userId) {
            return Optional.empty();
        }

        private void refuseWhenFailing(String what) {
            if (failing) {
                throw new DirectoryAccessException("Keycloak could not " + what);
            }
        }
    }

    @TestConfiguration
    static class RecordedRealm {

        @Bean
        @Primary
        RecordingDirectory recordingDirectory() {
            return new RecordingDirectory();
        }
    }
}
