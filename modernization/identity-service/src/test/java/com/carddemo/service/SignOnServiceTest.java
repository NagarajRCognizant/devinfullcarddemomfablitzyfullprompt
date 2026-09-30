package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carddemo.api.dto.SignOnRequest;
import com.carddemo.api.dto.SignOnResponse;
import com.carddemo.config.TokenIssuer;
import com.carddemo.domain.UserMessages;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.exception.SignOnFailedException;
import com.carddemo.persistence.entity.SecurityUserEntity;
import com.carddemo.persistence.repository.SecurityUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The edits, the lookup, the password comparison and the routing of COSGN00C. */
class SignOnServiceTest {

    private final SecurityUserRepository users = mock(SecurityUserRepository.class);
    private final TokenIssuer tokenIssuer = mock(TokenIssuer.class);
    private final SignOnService service = new SignOnService(users, tokenIssuer);

    @BeforeEach
    void stubTokenIssuer() {
        when(tokenIssuer.issue(any(), any())).thenReturn("token");
        when(tokenIssuer.ttlSeconds()).thenReturn(1800L);
    }

    @Test
    void aMissingUserIdIsReportedBeforeTheFileIsRead() {
        assertThatThrownBy(() -> service.signOn(new SignOnRequest("  ", "PASSWORD")))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(UserMessages.SIGNON_USER_ID_REQUIRED);
    }

    @Test
    void aMissingPasswordIsReportedAfterTheUserId() {
        assertThatThrownBy(() -> service.signOn(new SignOnRequest("ADMIN001", null)))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(UserMessages.SIGNON_PASSWORD_REQUIRED);
    }

    @Test
    void anUnknownUserIdIsReportedWithTheNotFoundText() {
        when(users.findById(eq("USER9999"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.signOn(new SignOnRequest("user9999", "password")))
                .isInstanceOf(SignOnFailedException.class)
                .hasMessage(UserMessages.SIGNON_USER_NOT_FOUND);
    }

    @Test
    void aWrongPasswordIsReportedWithoutSayingWhichFieldFailed() {
        when(users.findById("USER0001")).thenReturn(Optional.of(user("USER0001", "PASSWORD", "U")));

        assertThatThrownBy(() -> service.signOn(new SignOnRequest("USER0001", "SECRET")))
                .isInstanceOf(SignOnFailedException.class)
                .hasMessage(UserMessages.SIGNON_WRONG_PASSWORD);
    }

    /** Both fields are uppercased before the read and the comparison. */
    @Test
    void aLowerCaseUserIdAndPasswordAreUppercasedFirst() {
        when(users.findById("USER0001")).thenReturn(Optional.of(user("USER0001", "PASSWORD", "U")));

        SignOnResponse response = service.signOn(new SignOnRequest("user0001", "password"));

        assertThat(response.userId()).isEqualTo("USER0001");
        assertThat(response.nextScreen()).isEqualTo(SignOnService.MAIN_MENU_PROGRAM);
    }

    /** WS-USER-PWD is PIC X(08), so a longer keyed password is truncated before the comparison. */
    @Test
    void aPasswordLongerThanEightCharactersIsTruncatedBeforeComparison() {
        when(users.findById("USER0001")).thenReturn(Optional.of(user("USER0001", "PASSWORD", "U")));

        assertThat(service.signOn(new SignOnRequest("USER0001", "PASSWORDXYZ")).userId())
                .isEqualTo("USER0001");
    }

    @Test
    void anAdministratorIsRoutedToTheAdminMenu() {
        when(users.findById("ADMIN001")).thenReturn(Optional.of(user("ADMIN001", "PASSWORD", "A")));

        SignOnResponse response = service.signOn(new SignOnRequest("ADMIN001", "PASSWORD"));

        assertThat(response.userType()).isEqualTo(UserTypes.ADMIN);
        assertThat(response.nextScreen()).isEqualTo(SignOnService.ADMIN_MENU_PROGRAM);
        assertThat(response.accessToken()).isEqualTo("token");
        assertThat(response.expiresInSeconds()).isEqualTo(1800L);
    }

    /** Any user type other than 'A' reaches the regular menu, including a blank one. */
    @Test
    void anUnknownUserTypeIsTreatedAsARegularUser() {
        when(users.findById("USER0002")).thenReturn(Optional.of(user("USER0002", "PASSWORD", " ")));

        assertThat(service.signOn(new SignOnRequest("USER0002", "PASSWORD")).nextScreen())
                .isEqualTo(SignOnService.MAIN_MENU_PROGRAM);
    }

    private static SecurityUserEntity user(String id, String password, String type) {
        SecurityUserEntity user = new SecurityUserEntity();
        user.setUserId(id);
        user.setFirstName("FIRST");
        user.setLastName("LAST");
        user.setPassword(password);
        user.setUserType(type);
        return user;
    }
}
