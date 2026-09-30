package com.carddemo.transaction.client;

import java.time.Duration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The remote replacement for the VSAM reads and the ACCTDAT rewrite the transaction screens
 * performed directly.
 *
 * <p>CARDXREF, ACCTDAT and CUSTDAT belong to the account context, so COTRN02C's READ-CXACAIX-FILE
 * and READ-CCXREF-FILE become keyed lookups over HTTP and COBIL00C's UPDATE-ACCTDAT-FILE becomes a
 * balance adjustment call. The adjustment carries an idempotency key because, unlike the source's
 * single unit of recovery, a retried HTTP call could otherwise pay the same bill twice.
 */
@Component
public class AccountServiceClient {

    private final RestClient restClient;
    private final ServiceAccessTokens tokens;

    public AccountServiceClient(RestClient.Builder builder, AccountServiceProperties properties,
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

    /** COTRN02C READ-CCXREF-FILE: the cross reference read on the card number. */
    public CardholderView byCardNumber(String cardNumber) {
        return get("/api/cardholders/by-card/" + cardNumber, cardNumber);
    }

    /** COTRN02C READ-CXACAIX-FILE and COBIL00C READ-ACCTDAT-FILE: the same read on the account id. */
    public CardholderView byAccountId(long accountId) {
        return get("/api/cardholders/by-account/" + accountId, Long.toString(accountId));
    }

    /** CBSTM03A 1000-XREFFILE-GET-NEXT with the customer and account reads that follow it. */
    public StatementPartyPage statementParties(int page, int size) {
        try {
            StatementPartyPage body = restClient.get()
                    .uri(uri -> uri.path("/api/statement-parties")
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.bearerToken())
                    .retrieve()
                    .body(StatementPartyPage.class);
            if (body == null) {
                throw new AccountServiceException(
                        "The account service returned no cross reference page " + page, null);
            }
            return body;
        } catch (RestClientException e) {
            throw new AccountServiceException(
                    "Cross reference read failed at page " + page + ": " + e.getMessage(), e);
        }
    }

    /** COBIL00C UPDATE-ACCTDAT-FILE, as an idempotent call on the owning service. */
    public BalanceAdjustmentResult adjustBalance(long accountId, BalanceAdjustment adjustment) {
        try {
            BalanceAdjustmentResult result = restClient.post()
                    .uri("/api/accounts/" + accountId + "/balance-adjustments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.bearerToken())
                    .body(adjustment)
                    .retrieve()
                    .body(BalanceAdjustmentResult.class);
            if (result == null) {
                throw new AccountServiceException(
                        "The account service returned no body for account " + accountId, null);
            }
            return result;
        } catch (RestClientException e) {
            throw new AccountServiceException(
                    "Balance adjustment failed for account " + accountId + ": " + e.getMessage(), e);
        }
    }

    private CardholderView get(String path, String key) {
        try {
            CardholderView view = restClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.bearerToken())
                    .retrieve()
                    .body(CardholderView.class);
            if (view == null) {
                throw new AccountServiceException(
                        "The account service returned no body for " + key, null);
            }
            return view;
        } catch (HttpClientErrorException.NotFound e) {
            return CardholderView.notFound(key);
        } catch (RestClientException e) {
            throw new AccountServiceException(
                    "Cardholder lookup failed for " + key + ": " + e.getMessage(), e);
        }
    }
}
