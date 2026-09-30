package com.carddemo.config;

import com.carddemo.security.CardDemoJwtDecoders;
import com.carddemo.security.RealmRoleAuthorities;
import com.carddemo.security.TokenProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Replaces the CICS sign-on that guarded the transactions, with the realm performing it.
 *
 * <p>The rule of the source is a single one: only COSGN00C is reachable without having signed on,
 * and the four user screens are only reachable from the admin menu, which COSGN00C routes
 * administrators to. COSGN00C itself is now the login page of the realm, which is why no endpoint of
 * this service is public any more, and the admin menu screens keep requiring the administrator, which
 * is asked of the USRSEC record and not of the realm role alone.
 */
@Configuration
@EnableConfigurationProperties(TokenProperties.class)
@ConditionalOnProperty(prefix = "carddemo.security", name = "mode", havingValue = "keycloak",
        matchIfMissing = true)
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AdministratorOfRecord administrators)
            throws Exception {
        return http
                // The API is stateless and token authenticated, so there is no session or form to
                // protect with a CSRF token.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/users/**", "/api/admin-menu/**")
                                .access(administrators)
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
}
