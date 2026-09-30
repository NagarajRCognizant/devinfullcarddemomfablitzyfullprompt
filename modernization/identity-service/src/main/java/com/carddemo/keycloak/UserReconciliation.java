package com.carddemo.keycloak;

import com.carddemo.persistence.entity.SecurityUserEntity;
import com.carddemo.persistence.repository.SecurityUserRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Gives every USRSEC record a realm account, and every record the id of the account it has.
 *
 * <p>Two cases need it. The ten records shipped with the source file exist before the realm does, so
 * their accounts have to be created once; and a realm write that outlived a rolled back USRSEC write
 * leaves an account whose record either does not exist or does not name it.
 *
 * <p>The password of the record is used as the initial credential, as the sign-on screen of the source
 * would have accepted it, and the enrolment of the authenticator is required at the first sign-on.
 */
@Service
public class UserReconciliation {

    private static final Logger LOG = LoggerFactory.getLogger(UserReconciliation.class);

    private final SecurityUserRepository users;
    private final UserDirectory directory;

    public UserReconciliation(SecurityUserRepository users, UserDirectory directory) {
        this.users = users;
        this.directory = directory;
    }

    /**
     * Walks the records and returns what it did.
     *
     * <p>Each record is linked by its own write, so one unreachable account does not undo the accounts
     * already linked.
     */
    public ReconciliationReport reconcile() {
        List<SecurityUserEntity> all = users.findAll();
        int linked = 0;
        int created = 0;
        int failed = 0;
        for (SecurityUserEntity user : all) {
            if (user.getKeycloakUserId() != null) {
                continue;
            }
            try {
                Optional<String> existing = directory.findId(user.getUserId());
                String id = existing.orElseGet(() -> directory.create(DirectoryUser.of(user)));
                if (id == null) {
                    continue;
                }
                link(user.getUserId(), id);
                if (existing.isPresent()) {
                    linked++;
                } else {
                    created++;
                }
            } catch (RuntimeException failure) {
                failed++;
                LOG.error("Could not reconcile the account of {}: {}", user.getUserId(),
                        failure.getMessage(), failure);
            }
        }
        return new ReconciliationReport(all.size(), created, linked, failed);
    }

    private void link(String userId, String directoryUserId) {
        users.findById(userId).ifPresent(user -> {
            user.setKeycloakUserId(directoryUserId);
            users.saveAndFlush(user);
        });
    }
}
