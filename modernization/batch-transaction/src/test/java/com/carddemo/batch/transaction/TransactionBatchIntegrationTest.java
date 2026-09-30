package com.carddemo.batch.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.carddemo.cobol.migration.MigrationExportHeader;
import com.carddemo.cobol.migration.MigrationExportRecord;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.StatementParty;
import com.carddemo.transaction.client.StatementPartyPage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The converted CBSTM03A statement job and the transaction half of the converted CBEXPORT job,
 * end to end against the seeded transaction database.
 *
 * <p>The cross reference, customer and account reads of the source program belong to the account
 * context, so the client that replaced them is stubbed here and the transactions come from the
 * database, which is the split the two jobs run with in production.
 */
@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@Import(TransactionBatchIntegrationTest.CleanDatabase.class)
class TransactionBatchIntegrationTest {

    private static final String CARD_NUMBER = "8112545834239735";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    private Job accountStatementJob;
    @Autowired
    private Job exportBranchMigrationTransactionsJob;
    @Autowired
    private TransactionBatchProperties properties;
    @Autowired
    private TransactionRepository transactions;
    @MockBean
    private AccountServiceClient accounts;

    private JobExecution lastExecution;

    /** One type 'T' record of 500 bytes per transaction, numbered from one. */
    @Test
    void theTransactionExportWritesOneRecordPerTransaction() throws Exception {
        run(exportBranchMigrationTransactionsJob);

        assertThat(lastExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        byte[] bytes = Files.readAllBytes(file(properties.migrationExportFile()));
        long count = transactions.count();
        assertThat(bytes).hasSize((int) count * MigrationExportRecord.RECORD_LENGTH);

        MigrationExportRecord first = record(bytes, 0);
        assertThat(first.recordType()).isEqualTo(MigrationExportRecord.TYPE_TRANSACTION);
        assertThat(first.sequenceNumber()).isEqualTo(1L);
        assertThat(first.branchId()).isEqualTo(MigrationExportHeader.BRANCH_ID);
        assertThat(first.regionCode()).isEqualTo(MigrationExportHeader.REGION_CODE);
        assertThat(first.transaction().tranId())
                .isEqualTo(transactions.findAll().stream()
                        .map(transaction -> transaction.getTranId())
                        .sorted()
                        .findFirst()
                        .orElseThrow());
    }

    /** A re-run numbers its records from one again, as a fresh run of the program did. */
    @Test
    void aSecondTransactionExportStartsTheSequenceAgain() throws Exception {
        run(exportBranchMigrationTransactionsJob);
        run(exportBranchMigrationTransactionsJob);

        byte[] bytes = Files.readAllBytes(file(properties.migrationExportFile()));
        assertThat(bytes).hasSize((int) transactions.count()
                * MigrationExportRecord.RECORD_LENGTH);
        assertThat(record(bytes, 0).sequenceNumber()).isEqualTo(1L);
    }

    /** CBSTM03A writes the 80 column statement and the 100 column HTML statement of each party. */
    @Test
    void theStatementJobWritesBothStatementFiles() throws Exception {
        when(accounts.statementParties(anyInt(), anyInt()))
                .thenReturn(new StatementPartyPage(List.of(party()), true));

        run(accountStatementJob);

        assertThat(lastExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        String statement = Files.readString(file(properties.statementFile()),
                StandardCharsets.ISO_8859_1);
        assertThat(statement).contains("START OF STATEMENT", "John Q Doe", "Basic Details",
                "END OF STATEMENT");
        assertThat(statement.lines().filter(line -> line.startsWith("Total EXP")))
                .hasSize(1);
        assertThat(statement)
                .contains(transactions.findByTranCardNumOrderByTranIdAsc(CARD_NUMBER).get(0)
                        .getTranId());

        String html = Files.readString(file(properties.statementHtmlFile()),
                StandardCharsets.ISO_8859_1);
        assertThat(html).contains("<html lang=\"en\">", "Statement for Account Number",
                "John Q Doe");
    }

    /**
     * 2000-CUSTFILE-GET and 3000-ACCTFILE-GET abend when the record is missing, so the step fails
     * rather than printing a statement without the customer.
     */
    @Test
    void aMissingCustomerRecordFailsTheStatementRun() throws Exception {
        StatementParty missing = new StatementParty(CARD_NUMBER, 1L, 1L, true, false,
                BigDecimal.ZERO, 700, "John", "Q", "Doe", "1 Main St", "", "", "NY", "USA",
                "10001");
        when(accounts.statementParties(anyInt(), anyInt()))
                .thenReturn(new StatementPartyPage(List.of(missing), true));

        run(accountStatementJob);

        assertThat(lastExecution.getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    private static StatementParty party() {
        return new StatementParty(CARD_NUMBER, 1L, 1L, true, true, new BigDecimal("1000.00"),
                700, "John", "Q", "Doe", "1 Main St", "", "", "NY", "USA", "10001");
    }

    private MigrationExportRecord record(byte[] bytes, int index) {
        byte[] record = new byte[MigrationExportRecord.RECORD_LENGTH];
        System.arraycopy(bytes, index * MigrationExportRecord.RECORD_LENGTH, record, 0,
                record.length);
        return new MigrationExportRecord(record, properties.charset());
    }

    private void run(Job job) throws Exception {
        jobLauncherTestUtils.setJob(job);
        lastExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLong("run.id", System.nanoTime())
                .toJobParameters());
    }

    private Path file(String name) {
        return Path.of(properties.outputDirectory(), name);
    }

    /** Re-applies the transaction migrations, which reload the sample data, per test context. */
    @TestConfiguration
    static class CleanDatabase {

        @Bean
        FlywayMigrationStrategy cleanMigrate() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }
}
