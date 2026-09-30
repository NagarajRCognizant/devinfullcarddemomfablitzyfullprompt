package com.carddemo.transaction;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The transaction bounded context: CICS transactions CT00, CT01, CT02, CB00 and CR00, owning the
 * {@code carddemo_transaction} database.
 *
 * <p>The persistence classes live in the shared {@code transaction-domain} module so the posting,
 * interest and reporting batch jobs reuse them, which is why the entity and repository packages
 * are declared explicitly.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EntityScan("com.carddemo.persistence.entity")
@EnableJpaRepositories("com.carddemo.persistence.repository")
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }

    /**
     * The single source of time for the service, standing in for CICS ASKTIME, which COBIL00C and
     * CORPT00C use to stamp the transaction and to derive the report date range.
     */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
