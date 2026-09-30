package com.carddemo.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.persistence.entity.SecurityUserEntity;
import org.junit.jupiter.api.Test;

/** The fixed width USRSEC record as the realm needs it. */
class DirectoryUserTest {

    @Test
    void theEightCharacterKeyBecomesALowerCaseUsername() {
        DirectoryUser user = DirectoryUser.of(record("ADMIN001", "A"));

        assertThat(user.userId()).isEqualTo("ADMIN001");
        assertThat(user.username()).isEqualTo("admin001");
    }

    @Test
    void theTypeAOfTheAdminMenuIsTheAdminRole() {
        DirectoryUser user = DirectoryUser.of(record("ADMIN001", "A"));

        assertThat(user.realmRole()).isEqualTo(DirectoryUser.ADMIN_ROLE);
        assertThat(user.administrator()).isTrue();
    }

    @Test
    void everyOtherTypeIsTheUserRole() {
        assertThat(DirectoryUser.of(record("USER0001", "U")).realmRole())
                .isEqualTo(DirectoryUser.USER_ROLE);
        assertThat(DirectoryUser.of(record("USER0002", " ")).realmRole())
                .isEqualTo(DirectoryUser.USER_ROLE);
    }

    private SecurityUserEntity record(String userId, String userType) {
        SecurityUserEntity user = new SecurityUserEntity();
        user.setUserId(userId);
        user.setFirstName("MARGARET");
        user.setLastName("BROWN");
        user.setPassword("SECRET");
        user.setUserType(userType);
        return user;
    }
}
