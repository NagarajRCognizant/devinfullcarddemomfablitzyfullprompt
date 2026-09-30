package com.carddemo.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.TestFixtures;
import com.carddemo.cobol.ZonedDecimalCodec;
import com.carddemo.persistence.entity.AccountEntity;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The three output layouts of CBACT01C, byte for byte. */
class AcctRecordAggregatorTest {

    private static final int ZONED_BYTES = 12;
    private static final int PACKED_BYTES = 7;

    @Test
    void theCompRecordIsOneHundredAndSevenBytesInLayoutOrder() {
        AccountEntity account = TestFixtures.account();

        byte[] record = bytes(new AcctCompRecordAggregator(StandardCharsets.ISO_8859_1)
                .aggregate(account));

        assertThat(record).hasSize(AcctCompRecordAggregator.RECORD_LENGTH);
        assertThat(text(record, 0, 11)).isEqualTo("11111111111");
        assertThat(text(record, 11, 1)).isEqualTo("Y");
        assertThat(zoned(record, 12)).isEqualByComparingTo("1025.50");
        assertThat(zoned(record, 24)).isEqualByComparingTo("5000.00");
        assertThat(zoned(record, 36)).isEqualByComparingTo("1000.00");
        assertThat(text(record, 48, 10)).isEqualTo("2015-03-01");
        assertThat(text(record, 58, 10)).isEqualTo("2026-03-01");
        assertThat(zoned(record, 78)).isEqualByComparingTo("120.00");
        assertThat(text(record, 97, 10)).isEqualTo("GRP0000001");
    }

    /** The COBDATFT call converts YYYY-MM-DD back to YYYYMMDD, left aligned in a ten byte field. */
    @Test
    void theReissueDateIsWrittenAsTheEightDigitFormatTheAssemblerReturns() {
        byte[] record = bytes(new AcctCompRecordAggregator(StandardCharsets.ISO_8859_1)
                .aggregate(TestFixtures.account()));

        assertThat(text(record, 68, 10)).isEqualTo("20220301  ");
    }

    @Test
    void aZeroCurrentCycleDebitIsReplacedByTheHardCodedDefault() {
        AccountEntity account = TestFixtures.account();
        account.setCurrCycDebit(BigDecimal.ZERO.setScale(2));

        byte[] record = bytes(new AcctCompRecordAggregator(StandardCharsets.ISO_8859_1)
                .aggregate(account));

        assertThat(packedDigits(record, 90))
                .isEqualTo("0000000252500")
                .hasSize(13);
        assertThat(record[90 + PACKED_BYTES - 1] & 0x0F).isEqualTo(0x0C);
    }

    /** The source never re-initialises the record area, so a non zero debit leaves it untouched. */
    @Test
    void aNonZeroCurrentCycleDebitLeavesWhateverTheRecordAreaHeld() {
        AcctCompRecordAggregator aggregator =
                new AcctCompRecordAggregator(StandardCharsets.ISO_8859_1);
        AccountEntity first = TestFixtures.account();
        first.setCurrCycDebit(BigDecimal.ZERO.setScale(2));
        AccountEntity second = TestFixtures.account();
        second.setCurrCycDebit(new BigDecimal("99.99"));

        byte[] uninitialised = bytes(aggregator.aggregate(second));
        assertThat(text(uninitialised, 90, PACKED_BYTES)).isEqualTo(" ".repeat(PACKED_BYTES));

        aggregator.aggregate(first);
        byte[] carriedOver = bytes(aggregator.aggregate(second));
        assertThat(packedDigits(carriedOver, 90)).isEqualTo("0000000252500");
    }

    @Test
    void theArrayRecordIsOneHundredAndTenBytesWithOnlyThreeOccurrencesPopulated() {
        byte[] record = bytes(new AcctArrayRecordAggregator(StandardCharsets.ISO_8859_1)
                .aggregate(TestFixtures.account()));

        assertThat(record).hasSize(AcctArrayRecordAggregator.RECORD_LENGTH);
        assertThat(text(record, 0, 11)).isEqualTo("11111111111");
        assertThat(zoned(record, 11)).isEqualByComparingTo("1025.50");
        assertThat(packedDigits(record, 23)).isEqualTo("0000000100500");
        assertThat(zoned(record, 30)).isEqualByComparingTo("1025.50");
        assertThat(packedDigits(record, 42)).isEqualTo("0000000152500");
        assertThat(zoned(record, 49)).isEqualByComparingTo("-1025.00");
        assertThat(packedDigits(record, 61)).isEqualTo("0000000250000");
        assertThat(record[61 + PACKED_BYTES - 1] & 0x0F).isEqualTo(0x0D);
        assertThat(zoned(record, 68)).isEqualByComparingTo("0.00");
        assertThat(zoned(record, 87)).isEqualByComparingTo("0.00");
        assertThat(text(record, 106, 4).strip()).isEmpty();
    }

    @Test
    void everyAccountProducesATwelveByteAndAThirtyNineByteVariableRecord() {
        byte[] written = bytes(new AcctVbRecordAggregator(StandardCharsets.ISO_8859_1)
                .aggregate(TestFixtures.account()));

        assertThat(written).hasSize(4 + AcctVbRecordAggregator.VB1_LENGTH + 4
                + AcctVbRecordAggregator.VB2_LENGTH);
        assertThat(descriptor(written, 0)).isEqualTo(AcctVbRecordAggregator.VB1_LENGTH + 4);
        assertThat(text(written, 4, 11)).isEqualTo("11111111111");
        assertThat(text(written, 15, 1)).isEqualTo("Y");

        int second = 4 + AcctVbRecordAggregator.VB1_LENGTH;
        assertThat(descriptor(written, second)).isEqualTo(AcctVbRecordAggregator.VB2_LENGTH + 4);
        assertThat(text(written, second + 4, 11)).isEqualTo("11111111111");
        assertThat(zoned(written, second + 15)).isEqualByComparingTo("1025.50");
        assertThat(zoned(written, second + 27)).isEqualByComparingTo("5000.00");
        assertThat(text(written, second + 39, 4)).isEqualTo("2022");
    }

    private static byte[] bytes(String line) {
        return line.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String text(byte[] record, int offset, int length) {
        return new String(record, offset, length, StandardCharsets.ISO_8859_1);
    }

    private static BigDecimal zoned(byte[] record, int offset) {
        return ZonedDecimalCodec.decode(text(record, offset, ZONED_BYTES), 2);
    }

    private static String packedDigits(byte[] record, int offset) {
        StringBuilder digits = new StringBuilder();
        for (int index = offset; index < offset + PACKED_BYTES; index++) {
            digits.append((record[index] >> 4) & 0x0F);
            if (index < offset + PACKED_BYTES - 1) {
                digits.append(record[index] & 0x0F);
            }
        }
        return digits.toString();
    }

    private static int descriptor(byte[] record, int offset) {
        return ((record[offset] & 0xFF) << 8) | (record[offset + 1] & 0xFF);
    }
}
