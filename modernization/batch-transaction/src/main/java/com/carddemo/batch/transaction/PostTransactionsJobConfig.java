package com.carddemo.batch.transaction;

import java.nio.charset.Charset;
import java.nio.file.Path;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Converted POSTTRAN job (app/jcl/POSTTRAN.jcl running program CBTRN02C).
 *
 * <p>The sequential DALYTRAN read becomes a flat file reader over the same fixed length records,
 * the four indexed files become the transaction schema and the account service, and the DALYREJS
 * output becomes the {@code daily_transaction_reject} table so a rejected record keeps its
 * original 350 bytes and its reason trailer and can be queried rather than re-read from tape.
 *
 * <p>The source returns 4 when any record was rejected; the step reports that as the
 * {@code COMPLETED WITH REJECTS} exit status, which the scheduler treats the way it treated RC=4.
 */
@Configuration
public class PostTransactionsJobConfig {

    public static final String JOB_NAME = "postTransactionsJob";
    public static final String STEP_NAME = "postTransactionsStep";
    /** The exit status that stands for the source's RETURN-CODE 4. */
    public static final String REJECTS_EXIT_CODE = "COMPLETED WITH REJECTS";
    /** Step context key holding WS-REJECT-COUNT. */
    public static final String REJECT_COUNT = "rejectCount";

    private final TransactionBatchProperties properties;

    public PostTransactionsJobConfig(TransactionBatchProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Job postTransactionsJob(JobRepository jobRepository, Step postTransactionsStep) {
        return new JobBuilder(JOB_NAME, jobRepository).start(postTransactionsStep).build();
    }

    @Bean
    public Step postTransactionsStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<DailyTransaction> dailyTransactionReader,
            DailyTransactionValidator dailyTransactionValidator,
            PostedTransactionWriter postedTransactionWriter) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<DailyTransaction, PostingCandidate>chunk(properties.chunkSize(), transactionManager)
                .reader(dailyTransactionReader)
                .processor(dailyTransactionValidator)
                .writer(postedTransactionWriter)
                .listener((StepExecutionListener) postedTransactionWriter)
                .listener(rejectExitStatus())
                .build();
    }

    /** The DALYTRAN DD: fixed length records in the single byte encoding of the supplied files. */
    @Bean
    public FlatFileItemReader<DailyTransaction> dailyTransactionReader() {
        Path input = Path.of(properties.inputDirectory(), properties.dailyTransactionFile());
        return new FlatFileItemReaderBuilder<DailyTransaction>()
                .name("dailyTransactionReader")
                .resource(new FileSystemResource(input))
                .encoding(Charset.forName(properties.recordCharset()).name())
                .lineMapper((line, lineNumber) -> DailyTransaction.parse(line))
                .build();
    }

    private StepExecutionListener rejectExitStatus() {
        return new StepExecutionListener() {
            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                long rejected = stepExecution.getExecutionContext().getLong(REJECT_COUNT, 0L);
                return rejected == 0
                        ? stepExecution.getExitStatus()
                        : new ExitStatus(REJECTS_EXIT_CODE,
                                rejected + " of " + stepExecution.getReadCount()
                                        + " daily transactions were rejected");
            }
        };
    }
}
