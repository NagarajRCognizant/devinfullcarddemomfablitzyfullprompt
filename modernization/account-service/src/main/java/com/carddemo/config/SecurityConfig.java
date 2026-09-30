package com.carddemo.config;

import com.carddemo.security.CardDemoJwtDecoders;
import com.carddemo.security.RealmRoleAuthorities;
import com.carddemo.security.TokenProperties;
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
 * Accepts the access token of the realm in place of the CICS sign-on.
 *
 * <p>Both account screens were reachable from the main menu by any signed-on user, so every endpoint
 * only requires authentication; the admin-only rule of COMEN01C is a per option rule and stays in
 * the menu service, where the source keeps it.
 *
 * <p>The token has to name this service in its audience, so the token of a caller that was sent to
 * another service is refused even though the realm signed it.
 */
@Configuration
@EnableConfigurationProperties(TokenProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Stateless token authentication; there is no session or form to protect.
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
}
