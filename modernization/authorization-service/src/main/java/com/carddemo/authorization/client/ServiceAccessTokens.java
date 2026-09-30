package com.carddemo.authorization.client;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Supplies the bearer token for the outbound call to the account service.
 *
 * <p>A screen request already carries the signed-on user's token and it is forwarded unchanged, so
 * the account service sees the same principal the terminal user had. The Kafka path has no user -
 * CICS ran CP00 from the MQ trigger under the region's authority - so the service asks the realm for
 * a token of its own with the client credentials grant.
 *
 * <p>The token is not minted here and no signing key is held: the realm signs it, the account service
 * validates it against the same JWKS it validates a user token with, and the authorized client
 * manager keeps it until it expires instead of asking for one per call.
 */
@Component
public class ServiceAccessTokens {

    /**
     * The client credentials grant has no end user; this name only labels the cached authorization.
     */
    static final String SERVICE_PRINCIPAL = "carddemo-authorization-service";

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
            throw new ServiceTokenUnavailableException(
                    "The realm did not issue a token for " + registrationId);
        }
        return client.getAccessToken().getTokenValue();
    }
}
