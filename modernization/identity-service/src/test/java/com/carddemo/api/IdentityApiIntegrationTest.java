package com.carddemo.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.PostgresIntegrationTest;
import com.carddemo.domain.UserMessages;
import com.carddemo.service.SignOnService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The five converted transactions over HTTP against the seeded USRSEC table.
 *
 * <p>The user screens carry the administrator authority because the admin menu that reaches them is
 * only offered to SEC-USR-TYPE 'A'; the sign-on endpoint is the only one reachable without a token,
 * as COSGN00C was the only transaction reachable without having signed on.
 */
@AutoConfigureMockMvc
class IdentityApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anAdministratorSignsOnAndIsRoutedToTheAdminMenu() throws Exception {
        mockMvc.perform(post("/api/signon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"admin001\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("ADMIN001"))
                .andExpect(jsonPath("$.userType").value("A"))
                .andExpect(jsonPath("$.nextScreen").value(SignOnService.ADMIN_MENU_PROGRAM))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void aRegularUserSignsOnAndIsRoutedToTheMainMenu() throws Exception {
        mockMvc.perform(post("/api/signon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"USER0001\",\"password\":\"PASSWORD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextScreen").value(SignOnService.MAIN_MENU_PROGRAM));
    }

    @Test
    void aWrongPasswordIsRejectedWithTheSourceText() throws Exception {
        mockMvc.perform(post("/api/signon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"USER0001\",\"password\":\"NOPE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(UserMessages.SIGNON_WRONG_PASSWORD));
    }

    @Test
    void aMissingUserIdIsAValidationError() throws Exception {
        mockMvc.perform(post("/api/signon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"\",\"password\":\"PASSWORD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(UserMessages.SIGNON_USER_ID_REQUIRED))
                .andExpect(jsonPath("$.fieldFlags.userId").value("BLANK"));
    }

    @Test
    void aMalformedSignOnBodyIsARequestError() throws Exception {
        mockMvc.perform(post("/api/signon").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("MALFORMED_REQUEST"));
    }

    @Test
    void theUserScreensAreClosedToAnUnauthenticatedCaller() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    /** COSGN00C only routes an administrator to the menu that reaches these screens. */
    @Test
    @WithMockUser(username = "USER0001", authorities = "ROLE_USER")
    void theUserScreensAreClosedToARegularUser() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ADMIN001", authorities = "ROLE_ADMIN")
    void anAdministratorListsThePageOfUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(10))
                .andExpect(jsonPath("$.users[0].userId").value("ADMIN001"))
                .andExpect(jsonPath("$.pageNumber").value(1));
    }

    @Test
    @WithMockUser(username = "ADMIN001", authorities = "ROLE_ADMIN")
    void anAdministratorAddsUpdatesAndDeletesAUser() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"JOHN","lastName":"SMITH","userId":"USER0009",
                                 "password":"SECRET","userType":"U"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(UserMessages.userAdded("USER0009")));

        mockMvc.perform(put("/api/users/{id}", "USER0009")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"JOHN","lastName":"SMITHERS","password":"SECRET",
                                 "userType":"U"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(UserMessages.userUpdated("USER0009")))
                .andExpect(jsonPath("$.user.lastName").value("SMITHERS"));

        mockMvc.perform(get("/api/users/{id}", "USER0009"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").value("SECRET"));

        mockMvc.perform(delete("/api/users/{id}", "USER0009"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(UserMessages.userDeleted("USER0009")));

        mockMvc.perform(get("/api/users/{id}", "USER0009"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(UserMessages.USER_ID_NOT_FOUND));
    }

    @Test
    @WithMockUser(username = "ADMIN001", authorities = "ROLE_ADMIN")
    void anExistingUserIdIsAConflict() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"JOHN","lastName":"SMITH","userId":"ADMIN001",
                                 "password":"SECRET","userType":"A"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(UserMessages.USER_ALREADY_EXISTS));
    }

    @Test
    @WithMockUser(username = "ADMIN001", authorities = "ROLE_ADMIN")
    void theAdminMenuListsItsOptionsAndRejectsAnInvalidOne() throws Exception {
        mockMvc.perform(get("/api/admin-menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));

        mockMvc.perform(get("/api/admin-menu/selection").param("option", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.program").value("COUSR00C"));

        mockMvc.perform(get("/api/admin-menu/selection").param("option", "9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(UserMessages.INVALID_MENU_OPTION));
    }
}
