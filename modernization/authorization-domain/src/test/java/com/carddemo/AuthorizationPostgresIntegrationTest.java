package com.carddemo;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for the authorization tests that need a real PostgreSQL 16 instance, so the converted
 * PAUTSUM0/PAUTDTL1 hierarchy, its complemented key ordering and the pessimistic lock of
 * 8400-UPDATE-SUMMARY are exercised against the target database.
 *
 * <p>Shipped from this module as a test-jar so the authorization service and the purge batch job
 * share one definition of "a clean authorization database". Testcontainers is the playbook default
 * but cannot run here: the Docker Hub pull of the PostgreSQL image is rate limited on this machine
 * (recorded as UCR-09). The tests use the local {@code carddemo_authorization_test} database
 * created by {@code modernization/scripts/local-db.sh}, and each test context cleans and re-applies
 * every migration.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(AuthorizationPostgresIntegrationTest.CleanDatabase.class)
public abstract class AuthorizationPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Empties the authorization tables between tests. Each test commits for real, so the state one
     * test leaves behind would otherwise be read by the next one.
     */
    @BeforeEach
    void emptyAuthorizationTables() {
        jdbcTemplate.execute("""
                truncate table authorization_reply_outbox,
                               authorization_request_log,
                               auth_fraud,
                               pending_auth_detail,
                               pending_auth_summary
                restart identity cascade
                """);
    }

    /** Drops and re-creates the authorization schema before each test context starts. */
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
