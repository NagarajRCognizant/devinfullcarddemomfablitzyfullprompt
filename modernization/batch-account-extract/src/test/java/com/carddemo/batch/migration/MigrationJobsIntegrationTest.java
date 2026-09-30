package com.carddemo.batch.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.PostgresIntegrationTest;
import com.carddemo.cobol.migration.MigrationExportHeader;
import com.carddemo.cobol.migration.MigrationExportRecord;
import com.carddemo.persistence.repository.AccountRepository;
import com.carddemo.persistence.repository.CardRepository;
import com.carddemo.persistence.repository.CardXrefRepository;
import com.carddemo.persistence.repository.CustomerRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;

/** The converted CBEXPORT and CBIMPORT jobs end to end against the seeded database. */
@SpringBatchTest
class MigrationJobsIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    private Job exportBranchMigrationJob;
    @Autowired
    private Job importBranchMigrationJob;
    @Autowired
    private MigrationProperties properties;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private CardXrefRepository xrefs;
    @Autowired
    private CardRepository cards;

    private JobExecution lastExecution;

    @Test
    void theExportWritesOneFiveHundredByteRecordPerSourceRecord() throws Exception {
        run(exportBranchMigrationJob);

        assertThat(lastExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        long expected = customers.count() + accounts.count() + xrefs.count() + cards.count();
        assertThat(Files.size(file(properties.getExportFile())))
                .isEqualTo(expected * MigrationExportRecord.RECORD_LENGTH);
    }

    /**
     * The customers are exported first, then the accounts, the cross references and the cards, and
     * a single sequence counter runs across all of them, as in CBEXPORT.
     */
    @Test
    void theExportKeepsTheRecordOrderAndTheSharedHeaderOfCbexport() throws Exception {
        run(exportBranchMigrationJob);

        List<MigrationExportRecord> records = exported();
        String types = records.stream()
                .map(record -> String.valueOf(record.recordType()))
                .distinct()
                .reduce("", String::concat);
        assertThat(types).isEqualTo("CAXD");
        assertThat(records.get(0).sequenceNumber()).isEqualTo(1L);
        assertThat(records.get(records.size() - 1).sequenceNumber()).isEqualTo(records.size());
        assertThat(records).allSatisfy(record -> {
            assertThat(record.branchId()).isEqualTo(MigrationExportHeader.BRANCH_ID);
            assertThat(record.regionCode()).isEqualTo(MigrationExportHeader.REGION_CODE);
        });
    }

    /** Each record type reaches its own master file, which is what CBIMPORT dispatches on. */
    @Test
    void theImportSplitsTheExportIntoTheMasterFiles() throws Exception {
        run(exportBranchMigrationJob);

        run(importBranchMigrationJob);
        assertThat(lastExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        assertThat(Files.size(file(properties.getCustomerFile())))
                .isEqualTo(customers.count() * ImportMasterRecords.CUSTOMER_LENGTH);
        assertThat(Files.size(file(properties.getAccountFile())))
                .isEqualTo(accounts.count() * ImportMasterRecords.ACCOUNT_LENGTH);
        assertThat(Files.size(file(properties.getXrefFile())))
                .isEqualTo(xrefs.count() * ImportMasterRecords.XREF_LENGTH);
        assertThat(Files.size(file(properties.getCardFile())))
                .isEqualTo(cards.count() * ImportMasterRecords.CARD_LENGTH);
        assertThat(Files.size(file(properties.getErrorFile()))).isZero();
    }

    /** The imported account record carries the key and the balance of the exported account. */
    @Test
    void theImportedAccountRecordMatchesTheExportedAccount() throws Exception {
        run(exportBranchMigrationJob);
        run(importBranchMigrationJob);

        String first = new String(Files.readAllBytes(file(properties.getAccountFile())), 0,
                ImportMasterRecords.ACCOUNT_LENGTH, StandardCharsets.ISO_8859_1);
        var account = exported().stream()
                .filter(record -> record.recordType() == MigrationExportRecord.TYPE_ACCOUNT)
                .findFirst()
                .orElseThrow()
                .account();

        assertThat(first).isEqualTo(ImportMasterRecords.accountRecord(account));
    }

    /**
     * 2700-PROCESS-UNKNOWN-RECORD: an unrecognised record type is written to the error file and
     * the run carries on with the records that follow it.
     */
    @Test
    void anUnknownRecordTypeIsWrittenToTheErrorFileAndDoesNotStopTheRun() throws Exception {
        run(exportBranchMigrationJob);
        byte[] export = Files.readAllBytes(file(properties.getExportFile()));
        export[0] = 'Z';
        Files.write(file(properties.getExportFile()), export);

        run(importBranchMigrationJob);

        assertThat(lastExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(Files.size(file(properties.getCustomerFile())))
                .isEqualTo((customers.count() - 1) * ImportMasterRecords.CUSTOMER_LENGTH);
        String error = Files.readString(file(properties.getErrorFile()),
                StandardCharsets.ISO_8859_1);
        assertThat(error).hasSize(ImportMasterRecords.ERROR_LENGTH);
        assertThat(error).contains(ImportMasterRecords.UNKNOWN_RECORD_TYPE);
        assertThat(error.charAt(27)).isEqualTo('Z');
    }

    private List<MigrationExportRecord> exported() throws IOException {
        byte[] bytes = Files.readAllBytes(file(properties.getExportFile()));
        List<MigrationExportRecord> records = new ArrayList<>();
        for (int offset = 0; offset < bytes.length;
                offset += MigrationExportRecord.RECORD_LENGTH) {
            byte[] record = new byte[MigrationExportRecord.RECORD_LENGTH];
            System.arraycopy(bytes, offset, record, 0, record.length);
            records.add(new MigrationExportRecord(record, properties.charset()));
        }
        return records;
    }

    /**
     * Runs one converted job. The execution is kept in a field rather than returned because
     * Spring Batch's job scope listener treats a test method returning a JobExecution as the
     * factory for the scoped execution.
     */
    private void run(Job job) throws Exception {
        jobLauncherTestUtils.setJob(job);
        lastExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLong("run.id", System.nanoTime())
                .toJobParameters());
    }

    private Path file(String name) {
        return Path.of(properties.path(name));
    }
}
