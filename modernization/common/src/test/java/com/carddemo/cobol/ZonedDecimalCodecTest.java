package com.carddemo.cobol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ZonedDecimalCodecTest {

    @ParameterizedTest
    @CsvSource({
            "00000010250{,1025.00",
            "00000010250I,1025.09",
            "00000010250},-1025.00",
            "00000010250R,-1025.09",
            "000000102500,1025.00"})
    void overpunchedSignsAreDecoded(String field, String expected) {
        assertThat(ZonedDecimalCodec.decode(field, 2)).isEqualByComparingTo(expected);
    }

    @Test
    void blankFieldsDecodeToZero() {
        assertThat(ZonedDecimalCodec.decode("    ", 2)).isEqualByComparingTo("0.00");
        assertThat(ZonedDecimalCodec.decode(null, 2)).isEqualByComparingTo("0.00");
    }

    @Test
    void anUnknownSignCharacterIsRejected() {
        assertThatThrownBy(() -> ZonedDecimalCodec.decode("0000000000!", 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
