package com.carddemo.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.domain.UserMessages;
import com.carddemo.service.CurrentUserService;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The transactions over HTTP in the mode the application is deployed in, where the realm has already
 * authenticated the caller with a second factor and this service only reads its token.
 *
 * <p>COSGN00C no longer has an endpoint to post credentials to; what replaces its PROCESS-ENTER-KEY is
 * {@code /api/me}, which reads the USRSEC row of the verified token and routes to the same two menus.
 * The user screens still carry the administrator authority of the admin menu, which now arrives as a
 * realm role rather than as a claim this service signed.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(properties = "carddemo.security.mode=keycloak")
class KeycloakModeApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Flyway flyway;

    @BeforeEach
    void reloadTheSecurityFile() {
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void anAdministratorTokenIsRoutedToTheAdminMenu() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(token("admin001", "ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("ADMIN001"))
                .andExpect(jsonPath("$.userType").value("A"))
                .andExpect(jsonPath("$.nextScreen").value(CurrentUserService.ADMIN_MENU_PROGRAM));
    }

    @Test
    void aRegularUserTokenIsRoutedToTheMainMenu() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(token("user0001", "USER"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("USER0001"))
                .andExpect(jsonPath("$.userType").value("U"))
                .andExpect(jsonPath("$.nextScreen").value(CurrentUserService.MAIN_MENU_PROGRAM));
    }

    @Test
    void noTokenReachesNothing() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void theSignOnEndpointOfTheSourceIsNoLongerReachable() throws Exception {
        // The realm authenticates now, so there is nothing to post credentials to; the endpoint is not
        // registered at all in this mode, and an anonymous request is refused before that matters.
        mockMvc.perform(post("/api/signon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"admin001\",\"password\":\"password\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void aRegularUserCannotReachTheUserScreensOfTheAdminMenu() throws Exception {
        mockMvc.perform(get("/api/users").with(jwt().jwt(token("user0001", "USER"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorReachesTheUserScreensOfTheAdminMenu() throws Exception {
        mockMvc.perform(get("/api/users").with(jwt().jwt(token("admin001", "ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void aTokenForSomebodyWithNoRecordIsRefusedWithTheTextOfTheProgram() throws Exception {
        // NOTFND of READ-USER-SEC-FILE: a realm account with no USRSEC row cannot reach a screen,
        // because every screen behind the menus keys off that row.
        mockMvc.perform(get("/api/me").with(jwt().jwt(token("nobody", "USER"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.message").value(UserMessages.SIGNON_USER_NOT_FOUND));
    }

    @Test
    void anAdminRoleTheRecordDoesNotHaveIsNotAnAuthority() throws Exception {
        // SEC-USR-TYPE decides what a user is, so a realm role granted behind the record's back does
        // not make an administrator of USER0001: the sign-on is refused and the screens stay closed.
        mockMvc.perform(get("/api/me").with(jwt().jwt(token("user0001", "ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(UserMessages.SIGNON_UNABLE_TO_VERIFY));
        mockMvc.perform(get("/api/users").with(jwt().jwt(token("user0001", "ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    /** A token of the shape the carddemo realm issues to the SPA after the OTP step. */
    private Consumer<Jwt.Builder> token(String username, String realmRole) {
        return builder -> builder
                .claim("preferred_username", username)
                .claim("azp", "carddemo-ui")
                .claim("realm_access", Map.of("roles", List.of(realmRole)));
    }
}
