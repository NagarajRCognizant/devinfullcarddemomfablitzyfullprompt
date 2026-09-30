package com.carddemo.authorization.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.carddemo.authorization.config.AccountServiceProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

/**
 * The remote read that replaces the CARDXREF, ACCTDAT and CUSTDAT reads. A VSAM read either found
 * a record or returned a status; an HTTP call can also return no body at all or fail outright, and
 * those two new outcomes must not be mistaken by the decision logic for a record it may act on.
 */
@ExtendWith(MockitoExtension.class)
class CardholderLookupClientTest {

    private static final String BODY = "{\"cardFound\":true,\"accountFound\":true,"
            + "\"customerFound\":true,\"cardNumber\":\"4111111111111111\",\"accountId\":77,"
            + "\"customerId\":555,\"accountActiveStatus\":\"Y\",\"creditLimit\":5000.00,"
            + "\"cashCreditLimit\":500.00,\"currentBalance\":100.00,\"firstName\":\"ANN\","
            + "\"middleName\":\"B\",\"lastName\":\"LEE\",\"addressLine1\":\"1 MAIN ST\","
            + "\"addressLine2\":\"APT 2\",\"addressLine3\":\"SEATTLE\",\"stateCode\":\"WA\","
            + "\"zip\":\"98101\",\"phone1\":\"2065550100\"}";

    @Mock
    private ServiceAccessTokens tokens;

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final List<String> authorizationHeaders = new ArrayList<>();
    private String body = BODY;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/cardholders", this::respond);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void aFoundCardholderIsReadWithTheBearerTokenOfTheCaller() {
        when(tokens.bearerToken()).thenReturn("token-1");

        CardholderView view = client().byCardNumber("4111111111111111");

        assertThat(view.cardFound()).isTrue();
        assertThat(view.accountId()).isEqualTo(77L);
        assertThat(view.creditLimit()).isEqualByComparingTo("5000.00");
        assertThat(authorizationHeaders).containsExactly("Bearer token-1");
    }

    @Test
    void anEmptyResponseBodyIsTreatedAsNoRecordRatherThanAsANullCardholder() {
        when(tokens.bearerToken()).thenReturn("token-1");
        body = "";
        status.set(204);

        CardholderView view = client().byAccountId(77L);

        assertThat(view.cardFound()).isFalse();
        assertThat(view.accountFound()).isFalse();
        assertThat(view.customerFound()).isFalse();
        assertThat(view.cardNumber()).isEqualTo("77");
    }

    @Test
    void aFailedLookupIsRaisedRatherThanDecidedOnAbsentData() {
        when(tokens.bearerToken()).thenReturn("token-1");
        status.set(500);
        body = "boom";
        CardholderLookupClient client = client();

        assertThatThrownBy(() -> client.byCardNumber("4111111111111111"))
                .isInstanceOf(CardholderLookupException.class)
                .hasMessageContaining("4111111111111111");
    }

    private CardholderLookupClient client() {
        return new CardholderLookupClient(RestClient.builder(),
                new AccountServiceProperties("http://127.0.0.1:" + server.getAddress().getPort(),
                        1000, 2000),
                tokens);
    }

    private void respond(HttpExchange exchange) throws IOException {
        authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (payload.length == 0) {
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(status.get(), payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }
}
