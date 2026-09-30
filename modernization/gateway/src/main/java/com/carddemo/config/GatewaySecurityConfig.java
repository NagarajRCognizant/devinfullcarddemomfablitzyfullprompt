package com.carddemo.config;

import com.carddemo.security.CardDemoJwtDecoders;
import com.carddemo.security.RealmRoleAuthorities;
import com.carddemo.security.TokenProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Rejects unauthenticated calls at the edge, so a request that the CICS sign-on would have refused
 * never reaches a service. Each service still applies its own rules, because the gateway is not the
 * only way in for internal callers such as the batch job.
 *
 * <p>The sign-on itself is no longer an endpoint here: the screens are redirected to the realm, which
 * asks for the password and the TOTP and hands back the token this filter chain validates.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(TokenProperties.class)
public class GatewaySecurityConfig {

    @Bean
    SecurityWebFilterChain filterChain(ServerHttpSecurity http, ReactiveJwtDecoder decoder) {
        return http
                // Stateless token authentication; there is no session or form to protect.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt
                        .jwtDecoder(decoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(TokenProperties properties) {
        return CardDemoJwtDecoders.reactive(properties);
    }

    private ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new ReactiveJwtGrantedAuthoritiesConverterAdapter(new RealmRoleAuthorities()));
        return converter;
    }
}
