package com.carddemo.batch.transaction;

import com.carddemo.cobol.migration.MigrationExportData;
import com.carddemo.cobol.migration.MigrationExportHeader;
import com.carddemo.cobol.migration.MigrationExportRecord;
import com.carddemo.persistence.entity.TransactionEntity;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.transform.LineAggregator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The transaction record type of the converted CBEXPORT job.
 *
 * <p>{@code 5000-EXPORT-TRANSACTIONS} reads the TRANSACT data set in ascending transaction id
 * order and writes one type 'T' export record per transaction. The transaction context owns that
 * data, so the step runs here and produces its own export file; the import job reads it together
 * with the export file of the account context.
 */
@Configuration
public class MigrationExportTransactionsJobConfig {

    public static final String JOB_NAME = "exportBranchMigrationTransactionsJob";
    public static final String STEP_NAME = "exportBranchMigrationTransactionsStep";

    private final TransactionBatchProperties properties;

    public MigrationExportTransactionsJobConfig(TransactionBatchProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Job exportBranchMigrationTransactionsJob(JobRepository jobRepository,
                                                    Step exportBranchMigrationTransactionsStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(exportBranchMigrationTransactionsStep)
                .build();
    }

    @Bean
    public Step exportBranchMigrationTransactionsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaPagingItemReader<TransactionEntity> migrationTransactionReader,
            FlatFileItemWriter<TransactionEntity> migrationTransactionExportWriter,
            TransactionExportAggregator transactionExportAggregator) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<TransactionEntity, TransactionEntity>chunk(properties.chunkSize(),
                        transactionManager)
                .reader(migrationTransactionReader)
                .writer(migrationTransactionExportWriter)
                .listener(transactionExportAggregator)
                .build();
    }

    /** 5100-READ-TRANSACTION-RECORD: sequential read of the TRANSACT cluster. */
    @Bean
    public JpaPagingItemReader<TransactionEntity> migrationTransactionReader(
            EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<TransactionEntity>()
                .name("migrationTransactionReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("select t from TransactionEntity t order by t.tranId asc")
                .pageSize(properties.chunkSize())
                .saveState(false)
                .build();
    }

    @Bean
    public TransactionExportAggregator transactionExportAggregator() {
        return new TransactionExportAggregator(properties.charset());
    }

    @Bean
    public FlatFileItemWriter<TransactionEntity> migrationTransactionExportWriter(
            TransactionExportAggregator transactionExportAggregator) {
        return new FlatFileItemWriterBuilder<TransactionEntity>()
                .name("migrationTransactionExportWriter")
                .resource(new FileSystemResource(properties.outputDirectory() + "/"
                        + properties.migrationExportFile()))
                .encoding(StandardCharsets.ISO_8859_1.name())
                .lineSeparator("")
                .lineAggregator(transactionExportAggregator)
                .shouldDeleteIfExists(true)
                .saveState(false)
                .build();
    }

    /** 5200-CREATE-TRAN-EXP-REC. */
    static class TransactionExportAggregator
            implements LineAggregator<TransactionEntity>, StepExecutionListener {

        private final Charset charset;
        private LocalDateTime runTime;
        private long sequenceCounter;

        TransactionExportAggregator(Charset charset) {
            this.charset = charset;
        }

        /** 1050-GENERATE-TIMESTAMP: each run stamps its own timestamp and numbers from one. */
        @Override
        public void beforeStep(StepExecution stepExecution) {
            runTime = LocalDateTime.now();
            sequenceCounter = 0;
        }

        @Override
        public String aggregate(TransactionEntity transaction) {
            MigrationExportHeader header = MigrationExportHeader.of(runTime, ++sequenceCounter);
            MigrationExportData.Transaction data = new MigrationExportData.Transaction(
                    transaction.getTranId(),
                    transaction.getTranTypeCd(),
                    transaction.getTranCatCd(),
                    transaction.getTranSource(),
                    transaction.getTranDesc(),
                    transaction.getTranAmt(),
                    transaction.getTranMerchantId(),
                    transaction.getTranMerchantName(),
                    transaction.getTranMerchantCity(),
                    transaction.getTranMerchantZip(),
                    transaction.getTranCardNum(),
                    transaction.getTranOrigTs(),
                    transaction.getTranProcTs());
            return new String(MigrationExportRecord.encode(header, data, charset),
                    StandardCharsets.ISO_8859_1);
        }
    }
}
