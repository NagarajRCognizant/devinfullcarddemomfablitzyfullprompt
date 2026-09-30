package com.carddemo.batch.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The batch side of the transaction bounded context: the daily transaction verification and
 * posting jobs (CBTRN01C, CBTRN02C), the daily transaction report (CBTRN03C) and the interest
 * calculation (CBACT04C).
 *
 * <p>One application carries all four jobs, as the JCL carried them as steps of the same nightly
 * stream; each is submitted by name the way a job was submitted, with
 * {@code --spring.batch.job.enabled=true --spring.batch.job.name=<job>}.
 *
 * <p>The account reads and the account rewrite of the posting and interest jobs go through the
 * shared transaction client, whose package is scanned here alongside the jobs.
 */
@SpringBootApplication(scanBasePackages = {
        "com.carddemo.batch.transaction",
        "com.carddemo.transaction.client",
        "com.carddemo.config"})
@ConfigurationPropertiesScan({"com.carddemo.batch.transaction", "com.carddemo.transaction.client"})
@EntityScan("com.carddemo.persistence.entity")
@EnableJpaRepositories("com.carddemo.persistence.repository")
public class TransactionBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionBatchApplication.class, args);
    }
}
