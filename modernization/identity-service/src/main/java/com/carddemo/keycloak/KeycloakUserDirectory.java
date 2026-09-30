package com.carddemo.keycloak;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The realm side of COUSR01C, COUSR02C and COUSR03C, over the Keycloak admin REST API.
 *
 * <p>The service authenticates as its own confidential client with the client credentials grant, so
 * no administrator password is held anywhere; the client is granted the user and role-reading roles of
 * the realm and nothing else, so a defect here cannot change a client or a flow.
 *
 * <p>A new account is created with the keyed password as its credential, which is what the screen
 * did, and with the TOTP enrolment as a required action, which is what the source had no equivalent
 * of: the first sign-on shows the enrolment page of the realm before any screen is reachable.
 */
@Component
@ConditionalOnProperty(prefix = "carddemo.security", name = "mode", havingValue = "keycloak",
        matchIfMissing = true)
public class KeycloakUserDirectory implements UserDirectory {

    /** The enrolment page of the realm, shown at the next sign-on of the account. */
    static final String CONFIGURE_TOTP = "CONFIGURE_TOTP";

    private static final Logger log = LoggerFactory.getLogger(KeycloakUserDirectory.class);

    private final RestClient restClient;
    private final OAuth2AuthorizedClientManager clientManager;
    private final String registrationId;
    private final String adminRealmUri;

    public KeycloakUserDirectory(RestClient restClient, KeycloakProperties properties,
                                 OAuth2AuthorizedClientManager clientManager) {
        this.restClient = restClient;
        this.clientManager = clientManager;
        this.registrationId = properties.clientId();
        this.adminRealmUri = properties.adminRealmUri();
    }

    @Override
    public String create(DirectoryUser user) {
        URI created = call("create " + user.userId(), () -> restClient.post()
                .uri(adminRealmUri + "/users")
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username", user.username(),
                        "firstName", user.firstName(),
                        "lastName", user.lastName(),
                        "enabled", true,
                        "requiredActions", List.of(CONFIGURE_TOTP),
                        "credentials", List.of(passwordCredential(user))))
                .retrieve()
                .toBodilessEntity()
                .getHeaders()
                .getLocation());
        if (created == null) {
            throw new DirectoryAccessException(
                    "Keycloak did not report the id of the created account " + user.userId());
        }
        String path = created.getPath();
        String directoryUserId = path.substring(path.lastIndexOf('/') + 1);
        // Creating the account and granting it its role are two calls, and the caller can only undo a
        // create it has been told the id of. An account without its role could not sign on to anything
        // and would refuse the next attempt at the same id as a duplicate, so it is withdrawn here.
        try {
            grantRealmRole(directoryUserId, user.realmRole());
        } catch (RuntimeException failure) {
            withdrawCreated(directoryUserId, user, failure);
            throw failure;
        }
        return directoryUserId;
    }

    @Override
    public void update(String directoryUserId, DirectoryUser user) {
        call("update " + user.userId(), () -> restClient.put()
                .uri(adminRealmUri + "/users/" + directoryUserId)
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "firstName", user.firstName(),
                        "lastName", user.lastName(),
                        "enabled", true))
                .retrieve()
                .toBodilessEntity());
        call("reset the password of " + user.userId(), () -> restClient.put()
                .uri(adminRealmUri + "/users/" + directoryUserId + "/reset-password")
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(passwordCredential(user))
                .retrieve()
                .toBodilessEntity());
        // COUSR02C can change SEC-USR-TYPE, which is the whole authorisation model of the source, so
        // the role the type maps to is re-applied and the other one withdrawn.
        grantRealmRole(directoryUserId, user.realmRole());
        withdrawRealmRole(directoryUserId,
                user.administrator() ? DirectoryUser.USER_ROLE : DirectoryUser.ADMIN_ROLE);
    }

    @Override
    public void delete(String directoryUserId) {
        call("delete the account " + directoryUserId, () -> restClient.delete()
                .uri(adminRealmUri + "/users/" + directoryUserId)
                .headers(this::authorize)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public Optional<String> findId(String userId) {
        List<Map<String, Object>> found = call("look up " + userId, () -> restClient.get()
                .uri(adminRealmUri + "/users?exact=true&username="
                        + userId.trim().toLowerCase(Locale.ROOT))
                .headers(this::authorize)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { }));
        if (found == null || found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(found.get(0).get("id")).map(String::valueOf);
    }

    private void withdrawCreated(String directoryUserId, DirectoryUser user, RuntimeException cause) {
        try {
            delete(directoryUserId);
        } catch (RuntimeException undoFailure) {
            cause.addSuppressed(undoFailure);
            log.error("The realm account {} of {} was created without its role and could not be "
                    + "withdrawn; reconciliation has to adopt or remove it",
                    directoryUserId, user.userId(), undoFailure);
        }
    }

    private Map<String, Object> passwordCredential(DirectoryUser user) {
        // The administrator keys the password on the screen and the user signs on with it, exactly as
        // USRSEC held it; it is not marked temporary, so no behaviour of the screens changes.
        return Map.of("type", "password", "value", user.password(), "temporary", false);
    }

    private void grantRealmRole(String directoryUserId, String role) {
        Map<String, Object> representation = realmRole(role);
        call("grant " + role, () -> restClient.post()
                .uri(adminRealmUri + "/users/" + directoryUserId + "/role-mappings/realm")
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(representation))
                .retrieve()
                .toBodilessEntity());
    }

    private void withdrawRealmRole(String directoryUserId, String role) {
        Map<String, Object> representation = realmRole(role);
        call("withdraw " + role, () -> restClient.method(HttpMethod.DELETE)
                .uri(adminRealmUri + "/users/" + directoryUserId + "/role-mappings/realm")
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(representation))
                .retrieve()
                .toBodilessEntity());
    }

    private Map<String, Object> realmRole(String role) {
        Map<String, Object> representation = call("read the role " + role, () -> restClient.get()
                .uri(adminRealmUri + "/roles/" + role)
                .headers(this::authorize)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { }));
        if (representation == null) {
            throw new DirectoryAccessException("The realm has no role " + role);
        }
        return Map.of("id", representation.get("id"), "name", representation.get("name"));
    }

    private void authorize(HttpHeaders headers) {
        OAuth2AuthorizedClient client = clientManager.authorize(OAuth2AuthorizeRequest
                .withClientRegistrationId(registrationId)
                .principal(registrationId)
                .build());
        if (client == null) {
            throw new DirectoryAccessException(
                    "The realm did not issue a token for " + registrationId);
        }
        headers.setBearerAuth(client.getAccessToken().getTokenValue());
    }

    /**
     * Every admin call fails the same way: the record write is not committed and the screen reports
     * the message of the source's failure path, rather than leaving the two stores disagreeing.
     */
    private <T> T call(String what, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException failure) {
            throw new DirectoryAccessException("Keycloak could not " + what + ": "
                    + failure.getMessage(), failure);
        }
    }
}
