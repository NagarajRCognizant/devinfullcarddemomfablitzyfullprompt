package com.carddemo.authorization;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The authorization bounded context: CICS transactions CP00, CPVS and CPVD plus their IMS and DB2
 * data, owning the {@code carddemo_authorization} database.
 *
 * <p>The persistence classes live in the shared {@code authorization-domain} module so the purge
 * batch job can reuse them, which is why the entity and repository packages are declared
 * explicitly. Scheduling is enabled for the outbox relay that publishes authorization replies.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EntityScan("com.carddemo.persistence.entity")
@EnableJpaRepositories("com.carddemo.persistence.repository")
public class AuthorizationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthorizationServiceApplication.class, args);
    }

    /**
     * The single source of time for the service, standing in for CICS ASKTIME. Injecting it keeps
     * the complemented authorization key deterministic in the parity tests.
     */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
