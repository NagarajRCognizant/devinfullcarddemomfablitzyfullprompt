package com.carddemo.cobol.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.cobol.CobolRecordReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** The CVEXPORT.cpy record layout CBEXPORT writes and CBIMPORT reads. */
class MigrationExportRecordTest {

    private static final Charset CHARSET = StandardCharsets.ISO_8859_1;
    private static final MigrationExportHeader HEADER =
            MigrationExportHeader.of(LocalDateTime.of(2026, 9, 24, 10, 30, 45), 7L);

    @Test
    void buildsTheHeaderCbexportMovesIntoEveryRecord() {
        MigrationExportRecord record = new MigrationExportRecord(
                MigrationExportRecord.encode(HEADER, xref(), CHARSET), CHARSET);

        assertThat(record.recordType()).isEqualTo(MigrationExportRecord.TYPE_XREF);
        assertThat(record.timestamp()).isEqualTo("2026-09-24 10:30:45.00");
        assertThat(record.sequenceNumber()).isEqualTo(7L);
        assertThat(record.branchId()).isEqualTo(MigrationExportHeader.BRANCH_ID);
        assertThat(record.regionCode()).isEqualTo(MigrationExportHeader.REGION_CODE);
    }

    @Test
    void writesFiveHundredBytesForEveryRecordType() {
        assertThat(MigrationExportRecord.encode(HEADER, customer(), CHARSET))
                .hasSize(MigrationExportRecord.RECORD_LENGTH);
        assertThat(MigrationExportRecord.encode(HEADER, account(), CHARSET))
                .hasSize(MigrationExportRecord.RECORD_LENGTH);
        assertThat(MigrationExportRecord.encode(HEADER, xref(), CHARSET))
                .hasSize(MigrationExportRecord.RECORD_LENGTH);
        assertThat(MigrationExportRecord.encode(HEADER, transaction(), CHARSET))
                .hasSize(MigrationExportRecord.RECORD_LENGTH);
        assertThat(MigrationExportRecord.encode(HEADER, card(), CHARSET))
                .hasSize(MigrationExportRecord.RECORD_LENGTH);
    }

    @Test
    void readsBackTheCustomerRecordItWrote() {
        MigrationExportRecord record = new MigrationExportRecord(
                MigrationExportRecord.encode(HEADER, customer(), CHARSET), CHARSET);

        assertThat(record.recordType()).isEqualTo(MigrationExportRecord.TYPE_CUSTOMER);
        assertThat(record.customer()).isEqualTo(customer());
    }

    @Test
    void readsBackTheAccountRecordItWrote() {
        MigrationExportRecord record = new MigrationExportRecord(
                MigrationExportRecord.encode(HEADER, account(), CHARSET), CHARSET);

        assertThat(record.recordType()).isEqualTo(MigrationExportRecord.TYPE_ACCOUNT);
        assertThat(record.account()).isEqualTo(account());
    }

    @Test
    void readsBackTheTransactionRecordItWrote() {
        MigrationExportRecord record = new MigrationExportRecord(
                MigrationExportRecord.encode(HEADER, transaction(), CHARSET), CHARSET);

        assertThat(record.recordType()).isEqualTo(MigrationExportRecord.TYPE_TRANSACTION);
        assertThat(record.transaction()).isEqualTo(transaction());
    }

    @Test
    void readsBackTheXrefAndCardRecordsItWrote() {
        assertThat(new MigrationExportRecord(
                MigrationExportRecord.encode(HEADER, xref(), CHARSET), CHARSET).xref())
                .isEqualTo(xref());
        MigrationExportRecord cardRecord = new MigrationExportRecord(
                MigrationExportRecord.encode(HEADER, card(), CHARSET), CHARSET);
        assertThat(cardRecord.recordType()).isEqualTo(MigrationExportRecord.TYPE_CARD);
        assertThat(cardRecord.card()).isEqualTo(card());
    }

    /** A negative balance keeps its sign through the COMP-3 and COMP fields of the layout. */
    @Test
    void keepsTheSignOfThePackedAndBinaryAmounts() {
        MigrationExportData.Account negative = new MigrationExportData.Account(11111111111L, "Y",
                new BigDecimal("-1234.56"), new BigDecimal("5000.00"), new BigDecimal("500.00"),
                "2020-01-01", "2030-01-01", "2025-01-01", new BigDecimal("-25.00"),
                new BigDecimal("-99.99"), "12345", "DEFAULT");

        MigrationExportRecord record = new MigrationExportRecord(
                MigrationExportRecord.encode(HEADER, negative, CHARSET), CHARSET);

        assertThat(record.account()).isEqualTo(negative);
    }

    /** The data area a record type does not use is left as the spaces of INITIALIZE. */
    @Test
    void padsTheUnusedDataAreaWithSpaces() {
        byte[] record = MigrationExportRecord.encode(HEADER, xref(), CHARSET);

        assertThat(new String(record, MigrationExportRecord.DATA_OFFSET + 33, 427, CHARSET))
                .isEqualTo(" ".repeat(427));
    }

    /** The character fields follow the configured code page, the binary fields do not. */
    @Test
    void writesCharacterFieldsInTheConfiguredCodePage() {
        Charset ebcdic = Charset.forName("IBM037");

        byte[] record = MigrationExportRecord.encode(HEADER, xref(), ebcdic);

        assertThat(new MigrationExportRecord(record, ebcdic).xref()).isEqualTo(xref());
        assertThat(new CobolRecordReader(record, ebcdic).picX(35, 5))
                .isEqualTo(MigrationExportHeader.REGION_CODE);
    }

    private static MigrationExportData.Customer customer() {
        return new MigrationExportData.Customer(100000001L, "John", "Q", "Public",
                "123 Main Street", "Apt 4", "", "TX", "USA", "75001", "(555)123-4567",
                "(555)987-6543", 123456789L, "TX1234567", "1980-04-01", "1234567890", "Y", 745);
    }

    private static MigrationExportData.Account account() {
        return new MigrationExportData.Account(11111111111L, "Y", new BigDecimal("1234.56"),
                new BigDecimal("5000.00"), new BigDecimal("500.00"), "2020-01-01", "2030-01-01",
                "2025-01-01", new BigDecimal("25.00"), new BigDecimal("99.99"), "12345",
                "DEFAULT");
    }

    private static MigrationExportData.Xref xref() {
        return new MigrationExportData.Xref("4111111111111111", 100000001L, 11111111111L);
    }

    private static MigrationExportData.Transaction transaction() {
        return new MigrationExportData.Transaction("0000000000000001", "01", 5411, "POS",
                "Grocery purchase", new BigDecimal("42.75"), 123456789L, "Acme Stores",
                "Dallas", "75001", "4111111111111111", "2026-09-01 08:00:00.000000",
                "2026-09-02 02:00:00.000000");
    }

    private static MigrationExportData.Card card() {
        return new MigrationExportData.Card("4111111111111111", 11111111111L, 123,
                "JOHN Q PUBLIC", "2030-01-01", "Y");
    }
}
