package com.carddemo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for the tests that need a real PostgreSQL instance, so the migrations and the fixed
 * width CHAR columns of the converted USRSEC cluster are exercised against the target database.
 *
 * <p>Testcontainers is the usual choice but cannot be used on this machine: the Docker Hub pull of
 * the PostgreSQL image is rate limited. The tests use the local {@code carddemo_identity_test}
 * database created by {@code modernization/scripts/local-db.sh}; every test starts by dropping the
 * schema and re-applying the migrations, which reloads the ten records of the shipped USRSEC file,
 * so a test that writes or deletes a record cannot affect the next one.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void reloadTheSecurityFile() {
        flyway.clean();
        flyway.migrate();
    }
}
