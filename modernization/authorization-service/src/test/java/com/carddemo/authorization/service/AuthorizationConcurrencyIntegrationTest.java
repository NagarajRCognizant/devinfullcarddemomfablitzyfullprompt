package com.carddemo.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.carddemo.AuthorizationPostgresIntegrationTest;
import com.carddemo.authorization.client.CardholderLookupClient;
import com.carddemo.authorization.client.CardholderView;
import com.carddemo.authorization.messaging.AuthorizationRequestMessage;
import com.carddemo.persistence.entity.AuthorizationSummaryEntity;
import com.carddemo.persistence.repository.AuthorizationSummaryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * The compensating mechanism of consistency-model delta register entry CMD-14.
 *
 * <p>The source read the request queue one message at a time in a single triggered task, so no two
 * authorizations were ever decided at the same time. The target consumes a partitioned topic with
 * one thread per partition, so two requests on two different cards of the same account can arrive
 * concurrently. The pessimistic row lock that {@code findByIdForUpdate} takes on PAUTSUM0 restores
 * what DL/I held for the duration of the unit of work; this test proves the two decisions serialise
 * on it and that available credit cannot be committed twice.
 */
class AuthorizationConcurrencyIntegrationTest extends AuthorizationPostgresIntegrationTest {

    private static final String CARD = "4111111111111111";
    private static final String SECOND_CARD = "4111111111112222";
    private static final long ACCOUNT = 11L;
    private static final long CUSTOMER = 22L;

    @MockBean
    private CardholderLookupClient cardholderLookupClient;

    @Autowired
    private AuthorizationRequestProcessor processor;
    @Autowired
    private AuthorizationSummaryRepository summaryRepository;

    private static CardholderView cardholder(String cardNumber) {
        return new CardholderView(true, true, true, cardNumber, ACCOUNT, CUSTOMER, "Y",
                new BigDecimal("1000.00"), new BigDecimal("500.00"), BigDecimal.ZERO,
                "JOHN", "Q", "PUBLIC", "1 MAIN ST", null, null, "TX", "75001", "2145551212");
    }

    private static AuthorizationRequestMessage request(String cardNumber, String amount,
                                                       String time, String transactionId) {
        return AuthorizationRequestMessage.parse(String.join(",",
                "240301", time, cardNumber, "01", "1225", "0100", "POS", "000000", amount,
                "5411", "840", "05", "MERCH000000001", "ACME STORE", "DALLAS", "TX", "75001",
                transactionId));
    }

    /**
     * Two cards of one account, each asking for more than half of the remaining credit. Serialised,
     * exactly one can be approved; interleaved, both would read the same credit balance and both
     * would be approved, over-committing the account.
     */
    @Test
    void twoConcurrentRequestsOnOneAccountCannotBothConsumeTheSameCredit() throws Exception {
        given(cardholderLookupClient.byCardNumber(anyString()))
                .willAnswer(invocation -> cardholder(invocation.getArgument(0)));

        // The first authorization creates PAUTSUM0 and leaves 900.00 of available credit, so both
        // concurrent requests below find a row to lock.
        processor.process(request(CARD, "100.00", "134530", "TRAN0000000001"), "CORR0", "reply.topic");

        CyclicBarrier bothInFlight = new CyclicBarrier(2);
        Callable<AuthorizationOutcome> first = () -> {
            bothInFlight.await(10, TimeUnit.SECONDS);
            return processor.process(request(CARD, "600.00", "134531", "TRAN0000000002"),
                    "CORR1", "reply.topic");
        };
        Callable<AuthorizationOutcome> second = () -> {
            bothInFlight.await(10, TimeUnit.SECONDS);
            return processor.process(request(SECOND_CARD, "600.00", "134532", "TRAN0000000003"),
                    "CORR2", "reply.topic");
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<AuthorizationOutcome>> outcomes;
        try {
            outcomes = executor.invokeAll(List.of(first, second));
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        List<String> responseCodes = List.of(
                outcomes.get(0).get().reply().authRespCode(),
                outcomes.get(1).get().reply().authRespCode());
        assertThat(responseCodes).containsExactlyInAnyOrder("00", "05");

        AuthorizationSummaryEntity summary = summaryRepository.findById(ACCOUNT).orElseThrow();
        // 100.00 + 600.00: only one of the two 600.00 requests consumed credit.
        assertThat(summary.getCreditBalance()).isEqualByComparingTo("700.00");
        assertThat(summary.getApprovedAuthCnt()).isEqualTo((short) 2);
        assertThat(summary.getDeclinedAuthCnt()).isEqualTo((short) 1);
    }
}
