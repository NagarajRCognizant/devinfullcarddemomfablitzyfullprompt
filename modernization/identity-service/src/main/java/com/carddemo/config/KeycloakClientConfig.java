package com.carddemo.config;

import com.carddemo.keycloak.KeycloakProperties;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/**
 * How this service authenticates to the admin API of the realm.
 *
 * <p>The client credentials grant of its own confidential client, so there is no administrator
 * password anywhere; the token is cached in the authorized client service and re-requested only when
 * it expires, so a screen action is one admin call, not two.
 */
@Configuration
@EnableConfigurationProperties(KeycloakProperties.class)
@ConditionalOnProperty(prefix = "carddemo.security", name = "mode", havingValue = "keycloak",
        matchIfMissing = true)
public class KeycloakClientConfig {

    /**
     * The client the admin calls go over, with timeouts: an unreachable realm has to fail the screen
     * rather than hold the request open, because the record write is waiting on the answer.
     */
    @Bean
    RestClient keycloakRestClient(RestClient.Builder builder, KeycloakProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()))
                .withReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));
        return builder
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService authorizedClients) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations,
                        authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        return manager;
    }
}
