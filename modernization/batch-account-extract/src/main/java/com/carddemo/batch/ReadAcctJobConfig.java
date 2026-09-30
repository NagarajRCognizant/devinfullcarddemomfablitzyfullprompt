package com.carddemo.batch;

import com.carddemo.persistence.entity.AccountEntity;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Converted READACCT job (app/jcl/READACCT.jcl running program CBACT01C).
 *
 * <p>The job step of the JCL becomes one chunk oriented Spring Batch step: the sequential read of
 * the ACCTDAT KSDS becomes a paged read of the account table ordered by account id, which is the
 * same order the key sequenced data set delivers, and the three DD statements become three writers
 * driven from the same item. The PREDEL step that deletes the previous generation of the three data
 * sets is reproduced by writers that recreate their files at the start of a fresh run, which is why
 * the job is not idempotent across restarts unless it is restarted rather than re-run - both are
 * documented in the batch documentation.
 *
 * <p>The COBOL program also DISPLAYs every record it reads; that operator log is replaced by the
 * step's structured logging and is not part of the extract.
 */
@Configuration
@EnableConfigurationProperties(AcctExtractProperties.class)
public class ReadAcctJobConfig {

    public static final String JOB_NAME = "readAcctJob";
    public static final String STEP_NAME = "readAcctStep";

    /** OUTFILE - AWS.M2.CARDDEMO.ACCTDATA.PSCOMP, LRECL 107 FB. */
    public static final String COMP_FILE = "acctdata.pscomp";
    /** ARRYFILE - AWS.M2.CARDDEMO.ACCTDATA.ARRYPS, LRECL 110 FB. */
    public static final String ARRAY_FILE = "acctdata.arryps";
    /** VBRCFILE - AWS.M2.CARDDEMO.ACCTDATA.VBPS, LRECL 84 VB. */
    public static final String VB_FILE = "acctdata.vbps";

    private final AcctExtractProperties properties;

    public ReadAcctJobConfig(AcctExtractProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Job readAcctJob(JobRepository jobRepository, Step readAcctStep) {
        return new JobBuilder(JOB_NAME, jobRepository).start(readAcctStep).build();
    }

    @Bean
    public Step readAcctStep(JobRepository jobRepository,
                             PlatformTransactionManager transactionManager,
                             JpaPagingItemReader<AccountEntity> accountFileReader,
                             ItemWriter<AccountEntity> acctExtractWriter) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<AccountEntity, AccountEntity>chunk(properties.getChunkSize(), transactionManager)
                .reader(accountFileReader)
                .writer(acctExtractWriter)
                .build();
    }

    /** 1000-ACCTFILE-GET-NEXT: sequential ascending read of the key sequenced data set. */
    @Bean
    public JpaPagingItemReader<AccountEntity> accountFileReader(EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<AccountEntity>()
                .name("accountFileReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("select a from AccountEntity a order by a.acctId asc")
                .pageSize(properties.getChunkSize())
                .saveState(true)
                .build();
    }

    @Bean
    public ItemWriter<AccountEntity> acctExtractWriter(
            FlatFileItemWriter<AccountEntity> acctCompWriter,
            FlatFileItemWriter<AccountEntity> acctArrayWriter,
            FlatFileItemWriter<AccountEntity> acctVbWriter) {
        CompositeItemWriter<AccountEntity> writer = new CompositeItemWriter<>();
        writer.setDelegates(List.of(acctCompWriter, acctArrayWriter, acctVbWriter));
        return writer;
    }

    @Bean
    public FlatFileItemWriter<AccountEntity> acctCompWriter() {
        return byteWriter("acctCompWriter", COMP_FILE,
                new AcctCompRecordAggregator(properties.charset()));
    }

    @Bean
    public FlatFileItemWriter<AccountEntity> acctArrayWriter() {
        return byteWriter("acctArrayWriter", ARRAY_FILE,
                new AcctArrayRecordAggregator(properties.charset()));
    }

    @Bean
    public FlatFileItemWriter<AccountEntity> acctVbWriter() {
        return byteWriter("acctVbWriter", VB_FILE,
                new AcctVbRecordAggregator(properties.charset()));
    }

    /**
     * The extract files are record images, not text: there is no line separator and the record
     * bytes are mapped one to one through ISO-8859-1 so that COMP-3 fields reach the file
     * unchanged, whatever character set the character fields were encoded with.
     */
    private FlatFileItemWriter<AccountEntity> byteWriter(
            String name,
            String fileName,
            org.springframework.batch.item.file.transform.LineAggregator<AccountEntity> aggregator) {
        return new FlatFileItemWriterBuilder<AccountEntity>()
                .name(name)
                .resource(new FileSystemResource(properties.getOutputDirectory() + "/" + fileName))
                .encoding(StandardCharsets.ISO_8859_1.name())
                .lineSeparator("")
                .lineAggregator(aggregator)
                .shouldDeleteIfExists(true)
                .saveState(true)
                .build();
    }
}
