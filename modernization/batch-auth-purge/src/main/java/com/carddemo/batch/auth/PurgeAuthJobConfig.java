package com.carddemo.batch.auth;

import com.carddemo.persistence.entity.AuthorizationSummaryEntity;
import com.carddemo.persistence.repository.AuthorizationDetailRepository;
import com.carddemo.persistence.repository.AuthorizationSummaryRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Converted CBPAUP0J / CBPAUP0C: the daily purge of expired pending authorizations.
 *
 * <p>The BMP's single MAIN-PARA loop becomes one chunk oriented step. The IMS
 * {@code GN SEGMENT(PAUTSUM0)} scan becomes a keyset read of the summary table in account id
 * order, which is the root key order HIDAM delivers; the child walk and the expiry test live in the
 * processor; the deletes and the counter reversal live in the writer. The chunk size is the source's
 * checkpoint frequency, so a chunk commit is the IMS CHKP: the job is restartable from the last
 * committed chunk instead of from the last checkpoint id, and re-running it is idempotent because
 * an already purged detail is no longer there to be found.
 *
 * <p>The BMP's RETURN-CODE 16 abend path is the step failing: a failed delete or read rolls the
 * chunk back and the job ends FAILED with a non-zero exit status, which is what the scheduler
 * reacted to on the mainframe.
 */
@Configuration
@EnableConfigurationProperties(AuthPurgeProperties.class)
public class PurgeAuthJobConfig {

    public static final String JOB_NAME = "purgeExpiredAuthorizationsJob";
    public static final String STEP_NAME = "purgeExpiredAuthorizationsStep";

    private static final Logger log = LoggerFactory.getLogger(PurgeAuthJobConfig.class);

    private final AuthPurgeProperties properties;

    public PurgeAuthJobConfig(AuthPurgeProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Job purgeExpiredAuthorizationsJob(JobRepository jobRepository,
                                             Step purgeExpiredAuthorizationsStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(purgeExpiredAuthorizationsStep)
                .listener(purgeReportListener())
                .build();
    }

    @Bean
    public Step purgeExpiredAuthorizationsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            AuthSummaryKeysetReader authSummaryReader,
            ExpiredAuthorizationProcessor expiredAuthorizationProcessor,
            AuthPurgeWriter authPurgeWriter) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<AuthorizationSummaryEntity, SummaryPurgePlan>chunk(
                        properties.getCheckpointFrequency(), transactionManager)
                .reader(authSummaryReader)
                .processor(expiredAuthorizationProcessor)
                .writer(authPurgeWriter)
                .listener(authPurgeWriter)
                .build();
    }

    /** 2000-FIND-NEXT-AUTH-SUMMARY: the GN scan of the root segment, in root key order. */
    @Bean
    public AuthSummaryKeysetReader authSummaryReader(
            AuthorizationSummaryRepository summaryRepository) {
        return new AuthSummaryKeysetReader(summaryRepository, properties.getCheckpointFrequency());
    }

    @Bean
    public ExpiredAuthorizationProcessor expiredAuthorizationProcessor(
            AuthorizationDetailRepository detailRepository, Clock clock) {
        return new ExpiredAuthorizationProcessor(detailRepository, properties, clock);
    }

    @Bean
    public AuthPurgeWriter authPurgeWriter(AuthorizationSummaryRepository summaryRepository,
                                           AuthorizationDetailRepository detailRepository) {
        return new AuthPurgeWriter(summaryRepository, detailRepository, properties);
    }

    /** The DISPLAY block that closes MAIN-PARA, as structured operator logging. */
    private JobExecutionListener purgeReportListener() {
        return new JobExecutionListener() {
            @Override
            public void afterJob(JobExecution jobExecution) {
                for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
                    if (STEP_NAME.equals(stepExecution.getStepName())) {
                        AuthPurgeWriter.report(stepExecution).forEach(log::info);
                    }
                }
            }
        };
    }
}
