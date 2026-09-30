package com.carddemo.batch.transaction;

import com.carddemo.persistence.entity.TransactionCategoryBalanceEntity;
import com.carddemo.persistence.repository.DisclosureGroupRepository;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.client.AccountServiceClient;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Converted INTCALC job (app/jcl/INTCALC.jcl running program CBACT04C).
 *
 * <p>The sequential read of TCATBAL becomes a paged read in the same key order, the account and
 * cross reference reads become calls on the account service and the account rewrite becomes an
 * idempotent interest settlement on that service. The run date the JCL passes as the PARM is a
 * job parameter, since it is part of the identifier of every transaction the run writes.
 */
@Configuration
public class InterestCalculationJobConfig {

    public static final String JOB_NAME = "interestCalculationJob";
    public static final String STEP_NAME = "interestCalculationStep";
    /** The PARM of the JCL: the ten character run date that prefixes each interest transaction. */
    public static final String PARM_DATE = "parmDate";

    private final TransactionBatchProperties properties;

    public InterestCalculationJobConfig(TransactionBatchProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Job interestCalculationJob(JobRepository jobRepository, Step interestCalculationStep) {
        return new JobBuilder(JOB_NAME, jobRepository).start(interestCalculationStep).build();
    }

    @Bean
    public Step interestCalculationStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaPagingItemReader<TransactionCategoryBalanceEntity> categoryBalanceReader,
            InterestAccrualWriter interestAccrualWriter) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<TransactionCategoryBalanceEntity, TransactionCategoryBalanceEntity>chunk(
                        properties.chunkSize(), transactionManager)
                .reader(categoryBalanceReader)
                .writer(interestAccrualWriter)
                .listener(interestAccrualWriter)
                .build();
    }

    /**
     * The TCATBAL browse, in the key order the interest run depends on: the rows of one account
     * must arrive together for the totals to be settled at the key break.
     */
    @Bean
    public JpaPagingItemReader<TransactionCategoryBalanceEntity> categoryBalanceReader(
            EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<TransactionCategoryBalanceEntity>()
                .name("categoryBalanceReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("select b from TransactionCategoryBalanceEntity b"
                        + " order by b.id.trancatAcctId asc, b.id.trancatTypeCd asc, b.id.trancatCd asc")
                .pageSize(properties.chunkSize())
                .build();
    }

    @Bean
    @StepScope
    public InterestAccrualWriter interestAccrualWriter(TransactionRepository transactions,
            DisclosureGroupRepository disclosureGroups,
            AccountServiceClient accounts,
            Clock clock,
            @Value("#{jobParameters['" + PARM_DATE + "']}") String parmDate) {
        return new InterestAccrualWriter(transactions, disclosureGroups, accounts, clock, parmDate);
    }
}
