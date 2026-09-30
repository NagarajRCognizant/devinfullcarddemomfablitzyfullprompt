package com.carddemo.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.PostgresIntegrationTest;
import com.carddemo.persistence.repository.SecurityUserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The ten shipped USRSEC records exist before the realm does, and a realm write can outlive a rolled
 * back record write; both leave a record with no account id, which is what reconciliation repairs.
 */
class UserReconciliationTest extends PostgresIntegrationTest {

    @Autowired
    private SecurityUserRepository users;

    @Test
    void everyShippedRecordIsGivenAnAccountAndKeepsItsId() {
        RecordingDirectory directory = new RecordingDirectory();
        UserReconciliation reconciliation = new UserReconciliation(users, directory);

        ReconciliationReport first = reconciliation.reconcile();

        assertThat(first.records()).isEqualTo(users.count());
        assertThat(first.accountsCreated()).isEqualTo((int) users.count());
        assertThat(first.failures()).isZero();
        assertThat(users.findById("ADMIN001").orElseThrow().getKeycloakUserId()).isNotNull();
    }

    @Test
    void aSecondRunHasNothingToDo() {
        UserReconciliation reconciliation = new UserReconciliation(users, new RecordingDirectory());
        reconciliation.reconcile();

        ReconciliationReport second = reconciliation.reconcile();

        assertThat(second.accountsCreated()).isZero();
        assertThat(second.accountsLinked()).isZero();
    }

    @Test
    void anAccountLeftBehindByARolledBackWriteIsLinkedRatherThanDuplicated() {
        RecordingDirectory directory = new RecordingDirectory();
        directory.existing.put("USER0001", "orphaned-account-id");

        ReconciliationReport report = new UserReconciliation(users, directory).reconcile();

        assertThat(report.accountsLinked()).isEqualTo(1);
        assertThat(users.findById("USER0001").orElseThrow().getKeycloakUserId())
                .isEqualTo("orphaned-account-id");
        assertThat(directory.created).doesNotContain("USER0001");
    }

    @Test
    void oneUnreachableAccountDoesNotStopTheRun() {
        RecordingDirectory directory = new RecordingDirectory();
        directory.failing.add("ADMIN001");

        ReconciliationReport report = new UserReconciliation(users, directory).reconcile();

        assertThat(report.failures()).isEqualTo(1);
        assertThat(report.accountsCreated()).isEqualTo((int) users.count() - 1);
        assertThat(users.findById("ADMIN001").orElseThrow().getKeycloakUserId()).isNull();
        assertThat(users.findById("USER0001").orElseThrow().getKeycloakUserId()).isNotNull();
    }

    /** A realm that remembers what it was asked, so no Keycloak is needed for the walk itself. */
    private static final class RecordingDirectory implements UserDirectory {

        private final Map<String, String> existing = new LinkedHashMap<>();
        private final List<String> created = new ArrayList<>();
        private final List<String> failing = new ArrayList<>();

        @Override
        public String create(DirectoryUser user) {
            if (failing.contains(user.userId())) {
                throw new DirectoryAccessException("Keycloak could not create " + user.userId());
            }
            created.add(user.userId());
            return "account-" + user.username();
        }

        @Override
        public void update(String directoryUserId, DirectoryUser user) {
        }

        @Override
        public void delete(String directoryUserId) {
        }

        @Override
        public Optional<String> findId(String userId) {
            if (failing.contains(userId)) {
                throw new DirectoryAccessException("Keycloak could not look up " + userId);
            }
            return Optional.ofNullable(existing.get(userId));
        }
    }
}
