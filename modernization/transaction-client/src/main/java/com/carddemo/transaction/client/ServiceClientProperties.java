package com.carddemo.transaction.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which registered client this service asks the realm for its own token as, for the paths that
 * have no signed-on user behind them.
 */
@ConfigurationProperties(prefix = "carddemo.transaction.service-client")
public record ServiceClientProperties(String registrationId) {

    public ServiceClientProperties {
        registrationId = registrationId == null ? "carddemo-transaction-svc" : registrationId;
    }
}
