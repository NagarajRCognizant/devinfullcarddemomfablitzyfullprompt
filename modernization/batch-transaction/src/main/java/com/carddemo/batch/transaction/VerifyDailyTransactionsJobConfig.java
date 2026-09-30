package com.carddemo.batch.transaction;

import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.CardholderView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Converted daily transaction verification job (program CBTRN01C).
 *
 * <p>The program posts nothing: it reads every record of DALYTRAN, resolves the card through the
 * cross reference, reads the account and DISPLAYs what it found or did not find. That operator
 * listing is the whole output, so the step writes the same findings to the application log and
 * leaves the data untouched; it is run before POSTTRAN to see what the night's file contains.
 */
@Configuration
public class VerifyDailyTransactionsJobConfig {

    public static final String JOB_NAME = "verifyDailyTransactionsJob";
    public static final String STEP_NAME = "verifyDailyTransactionsStep";

    private static final Logger log = LoggerFactory.getLogger(VerifyDailyTransactionsJobConfig.class);

    private final TransactionBatchProperties properties;

    public VerifyDailyTransactionsJobConfig(TransactionBatchProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Job verifyDailyTransactionsJob(JobRepository jobRepository, Step verifyDailyTransactionsStep) {
        return new JobBuilder(JOB_NAME, jobRepository).start(verifyDailyTransactionsStep).build();
    }

    @Bean
    public Step verifyDailyTransactionsStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<DailyTransaction> dailyTransactionReader,
            AccountServiceClient accounts) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<DailyTransaction, DailyTransaction>chunk(properties.chunkSize(), transactionManager)
                .reader(dailyTransactionReader)
                .writer(verificationLog(accounts))
                .build();
    }

    /** Paragraphs 2000-LOOKUP-XREF and 3000-READ-ACCOUNT with their DISPLAY statements. */
    private ItemWriter<DailyTransaction> verificationLog(AccountServiceClient accounts) {
        return (Chunk<? extends DailyTransaction> chunk) -> {
            for (DailyTransaction transaction : chunk) {
                CardholderView view = accounts.byCardNumber(transaction.cardNumber());
                if (!view.cardFound()) {
                    log.warn("CARD NUMBER {} COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-{}",
                            transaction.cardNumber(), transaction.id());
                } else if (!view.accountFound()) {
                    log.warn("ACCOUNT {} NOT FOUND", view.accountId());
                } else {
                    log.info("SUCCESSFUL READ OF ACCOUNT FILE: card {} account {}",
                            transaction.cardNumber(), view.accountId());
                }
            }
        };
    }
}
