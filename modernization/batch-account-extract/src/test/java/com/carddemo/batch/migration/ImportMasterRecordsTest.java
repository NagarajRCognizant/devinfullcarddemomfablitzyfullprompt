package com.carddemo.batch.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.cobol.migration.MigrationExportData;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** The master file records CBIMPORT writes for each export record type. */
class ImportMasterRecordsTest {

    @Test
    void writesTheCustomerRecordOfCvcus01y() {
        String record = ImportMasterRecords.customerRecord(new MigrationExportData.Customer(
                100000001L, "John", "Q", "Public", "123 Main Street", "Apt 4", "", "TX", "USA",
                "75001", "(555)123-4567", "", 123456789L, "TX1234567", "1980-04-01", "1234567890",
                "Y", 745));

        assertThat(record).hasSize(ImportMasterRecords.CUSTOMER_LENGTH);
        assertThat(record).startsWith("100000001John");
        assertThat(record.substring(279, 288)).isEqualTo("123456789");
        assertThat(record.substring(328, 329)).isEqualTo("Y");
        assertThat(record.substring(329, 332)).isEqualTo("745");
    }

    /** The balances keep the trailing sign of the signed display fields. */
    @Test
    void writesTheAccountRecordOfCvact01y() {
        String record = ImportMasterRecords.accountRecord(new MigrationExportData.Account(
                11111111111L, "Y", new BigDecimal("-1234.56"), new BigDecimal("5000.00"),
                new BigDecimal("500.00"), "2020-01-01", "2030-01-01", "2025-01-01",
                new BigDecimal("25.00"), new BigDecimal("99.99"), "12345", "DEFAULT"));

        assertThat(record).hasSize(ImportMasterRecords.ACCOUNT_LENGTH);
        assertThat(record).startsWith("11111111111Y");
        assertThat(record.substring(12, 24)).isEqualTo("00000012345O");
        assertThat(record.substring(24, 36)).isEqualTo("00000050000{");
    }

    @Test
    void writesTheXrefCardAndTransactionRecords() {
        assertThat(ImportMasterRecords.xrefRecord(
                new MigrationExportData.Xref("4111111111111111", 100000001L, 11111111111L)))
                .hasSize(ImportMasterRecords.XREF_LENGTH)
                .startsWith("4111111111111111100000001" + "11111111111");
        assertThat(ImportMasterRecords.cardRecord(new MigrationExportData.Card(
                "4111111111111111", 11111111111L, 123, "JOHN Q PUBLIC", "2030-01-01", "Y")))
                .hasSize(ImportMasterRecords.CARD_LENGTH)
                .startsWith("411111111111111111111111111123JOHN Q PUBLIC");
        assertThat(ImportMasterRecords.transactionRecord(new MigrationExportData.Transaction(
                "0000000000000001", "01", 5411, "POS", "Grocery purchase",
                new BigDecimal("42.75"), 123456789L, "Acme Stores", "Dallas", "75001",
                "4111111111111111", "2026-09-01 08:00:00.000000", "2026-09-02 02:00:00.000000")))
                .hasSize(ImportMasterRecords.TRANSACTION_LENGTH)
                .startsWith("0000000000000001015411POS       ");
    }

    /** 2750-WRITE-ERROR: the pipe delimited record of an unrecognised record type. */
    @Test
    void writesThePipeDelimitedErrorRecord() {
        String record = ImportMasterRecords.errorRecord("2026092410304500", 'Z', 42L,
                ImportMasterRecords.UNKNOWN_RECORD_TYPE);

        assertThat(record).hasSize(ImportMasterRecords.ERROR_LENGTH);
        assertThat(record.trim())
                .isEqualTo("2026092410304500          |Z|0000042|Unknown record type encountered");
    }
}
