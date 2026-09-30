package com.carddemo.authorization.config;

import com.carddemo.authorization.client.ServiceClientProperties;
import com.carddemo.security.CardDemoJwtDecoders;
import com.carddemo.security.RealmRoleAuthorities;
import com.carddemo.security.TokenProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Accepts the access token of the realm in place of the CICS sign-on, exactly as the account service
 * does, and additionally obtains a token of its own for the path that has no signed-on user.
 *
 * <p>The MQ triggered path has no user: CICS started transaction CP00 from the trigger monitor under
 * the region's own authority. The equivalent here is the client credentials grant of the confidential
 * client of this service; nothing in this context signs a token any more, so no service holds a
 * signing key.
 */
@Configuration
@EnableConfigurationProperties({TokenProperties.class, ServiceClientProperties.class})
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(server -> server.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RealmRoleAuthorities());
        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder(TokenProperties properties) {
        return CardDemoJwtDecoders.servlet(properties);
    }

    /**
     * Caches the service token in the authorized client service and asks for a new one only once the
     * current one is spent, so a burst of authorization requests is one token, not one token each.
     */
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
