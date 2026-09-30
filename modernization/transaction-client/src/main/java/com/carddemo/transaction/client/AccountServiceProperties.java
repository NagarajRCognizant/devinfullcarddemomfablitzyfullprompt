package com.carddemo.transaction.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the account bounded context can be reached, and how long this service waits for it.
 *
 * <p>COTRN02C, COBIL00C, CBTRN02C and CBACT04C read CXACAIX, CCXREF and ACCTDAT directly; those
 * files belong to the account context now, so the same data is read over HTTP. The timeouts are
 * short so that a slow account service surfaces as the source's "Unable to lookup Account..."
 * rather than a hung screen.
 */
@ConfigurationProperties(prefix = "carddemo.transaction.account-service")
public record AccountServiceProperties(
        String baseUrl,
        int connectTimeoutMillis,
        int readTimeoutMillis) {

    public AccountServiceProperties {
        baseUrl = baseUrl == null ? "http://localhost:8080" : baseUrl;
        connectTimeoutMillis = connectTimeoutMillis <= 0 ? 2000 : connectTimeoutMillis;
        readTimeoutMillis = readTimeoutMillis <= 0 ? 5000 : readTimeoutMillis;
    }
}
