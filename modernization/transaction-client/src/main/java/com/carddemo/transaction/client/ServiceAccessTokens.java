package com.carddemo.transaction.client;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Supplies the bearer token for the outbound calls to the account service.
 *
 * <p>A screen request carries the signed-on user's token and it is forwarded unchanged, so the
 * account service sees the principal the terminal user had. A path with no user - a replay of a
 * bill payment, for instance - falls back to the client credentials grant of this service.
 */
@Component
public class ServiceAccessTokens {

    /** The client credentials grant has no end user; this name only labels the cached authorization. */
    static final String SERVICE_PRINCIPAL = "carddemo-transaction-service";

    private final OAuth2AuthorizedClientManager clientManager;
    private final String registrationId;

    public ServiceAccessTokens(OAuth2AuthorizedClientManager clientManager,
                               ServiceClientProperties properties) {
        this.clientManager = clientManager;
        this.registrationId = properties.registrationId();
    }

    /** The caller's own token when there is one, otherwise the token of the service itself. */
    public String bearerToken() {
        return currentUserToken().orElseGet(this::serviceToken);
    }

    private Optional<String> currentUserToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return Optional.of(jwt.getToken().getTokenValue());
        }
        return Optional.empty();
    }

    private String serviceToken() {
        OAuth2AuthorizedClient client = clientManager.authorize(OAuth2AuthorizeRequest
                .withClientRegistrationId(registrationId)
                .principal(SERVICE_PRINCIPAL)
                .build());
        if (client == null) {
            throw new AccountServiceException(
                    "The realm did not issue a token for " + registrationId, null);
        }
        return client.getAccessToken().getTokenValue();
    }
}
