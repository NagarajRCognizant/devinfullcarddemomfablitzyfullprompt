package com.carddemo.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the realm is and which confidential client this service provisions users as.
 *
 * @param serverUrl base URL of the Keycloak server, without a realm path
 * @param realm realm the CardDemo users live in
 * @param clientId confidential client of this service, the one holding the {@code manage-users} role
 * @param clientSecret secret of that client; supplied per environment and never defaulted
 * @param connectTimeoutMillis connect timeout of an admin call
 * @param readTimeoutMillis read timeout of an admin call
 */
@ConfigurationProperties(prefix = "carddemo.keycloak")
public record KeycloakProperties(String serverUrl, String realm, String clientId,
        String clientSecret, int connectTimeoutMillis, int readTimeoutMillis) {

    public KeycloakProperties {
        realm = realm == null ? "carddemo" : realm;
        clientId = clientId == null ? "carddemo-identity-admin" : clientId;
        connectTimeoutMillis = connectTimeoutMillis <= 0 ? 2000 : connectTimeoutMillis;
        readTimeoutMillis = readTimeoutMillis <= 0 ? 5000 : readTimeoutMillis;
    }

    /** Base path of the admin REST resources of the realm. */
    public String adminRealmUri() {
        return serverUrl + "/admin/realms/" + realm;
    }
}
