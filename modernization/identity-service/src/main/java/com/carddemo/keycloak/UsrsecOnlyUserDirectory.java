package com.carddemo.keycloak;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The user administration screens as they behaved before the realm existed: USRSEC is the whole
 * credential store, so there is no second write to perform.
 *
 * <p>Selected by {@code carddemo.security.mode=legacy} only, for the COBOL parity evidence.
 */
@Component
@ConditionalOnProperty(prefix = "carddemo.security", name = "mode", havingValue = "legacy")
public class UsrsecOnlyUserDirectory implements UserDirectory {

    @Override
    public String create(DirectoryUser user) {
        return null;
    }

    @Override
    public void update(String directoryUserId, DirectoryUser user) {
    }

    @Override
    public void delete(String directoryUserId) {
    }

    @Override
    public Optional<String> findId(String userId) {
        return Optional.empty();
    }
}
