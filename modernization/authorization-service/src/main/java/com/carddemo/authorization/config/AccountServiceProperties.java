package com.carddemo.authorization.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the account bounded context can be reached, and how long this service waits for it.
 *
 * <p>Externalised rather than compiled in because the address differs per environment; the
 * timeouts are deliberately short so that a slow account service surfaces as a retry on the Kafka
 * listener instead of holding an authorization open.
 */
@ConfigurationProperties(prefix = "carddemo.authorization.account-service")
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
