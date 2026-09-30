package com.carddemo.batch.transaction;

import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.persistence.repository.TransactionCategoryRepository;
import com.carddemo.persistence.repository.TransactionTypeRepository;
import com.carddemo.transaction.client.AccountServiceClient;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Converted TRANREPT job (program CBTRN03C).
 *
 * <p>The DATEPARM DD, a one record file holding the start and end dates, becomes two job
 * parameters, and the selection the program performs after reading every record becomes the
 * predicate of the query: only transactions whose processing date falls inside the range are
 * read. The TRANREPT DD stays a 133 character print file so the output can be compared with a
 * listing from the source system.
 */
@Configuration
public class TransactionReportJobConfig {

    public static final String JOB_NAME = "transactionReportJob";
    public static final String STEP_NAME = "transactionReportStep";
    /** WS-START-DATE of the DATEPARM record. */
    public static final String START_DATE = "startDate";
    /** WS-END-DATE of the DATEPARM record. */
    public static final String END_DATE = "endDate";

    private final TransactionBatchProperties properties;

    public TransactionReportJobConfig(TransactionBatchProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Job transactionReportJob(JobRepository jobRepository, Step transactionReportStep) {
        return new JobBuilder(JOB_NAME, jobRepository).start(transactionReportStep).build();
    }

    @Bean
    public Step transactionReportStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaPagingItemReader<TransactionEntity> reportedTransactionReader,
            TransactionReportWriter transactionReportWriter,
            FlatFileItemWriter<String> reportFileWriter) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<TransactionEntity, TransactionEntity>chunk(properties.chunkSize(), transactionManager)
                .reader(reportedTransactionReader)
                .writer(transactionReportWriter)
                .stream(reportFileWriter)
                .listener(transactionReportWriter)
                .build();
    }

    /** The sequential TRANSACT read, narrowed to the reported date range and kept in key order. */
    @Bean
    @StepScope
    public JpaPagingItemReader<TransactionEntity> reportedTransactionReader(
            EntityManagerFactory entityManagerFactory,
            @Value("#{jobParameters['" + START_DATE + "']}") String startDate,
            @Value("#{jobParameters['" + END_DATE + "']}") String endDate) {
        return new JpaPagingItemReaderBuilder<TransactionEntity>()
                .name("reportedTransactionReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("select t from TransactionEntity t"
                        + " where substring(t.tranProcTs, 1, 10) between :startDate and :endDate"
                        + " order by t.tranId asc")
                .parameterValues(Map.of("startDate", startDate, "endDate", endDate))
                .pageSize(properties.chunkSize())
                .build();
    }

    @Bean
    @StepScope
    public TransactionReportWriter transactionReportWriter(FlatFileItemWriter<String> reportFileWriter,
            TransactionTypeRepository types,
            TransactionCategoryRepository categories,
            AccountServiceClient accounts,
            @Value("#{jobParameters['" + START_DATE + "']}") String startDate,
            @Value("#{jobParameters['" + END_DATE + "']}") String endDate) {
        return new TransactionReportWriter(reportFileWriter, types, categories, accounts, startDate,
                endDate);
    }

    /** The TRANREPT DD: fixed length print lines, one per WRITE of paragraph 1111. */
    @Bean
    public FlatFileItemWriter<String> reportFileWriter() {
        Path output = Path.of(properties.outputDirectory(), properties.reportFile());
        return new FlatFileItemWriterBuilder<String>()
                .name("reportFileWriter")
                .resource(new FileSystemResource(output))
                .encoding(Charset.forName(properties.recordCharset()).name())
                .lineAggregator(line -> line)
                .build();
    }
}
