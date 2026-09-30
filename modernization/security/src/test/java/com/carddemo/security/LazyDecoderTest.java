package com.carddemo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

/**
 * The decoders are built on the first token rather than at startup; once built, the metadata of the
 * realm is not fetched again, so the cost is paid by one request and not by every one.
 */
class LazyDecoderTest {

    @Test
    void theServletDecoderIsBuiltOnceAndThenReused() {
        AtomicInteger built = new AtomicInteger();
        JwtDecoder decoder = new LazyJwtDecoder(() -> {
            built.incrementAndGet();
            return token -> TokenFixtures.token().build();
        });

        assertThat(built).hasValue(0);
        assertThat(decoder.decode("first").getSubject()).isNull();
        decoder.decode("second");

        assertThat(built).hasValue(1);
    }

    @Test
    void aFailureOfTheServletDecoderIsTheFailureOfTheDecodeItself() {
        JwtDecoder decoder = new LazyJwtDecoder(() -> token -> {
            throw new BadJwtException("no key of the realm matches this token");
        });

        assertThatThrownBy(() -> decoder.decode("forged")).isInstanceOf(BadJwtException.class);
    }

    @Test
    void theGatewayDecoderIsBuiltOnceAndThenReused() {
        AtomicInteger built = new AtomicInteger();
        Jwt expected = TokenFixtures.token().build();
        ReactiveJwtDecoder decoder = new LazyReactiveJwtDecoder(() -> {
            built.incrementAndGet();
            return token -> Mono.just(expected);
        });

        assertThat(decoder.decode("first").block()).isSameAs(expected);
        assertThat(decoder.decode("second").block()).isSameAs(expected);
        assertThat(built).hasValue(1);
    }

    @Test
    void theGatewayDecoderIsAlsoBuiltAgainstAnUnreachableRealmWithoutFailing() {
        TokenProperties unreachable = new TokenProperties("http://127.0.0.1:1/realms/carddemo", null,
                TokenFixtures.AUDIENCE, 60L, true, List.of("carddemo-ui"), List.of(),
                List.of("otp"), List.of());

        ReactiveJwtDecoder decoder = CardDemoJwtDecoders.reactive(unreachable);

        assertThatThrownBy(() -> decoder.decode("not.a.token").block())
                .isInstanceOf(RuntimeException.class);
    }
}
