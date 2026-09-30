package com.carddemo.batch.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The DALYTRAN record layout of app/cpy/CVTRA06Y.cpy, read from the supplied sample file. */
class DailyTransactionTest {

    private static final Path SAMPLE = Path.of("..", "..", "app", "data", "ASCII", "dailytran.txt");

    @Test
    void readsEveryFieldOfTheFirstSampleRecord() throws Exception {
        String record = firstRecord();

        DailyTransaction transaction = DailyTransaction.parse(record);

        assertThat(transaction.id()).isEqualTo("0000000000683580");
        assertThat(transaction.typeCd()).isEqualTo("01");
        assertThat(transaction.catCd()).isEqualTo(1);
        assertThat(transaction.source()).isEqualTo("POS TERM");
        assertThat(transaction.description()).isEqualTo("Purchase at Abshire-Lowe");
        assertThat(transaction.amount()).isEqualByComparingTo(new BigDecimal("504.77"));
        assertThat(transaction.merchantId()).isEqualTo(800000000L);
        assertThat(transaction.merchantName()).isEqualTo("Abshire-Lowe");
        assertThat(transaction.merchantCity()).isEqualTo("North Enoshaven");
        assertThat(transaction.merchantZip()).isEqualTo("72112");
        assertThat(transaction.cardNumber()).isEqualTo("4859452612877065");
        assertThat(transaction.originalTimestamp()).isEqualTo("2022-06-10 19:27:53.000000");
        assertThat(transaction.originalDate()).isEqualTo("2022-06-10");
        assertThat(transaction.rawRecord()).hasSize(350).isEqualTo(record);
    }

    /** A negative amount carries its sign in the last digit, as the zoned decimal does on the host. */
    @Test
    void decodesTheSignOverpunchOfANegativeAmount() {
        String record = DailyTransactionFixtures.record("0000000000000001", "4111111111111111",
                "-1234.56", "2022-06-10 19:27:53.000000");

        assertThat(DailyTransaction.parse(record).amount())
                .isEqualByComparingTo(new BigDecimal("-1234.56"));
    }

    @Test
    void padsAShortRecordToTheFullLength() {
        DailyTransaction transaction = DailyTransaction.parse("0000000000683580");

        assertThat(transaction.rawRecord()).hasSize(350);
        assertThat(transaction.cardNumber()).isBlank();
    }

    private String firstRecord() throws Exception {
        List<String> lines = Files.readAllLines(SAMPLE, StandardCharsets.ISO_8859_1);
        return lines.get(0);
    }
}
