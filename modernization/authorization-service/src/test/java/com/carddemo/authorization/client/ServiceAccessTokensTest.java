package com.carddemo.authorization.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The two callers of the account service. A screen request must reach the account service as the
 * signed-on user, because the source read the VSAM files under the terminal user's authority; the
 * Kafka path has no user at all - CP00 ran from the MQ trigger under the region - so the realm issues
 * a token for the service itself instead of the call going out anonymously.
 *
 * <p>Nothing is signed here any more: the realm signs both tokens, and the authorized client manager
 * holds the service one until it expires.
 */
@ExtendWith(MockitoExtension.class)
class ServiceAccessTokensTest {

    @Mock
    private OAuth2AuthorizedClientManager clientManager;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aScreenRequestForwardsTheTokenOfTheSignedOnUserUnchanged() {
        signedOn();

        assertThat(tokens().bearerToken()).isEqualTo("user-token");
    }

    @Test
    void aScreenRequestNeverAsksTheRealmForATokenOfItsOwn() {
        signedOn();

        tokens().bearerToken();

        verify(clientManager, times(0)).authorize(any());
    }

    @Test
    void theKafkaPathPresentsTheTokenTheRealmIssuedToTheServiceClient() {
        when(clientManager.authorize(any())).thenReturn(authorizedClient("service-token"));

        assertThat(tokens().bearerToken()).isEqualTo("service-token");
    }

    @Test
    void aRealmThatIssuesNoTokenStopsTheCallRatherThanSendingItUnauthenticated() {
        when(clientManager.authorize(any())).thenReturn(null);

        assertThatThrownBy(() -> tokens().bearerToken())
                .isInstanceOf(ServiceTokenUnavailableException.class)
                .hasMessageContaining("carddemo-authorization-svc");
    }

    private ServiceAccessTokens tokens() {
        return new ServiceAccessTokens(clientManager, new ServiceClientProperties(null));
    }

    private static void signedOn() {
        Jwt jwt = Jwt.withTokenValue("user-token")
                .header("alg", "RS256")
                .subject("USER0001")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("preferred_username", "user0001")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static OAuth2AuthorizedClient authorizedClient(String value) {
        ClientRegistration registration = ClientRegistration
                .withRegistrationId("carddemo-authorization-svc")
                .clientId("carddemo-authorization-svc")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://localhost:8088/realms/carddemo/protocol/openid-connect/token")
                .build();
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, value,
                Instant.now(), Instant.now().plusSeconds(60));
        return new OAuth2AuthorizedClient(registration, ServiceAccessTokens.SERVICE_PRINCIPAL, token);
    }
}
