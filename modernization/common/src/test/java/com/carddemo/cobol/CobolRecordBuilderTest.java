package com.carddemo.cobol;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CobolRecordBuilderTest {

    private CobolRecordBuilder builder() {
        return new CobolRecordBuilder(StandardCharsets.ISO_8859_1);
    }

    @Test
    void picXIsLeftJustifiedAndSpaceFilled() {
        assertThat(new String(builder().picX("AB", 5).build(), StandardCharsets.ISO_8859_1))
                .isEqualTo("AB   ");
    }

    @Test
    void pic9IsRightJustifiedAndZeroFilled() {
        assertThat(new String(builder().pic9(42L, 11).build(), StandardCharsets.ISO_8859_1))
                .isEqualTo("00000000042");
    }

    @Test
    void zonedDecimalCarriesTheSignAsAnOverpunch() {
        assertThat(new String(builder().zoned(new BigDecimal("1025.00"), 10, 2).build(),
                StandardCharsets.ISO_8859_1)).isEqualTo("00000010250{");
        assertThat(new String(builder().zoned(new BigDecimal("-1025.05"), 10, 2).build(),
                StandardCharsets.ISO_8859_1)).isEqualTo("00000010250N");
        assertThat(new String(builder().zoned(null, 10, 2).build(), StandardCharsets.ISO_8859_1))
                .isEqualTo("00000000000{");
    }

    @Test
    void zonedDecimalRoundTripsThroughTheDecoder() {
        BigDecimal value = new BigDecimal("-98765.43");
        String field = new String(builder().zoned(value, 10, 2).build(), StandardCharsets.ISO_8859_1);

        assertThat(ZonedDecimalCodec.decode(field, 2)).isEqualByComparingTo(value);
    }

    @Test
    void packedDecimalUsesTwoDigitsPerByteAndASignNibble() {
        byte[] positive = builder().packed(new BigDecimal("2525.00"), 10, 2).build();

        assertThat(positive).hasSize(7);
        assertThat(positive[6] & 0x0F).isEqualTo(0x0C);
        assertThat(toDigits(positive)).isEqualTo("0000000252500");

        byte[] negative = builder().packed(new BigDecimal("-2500.00"), 10, 2).build();
        assertThat(negative[6] & 0x0F).isEqualTo(0x0D);
        assertThat(toDigits(negative)).isEqualTo("0000000250000");
    }

    @Test
    void movesTruncateHighOrderDigitsAndExtraDecimals() {
        assertThat(new String(builder().zoned(new BigDecimal("123456789012.99"), 10, 2).build(),
                StandardCharsets.ISO_8859_1)).isEqualTo("34567890129I");
        assertThat(new String(builder().zoned(new BigDecimal("1.999"), 10, 2).build(),
                StandardCharsets.ISO_8859_1)).isEqualTo("00000000019I");
    }

    @Test
    void rawAndTextAppendBytesUnchanged() {
        byte[] record = builder().raw(new byte[] {1, 2}).text("AB").build();

        assertThat(record).containsExactly(1, 2, 'A', 'B');
    }

    private static String toDigits(byte[] packed) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < packed.length; i++) {
            digits.append((packed[i] >> 4) & 0x0F);
            if (i < packed.length - 1) {
                digits.append(packed[i] & 0x0F);
            }
        }
        return digits.toString();
    }
}
