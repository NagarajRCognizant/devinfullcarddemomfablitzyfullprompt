package com.carddemo.security;

import java.util.function.Supplier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Reads the metadata of the issuer on the first token instead of at startup.
 *
 * <p>Reading it at startup makes every service depend on Keycloak being up before it can report itself
 * healthy, which the compose and chart start-up order would then have to guarantee; a token cannot be
 * validated before Keycloak is up either way, so the wait belongs on the first request.
 */
final class LazyJwtDecoder implements JwtDecoder {

    private final Supplier<JwtDecoder> factory;
    private volatile JwtDecoder delegate;

    LazyJwtDecoder(Supplier<JwtDecoder> factory) {
        this.factory = factory;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        JwtDecoder current = delegate;
        if (current == null) {
            synchronized (this) {
                current = delegate;
                if (current == null) {
                    current = factory.get();
                    delegate = current;
                }
            }
        }
        return current.decode(token);
    }
}
