package com.carddemo.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.carddemo.persistence.entity.SecurityUserEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The admin REST calls the user administration screens now make, against a recorded realm.
 *
 * <p>What matters is the shape of each call: the enrolment of the authenticator asked for on creation,
 * the role the SEC-USR-TYPE maps to granted and the other one withdrawn, the service account token
 * presented rather than an administrator password, and a failure surfacing as one exception the screen
 * can report and roll the record write back on.
 */
class KeycloakUserDirectoryTest {

    private static final String ADMIN_URI = "http://localhost:8088/admin/realms/carddemo";
    private static final String ACCOUNT_ID = "3f2b6f7a-0f1e-4f5c-9f4a-1c2d3e4f5a6b";

    private MockRestServiceServer realm;
    private KeycloakUserDirectory directory;

    @BeforeEach
    void bindToARecordedRealm() {
        RestClient.Builder builder = RestClient.builder();
        realm = MockRestServiceServer.bindTo(builder).build();
        directory = new KeycloakUserDirectory(builder.build(), new KeycloakProperties(
                "http://localhost:8088", "carddemo", "carddemo-identity-admin", "secret", 2000,
                5000), serviceAccountToken());
    }

    @Test
    void aNewAccountIsCreatedWithTheEnrolmentOfAnAuthenticatorRequired() {
        realm.expect(requestTo(ADMIN_URI + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-account-token"))
                .andExpect(jsonPath("$.username").value("user0006"))
                .andExpect(jsonPath("$.firstName").value("JOHN"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.requiredActions[0]").value("CONFIGURE_TOTP"))
                .andExpect(jsonPath("$.credentials[0].type").value("password"))
                .andExpect(jsonPath("$.credentials[0].temporary").value(false))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(java.net.URI.create(ADMIN_URI + "/users/" + ACCOUNT_ID)));
        expectRoleRead(DirectoryUser.USER_ROLE);
        expectRoleMapping(HttpMethod.POST, DirectoryUser.USER_ROLE);

        String created = directory.create(user("USER0006", "U"));

        assertThat(created).isEqualTo(ACCOUNT_ID);
        realm.verify();
    }

    @Test
    void anAdministratorRecordIsCreatedWithTheAdminRole() {
        realm.expect(requestTo(ADMIN_URI + "/users"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(java.net.URI.create(ADMIN_URI + "/users/" + ACCOUNT_ID)));
        expectRoleRead(DirectoryUser.ADMIN_ROLE);
        expectRoleMapping(HttpMethod.POST, DirectoryUser.ADMIN_ROLE);

        directory.create(user("ADMIN009", "A"));

        realm.verify();
    }

    @Test
    void aCreationTheRealmReportsNoIdForIsAFailure() {
        realm.expect(requestTo(ADMIN_URI + "/users"))
                .andRespond(withStatus(HttpStatus.CREATED));

        assertThatThrownBy(() -> directory.create(user("USER0006", "U")))
                .isInstanceOf(DirectoryAccessException.class)
                .hasMessageContaining("USER0006");
    }

    @Test
    void aChangeOfTypeGrantsTheNewRoleAndWithdrawsTheOldOne() {
        realm.expect(requestTo(ADMIN_URI + "/users/" + ACCOUNT_ID))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.firstName").value("JOHN"))
                .andRespond(withSuccess());
        realm.expect(requestTo(ADMIN_URI + "/users/" + ACCOUNT_ID + "/reset-password"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.value").value("SECRET"))
                .andRespond(withSuccess());
        expectRoleRead(DirectoryUser.ADMIN_ROLE);
        expectRoleMapping(HttpMethod.POST, DirectoryUser.ADMIN_ROLE);
        expectRoleRead(DirectoryUser.USER_ROLE);
        expectRoleMapping(HttpMethod.DELETE, DirectoryUser.USER_ROLE);

        directory.update(ACCOUNT_ID, user("USER0006", "A"));

        realm.verify();
    }

    @Test
    void aDeletedRecordTakesItsAccountWithIt() {
        realm.expect(requestTo(ADMIN_URI + "/users/" + ACCOUNT_ID))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        directory.delete(ACCOUNT_ID);

        realm.verify();
    }

    @Test
    void anAccountIsFoundOnTheLowerCaseFormOfTheKey() {
        realm.expect(requestTo(ADMIN_URI + "/users?exact=true&username=admin001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"" + ACCOUNT_ID + "\"}]",
                        MediaType.APPLICATION_JSON));

        assertThat(directory.findId("ADMIN001")).contains(ACCOUNT_ID);
    }

    @Test
    void aRecordWithNoAccountIsReportedAsAbsentRatherThanAsAFailure() {
        realm.expect(requestTo(ADMIN_URI + "/users?exact=true&username=user0009"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(directory.findId("USER0009")).isEqualTo(Optional.empty());
    }

    @Test
    void anUnreachableRealmIsOneFailureTheScreenCanReport() {
        realm.expect(requestTo(ADMIN_URI + "/users"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> directory.create(user("USER0006", "U")))
                .isInstanceOf(DirectoryAccessException.class)
                .hasMessageContaining("create USER0006");
    }

    @Test
    void aRoleTheRealmDoesNotHaveIsAFailureRatherThanAnAccountWithNoAuthority() {
        realm.expect(requestTo(ADMIN_URI + "/users"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(java.net.URI.create(ADMIN_URI + "/users/" + ACCOUNT_ID)));
        realm.expect(requestTo(ADMIN_URI + "/roles/USER")).andRespond(withResourceNotFound());
        // The account was created before the role was refused, and nothing outside this call knows its
        // id, so it is withdrawn here or it stays behind and refuses the next attempt as a duplicate.
        realm.expect(requestTo(ADMIN_URI + "/users/" + ACCOUNT_ID))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        assertThatThrownBy(() -> directory.create(user("USER0006", "U")))
                .isInstanceOf(DirectoryAccessException.class);
        realm.verify();
    }

    @Test
    void anAccountThatCanBeNeitherGrantedItsRoleNorWithdrawnIsStillOneFailure() {
        realm.expect(requestTo(ADMIN_URI + "/users"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(java.net.URI.create(ADMIN_URI + "/users/" + ACCOUNT_ID)));
        realm.expect(requestTo(ADMIN_URI + "/roles/USER")).andRespond(withServerError());
        realm.expect(requestTo(ADMIN_URI + "/users/" + ACCOUNT_ID)).andRespond(withServerError());

        assertThatThrownBy(() -> directory.create(user("USER0006", "U")))
                .isInstanceOf(DirectoryAccessException.class)
                .hasMessageContaining("read the role USER");
    }

    private void expectRoleRead(String role) {
        realm.expect(requestTo(ADMIN_URI + "/roles/" + role))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"role-" + role + "\",\"name\":\"" + role + "\"}",
                        MediaType.APPLICATION_JSON));
    }

    private void expectRoleMapping(HttpMethod expected, String role) {
        realm.expect(requestTo(ADMIN_URI + "/users/" + ACCOUNT_ID + "/role-mappings/realm"))
                .andExpect(method(expected))
                .andExpect(jsonPath("$[0].name").value(role))
                .andRespond(withSuccess());
    }

    private DirectoryUser user(String userId, String userType) {
        SecurityUserEntity record = new SecurityUserEntity();
        record.setUserId(userId);
        record.setFirstName("JOHN");
        record.setLastName("SMITH");
        record.setPassword("SECRET");
        record.setUserType(userType);
        return DirectoryUser.of(record);
    }

    /** The client credentials grant, already performed: the directory only presents the token. */
    private OAuth2AuthorizedClientManager serviceAccountToken() {
        ClientRegistration registration = ClientRegistration
                .withRegistrationId("carddemo-identity-admin")
                .clientId("carddemo-identity-admin")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://localhost:8088/realms/carddemo/protocol/openid-connect/token")
                .build();
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "service-account-token", Instant.now(), Instant.now().plus(5, ChronoUnit.MINUTES));
        return request -> new OAuth2AuthorizedClient(registration, "carddemo-identity-admin", token);
    }
}
