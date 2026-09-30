package com.carddemo.authorization.client;

import com.carddemo.authorization.config.AccountServiceProperties;
import java.time.Duration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The remote replacement for the VSAM reads the authorization programs performed directly.
 *
 * <p>The account bounded context owns CARDXREF, ACCTDAT and CUSTDAT, so this context reads them
 * over HTTP instead of sharing tables. Because a remote read can fail where a VSAM read could not,
 * the call has explicit connect and read timeouts and the failure is raised as
 * {@link CardholderLookupException}: the Kafka listener turns it into a bounded retry and the
 * screens into the source's system-error message, rather than deciding an authorization on absent
 * data.
 */
@Component
public class CardholderLookupClient {

    private final RestClient restClient;
    private final ServiceAccessTokens tokens;

    public CardholderLookupClient(RestClient.Builder builder, AccountServiceProperties properties,
                                  ServiceAccessTokens tokens) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()))
                .withReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
        this.tokens = tokens;
    }

    /** COPAUA0C 9600-READ-CARDXREF and the account and customer reads that follow it. */
    public CardholderView byCardNumber(String cardNumber) {
        return get("/api/cardholders/by-card/" + cardNumber, cardNumber);
    }

    /** COPAUS0C GATHER-ACCOUNT-DETAILS, which reads the same files by account id. */
    public CardholderView byAccountId(long accountId) {
        return get("/api/cardholders/by-account/" + accountId, Long.toString(accountId));
    }

    private CardholderView get(String path, String key) {
        try {
            CardholderView view = restClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.bearerToken())
                    .retrieve()
                    .body(CardholderView.class);
            return view == null ? CardholderView.notFound(key) : view;
        } catch (RestClientException e) {
            throw new CardholderLookupException(
                    "Cardholder lookup failed for " + key + ": " + e.getMessage(), e);
        }
    }
}
