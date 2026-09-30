package com.carddemo.authorization.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which registered client the service asks the realm for its own token as.
 *
 * @param registrationId the {@code spring.security.oauth2.client.registration} entry to use, which
 *     is the Keycloak client id of the confidential client of this service
 */
@ConfigurationProperties(prefix = "carddemo.authorization.service-client")
public record ServiceClientProperties(String registrationId) {

    public ServiceClientProperties {
        registrationId = registrationId == null ? "carddemo-authorization-svc" : registrationId;
    }
}
