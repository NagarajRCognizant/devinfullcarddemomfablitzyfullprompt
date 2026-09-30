package com.carddemo.security;

import java.util.function.Supplier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

/**
 * The gateway's decoder, deferred the same way {@link LazyJwtDecoder} defers a servlet one.
 */
final class LazyReactiveJwtDecoder implements ReactiveJwtDecoder {

    private final Supplier<ReactiveJwtDecoder> factory;
    private volatile ReactiveJwtDecoder delegate;

    LazyReactiveJwtDecoder(Supplier<ReactiveJwtDecoder> factory) {
        this.factory = factory;
    }

    @Override
    public Mono<Jwt> decode(String token) {
        return Mono.fromSupplier(this::delegate).flatMap(decoder -> decoder.decode(token));
    }

    private ReactiveJwtDecoder delegate() {
        ReactiveJwtDecoder current = delegate;
        if (current == null) {
            synchronized (this) {
                current = delegate;
                if (current == null) {
                    current = factory.get();
                    delegate = current;
                }
            }
        }
        return current;
    }
}
