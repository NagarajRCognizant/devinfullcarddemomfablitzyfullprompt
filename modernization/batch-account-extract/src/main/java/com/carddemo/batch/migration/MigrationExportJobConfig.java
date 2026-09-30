package com.carddemo.batch.migration;

import com.carddemo.persistence.entity.AccountEntity;
import com.carddemo.persistence.entity.CardEntity;
import com.carddemo.persistence.entity.CardXrefEntity;
import com.carddemo.persistence.entity.CustomerEntity;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Converted CBEXPORT job (app/jcl/CBEXPORT.jcl), the branch migration export.
 *
 * <p>The source program reads the customer, account, cross reference, transaction and card data
 * sets in turn and writes one 500 byte record per input record into a single export file. The
 * customer, account, cross reference and card data sets are owned by the account context, so this
 * job exports those four record types from the account database in the same order; the transaction
 * record type is exported by the transaction context, which owns the transaction data, and the two
 * export files are read together by the import job. The split and its effect on the record order
 * and on the sequence numbering are recorded in the consistency model delta register.
 *
 * <p>The export file is a record image rather than text, so it is written without a line separator
 * and mapped through ISO-8859-1 to keep the COMP and COMP-3 bytes intact. As in the source program
 * the file is recreated by a fresh run: a re-run replaces the previous export rather than adding
 * to it.
 */
@Configuration
@EnableConfigurationProperties(MigrationProperties.class)
public class MigrationExportJobConfig {

    public static final String JOB_NAME = "exportBranchMigrationJob";
    public static final String STEP_NAME = "exportBranchMigrationStep";

    private final MigrationProperties properties;

    public MigrationExportJobConfig(MigrationProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Job exportBranchMigrationJob(JobRepository jobRepository,
                                        Step exportBranchMigrationStep) {
        return new JobBuilder(JOB_NAME, jobRepository).start(exportBranchMigrationStep).build();
    }

    @Bean
    public Step exportBranchMigrationStep(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          EntityManagerFactory entityManagerFactory,
                                          MigrationExportAggregator migrationExportAggregator) {
        SequentialItemStreamReader reader = new SequentialItemStreamReader(List.of(
                jpaReader(entityManagerFactory, "exportCustomerReader",
                        "select c from CustomerEntity c order by c.custId asc"),
                jpaReader(entityManagerFactory, "exportAccountReader",
                        "select a from AccountEntity a order by a.acctId asc"),
                jpaReader(entityManagerFactory, "exportXrefReader",
                        "select x from CardXrefEntity x order by x.xrefCardNum asc"),
                jpaReader(entityManagerFactory, "exportCardReader",
                        "select c from CardEntity c order by c.cardNum asc")));
        return new StepBuilder(STEP_NAME, jobRepository)
                .<Object, Object>chunk(properties.getChunkSize(), transactionManager)
                .reader(reader)
                .writer(migrationExportWriter(migrationExportAggregator))
                .stream(reader)
                .listener(migrationExportAggregator)
                .build();
    }

    /**
     * 1100-OPEN-FILES and the five read paragraphs: each data set is read in ascending key order,
     * which is the order the key sequenced data set delivered its records.
     */
    private <T> ItemStreamReader<T> jpaReader(EntityManagerFactory entityManagerFactory,
                                              String name, String query) {
        return new JpaPagingItemReaderBuilder<T>()
                .name(name)
                .entityManagerFactory(entityManagerFactory)
                .queryString(query)
                .pageSize(properties.getChunkSize())
                .saveState(false)
                .build();
    }

    @Bean
    public MigrationExportAggregator migrationExportAggregator() {
        return new MigrationExportAggregator(properties.charset());
    }

    /** EXPFILE, LRECL 500 FB. */
    @Bean
    public FlatFileItemWriter<Object> migrationExportWriter(
            MigrationExportAggregator migrationExportAggregator) {
        return new FlatFileItemWriterBuilder<>()
                .name("migrationExportWriter")
                .resource(new FileSystemResource(properties.path(properties.getExportFile())))
                .encoding(StandardCharsets.ISO_8859_1.name())
                .lineSeparator("")
                .lineAggregator(migrationExportAggregator)
                .shouldDeleteIfExists(true)
                .saveState(false)
                .build();
    }
}
