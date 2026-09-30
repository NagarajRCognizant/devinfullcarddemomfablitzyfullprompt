package com.carddemo;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for the tests that need a real PostgreSQL 16 instance, so that the Flyway migrations,
 * the fixed width CHAR columns and the pessimistic locks are exercised against the target database
 * rather than an in memory substitute.
 *
 * <p>Testcontainers is the playbook default but cannot be used on this machine: the bundled
 * docker-java client negotiates Docker API 1.32 while the local Docker Engine 29 refuses anything
 * below 1.40. The tests therefore use the local {@code carddemo_test} database created by
 * {@code modernization/scripts/local-db.sh}, and each test context starts by cleaning and
 * re-applying every migration, which gives each test class the clean database Testcontainers would.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresIntegrationTest.CleanDatabase.class)
public abstract class PostgresIntegrationTest {

    /** Re-runs the ACCTFILE equivalent (drop, create, load) before each test context starts. */
    @TestConfiguration
    public static class CleanDatabase {

        @Bean
        FlywayMigrationStrategy cleanMigrate() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }
}
