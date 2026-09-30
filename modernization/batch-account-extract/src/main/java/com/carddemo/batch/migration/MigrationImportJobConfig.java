package com.carddemo.batch.migration;

import com.carddemo.cobol.migration.MigrationExportRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.LineAggregator;
import org.springframework.batch.item.support.ClassifierCompositeItemWriter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Converted CBIMPORT job (app/jcl/CBIMPORT.jcl), the branch migration import.
 *
 * <p>The source program reads the export file, dispatches each record on {@code EXPORT-REC-TYPE}
 * and writes the matching master file record; a record type it does not recognise is counted and
 * written to the error file instead, and the run continues. That is reproduced by a classifying
 * writer whose delegates are the six output files, so the dispatch and the "unknown type is not
 * fatal" rule of {@code 2200-PROCESS-RECORD-BY-TYPE} survive unchanged.
 *
 * <p>The program produces files, not database rows, so the whole job is file to file and does not
 * touch either context's database: it prepares the master files a receiving branch loads.
 */
@Configuration
@EnableConfigurationProperties(MigrationProperties.class)
public class MigrationImportJobConfig {

    public static final String JOB_NAME = "importBranchMigrationJob";
    public static final String STEP_NAME = "importBranchMigrationStep";

    private static final Logger LOG = LoggerFactory.getLogger("CBIMPORT");

    /** FUNCTION CURRENT-DATE as moved into ERR-TIMESTAMP. */
    private static final DateTimeFormatter ERROR_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSS");

    private final MigrationProperties properties;

    public MigrationImportJobConfig(MigrationProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Job importBranchMigrationJob(JobRepository jobRepository,
                                        Step importBranchMigrationStep) {
        return new JobBuilder(JOB_NAME, jobRepository).start(importBranchMigrationStep).build();
    }

    @Bean
    public Step importBranchMigrationStep(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager) {
        FixedLengthRecordReader reader = new FixedLengthRecordReader(importFiles(),
                MigrationExportRecord.RECORD_LENGTH);
        FlatFileItemWriter<MigrationExportRecord> customers = writer("importCustomerWriter",
                properties.getCustomerFile(),
                record -> ImportMasterRecords.customerRecord(record.customer()));
        FlatFileItemWriter<MigrationExportRecord> accounts = writer("importAccountWriter",
                properties.getAccountFile(),
                record -> ImportMasterRecords.accountRecord(record.account()));
        FlatFileItemWriter<MigrationExportRecord> xrefs = writer("importXrefWriter",
                properties.getXrefFile(),
                record -> ImportMasterRecords.xrefRecord(record.xref()));
        FlatFileItemWriter<MigrationExportRecord> transactions = writer("importTransactionWriter",
                properties.getTransactionFile(),
                record -> ImportMasterRecords.transactionRecord(record.transaction()));
        FlatFileItemWriter<MigrationExportRecord> cards = writer("importCardWriter",
                properties.getCardFile(),
                record -> ImportMasterRecords.cardRecord(record.card()));
        FlatFileItemWriter<MigrationExportRecord> errors = writer("importErrorWriter",
                properties.getErrorFile(), MigrationImportJobConfig::errorRecord);

        Map<Character, ItemWriter<? super MigrationExportRecord>> byType = Map.of(
                MigrationExportRecord.TYPE_CUSTOMER, customers,
                MigrationExportRecord.TYPE_ACCOUNT, accounts,
                MigrationExportRecord.TYPE_XREF, xrefs,
                MigrationExportRecord.TYPE_TRANSACTION, transactions,
                MigrationExportRecord.TYPE_CARD, cards);
        ClassifierCompositeItemWriter<MigrationExportRecord> composite =
                new ClassifierCompositeItemWriter<>();
        composite.setClassifier(record -> byType.getOrDefault(record.recordType(), errors));
        MigrationImportCountListener counts = new MigrationImportCountListener(LOG);

        return new StepBuilder(STEP_NAME, jobRepository)
                .<byte[], MigrationExportRecord>chunk(properties.getChunkSize(), transactionManager)
                .reader(reader)
                .processor(bytes -> new MigrationExportRecord(bytes, properties.charset()))
                .writer(composite)
                .stream(reader)
                .stream(customers)
                .stream(accounts)
                .stream(xrefs)
                .stream(transactions)
                .stream(cards)
                .stream(errors)
                .listener((ItemWriteListener<MigrationExportRecord>) counts)
                .listener((StepExecutionListener) counts)
                .build();
    }

    /** EXPFILE, read in the order the export files were produced. */
    private List<Path> importFiles() {
        return Arrays.stream(properties.getImportFiles().split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> Path.of(properties.path(name)))
                .toList();
    }

    /** 2700-PROCESS-UNKNOWN-RECORD followed by 2750-WRITE-ERROR. */
    private static String errorRecord(MigrationExportRecord record) {
        return ImportMasterRecords.errorRecord(ERROR_TIMESTAMP.format(LocalDateTime.now()),
                record.recordType(), record.sequenceNumber(),
                ImportMasterRecords.UNKNOWN_RECORD_TYPE);
    }

    /**
     * The master files are record images with no line separator, mapped through ISO-8859-1 so the
     * bytes the aggregator produced reach the file unchanged.
     */
    private FlatFileItemWriter<MigrationExportRecord> writer(
            String name, String fileName, LineAggregator<MigrationExportRecord> aggregator) {
        return new FlatFileItemWriterBuilder<MigrationExportRecord>()
                .name(name)
                .resource(new FileSystemResource(properties.path(fileName)))
                .encoding(StandardCharsets.ISO_8859_1.name())
                .lineSeparator("")
                .lineAggregator(aggregator)
                .shouldDeleteIfExists(true)
                .saveState(false)
                .build();
    }
}
