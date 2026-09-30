package com.carddemo.transaction;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for the transaction context tests that need a real PostgreSQL instance, so the
 * migrations, the fixed width CHAR(16) key and the keyset browse that replaces STARTBR/READNEXT
 * are exercised against the target database rather than an in memory substitute.
 *
 * <p>The database is the local {@code carddemo_transaction_test} database created by
 * {@code modernization/scripts/local-db.sh}; each context cleans it and re-applies every
 * migration, which is the IDCAMS DELETE/DEFINE/REPRO the cluster definition jobs performed.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TransactionPostgresIntegrationTest.CleanDatabase.class)
public abstract class TransactionPostgresIntegrationTest {

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
