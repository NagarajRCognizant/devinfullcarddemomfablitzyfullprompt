package com.carddemo.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** The complemented child key of PAUTDTL1 and the two values CBPAUP0C and COPAUS2C recover from it. */
class AuthorizationKeyTest {

    /** 8500-INSERT-AUTH: {@code 99999 - yyddd} and {@code 999999999 - hhmmssmmm}. */
    @Test
    void theKeyIsTheNinesComplementOfTheJulianDateAndTheTimeOfDay() {
        AuthorizationKey key = AuthorizationKey.of(LocalDateTime.of(2024, 3, 1, 13, 45, 30, 250_000_000));

        assertThat(AuthorizationKey.julianYyddd(LocalDate.of(2024, 3, 1))).isEqualTo(24061);
        assertThat(key.date9c()).isEqualTo(99999 - 24061);
        assertThat(key.time9c()).isEqualTo(999_999_999L - 134_530_250L);
        assertThat(key.value()).isEqualTo("%05d%09d".formatted(key.date9c(), key.time9c()));
        assertThat(key.value()).hasSize(14);
    }

    /** A later authorization of the same day sorts before an earlier one, which is what paging relies on. */
    @Test
    void aLaterAuthorizationHasTheLowerKey() {
        AuthorizationKey earlier = AuthorizationKey.of(LocalDateTime.of(2024, 3, 1, 9, 0, 0));
        AuthorizationKey later = AuthorizationKey.of(LocalDateTime.of(2024, 3, 1, 17, 0, 0));

        assertThat(later.value()).isLessThan(earlier.value());
    }

    /** 3100-CHECK-AUTH-EXPIRY reverses the date complement to recover the authorization date. */
    @Test
    void theJulianDateIsRecoveredFromTheComplement() {
        AuthorizationKey key = AuthorizationKey.of(LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999_000_000));

        assertThat(key.julianDate()).isEqualTo(24366);
        assertThat(key.timeOfDayMillis()).isEqualTo(235_959_999L);
    }

    @Test
    void theKeyRoundTripsThroughItsFourteenCharacterValue() {
        AuthorizationKey key = AuthorizationKey.of(LocalDateTime.of(2023, 7, 4, 6, 7, 8, 9_000_000));

        AuthorizationKey parsed = AuthorizationKey.parse(key.value());

        assertThat(parsed.date9c()).isEqualTo(key.date9c());
        assertThat(parsed.time9c()).isEqualTo(key.time9c());
        assertThat(parsed).hasToString(key.value());
    }

    @Test
    void aKeyThatIsNotFourteenDigitsIsRejected() {
        assertThatThrownBy(() -> AuthorizationKey.parse("1234"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuthorizationKey.parse("0123456789012X"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuthorizationKey.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** COPAUS2C FORMAT-AUTH-TIMESTAMP, from PA-AUTH-ORIG-DATE and the uncomplemented time. */
    @Test
    void theFraudTimestampCombinesTheOriginalDateAndTheUncomplementedTime() {
        assertThat(AuthorizationKey.fraudTimestamp("240301", 134_530_250L))
                .isEqualTo(LocalDateTime.of(2024, 3, 1, 13, 45, 30, 250_000_000));
    }

    @Test
    void aFraudTimestampNeedsASixDigitOriginalDate() {
        assertThatThrownBy(() -> AuthorizationKey.fraudTimestamp("2403", 1_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** The segment key is also convertible on its own, in the century window the source assumes. */
    @Test
    void theKeyConvertsToATimestamp() {
        AuthorizationKey key = AuthorizationKey.of(LocalDateTime.of(2024, 3, 1, 13, 45, 30));

        assertThat(key.toTimestamp(2000))
                .isEqualTo(LocalDateTime.of(2024, 3, 1, 13, 45, 30));
    }
}
