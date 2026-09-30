package com.carddemo.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.carddemo.AuthorizationPostgresIntegrationTest;
import com.carddemo.authorization.client.CardholderLookupClient;
import com.carddemo.authorization.client.CardholderView;
import com.carddemo.authorization.messaging.AuthorizationRequestMessage;
import com.carddemo.persistence.entity.AuthorizationDetailEntity;
import com.carddemo.persistence.entity.AuthorizationReplyOutboxEntity;
import com.carddemo.persistence.entity.AuthorizationSummaryEntity;
import com.carddemo.persistence.repository.AuthorizationDetailRepository;
import com.carddemo.persistence.repository.AuthorizationReplyOutboxRepository;
import com.carddemo.persistence.repository.AuthorizationRequestLogRepository;
import com.carddemo.persistence.repository.AuthorizationSummaryRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * COPAUA0C end to end against PostgreSQL: the decision, the summary update of 8400, the detail
 * insert of 8500, the idempotency record and the outbox row, all in one unit of work.
 *
 * <p>The account service is mocked because it is a different bounded context; what is exercised
 * here is everything the authorization program did with its own IMS data once the cross reference,
 * account and customer reads had returned.
 */
class AuthorizationRequestProcessorIntegrationTest extends AuthorizationPostgresIntegrationTest {

    private static final String CARD = "4111111111111111";
    private static final long ACCOUNT = 11L;
    private static final long CUSTOMER = 22L;

    @MockBean
    private CardholderLookupClient cardholderLookupClient;

    @Autowired
    private AuthorizationRequestProcessor processor;
    @Autowired
    private AuthorizationSummaryRepository summaryRepository;
    @Autowired
    private AuthorizationDetailRepository detailRepository;
    @Autowired
    private AuthorizationRequestLogRepository requestLogRepository;
    @Autowired
    private AuthorizationReplyOutboxRepository outboxRepository;

    private static CardholderView cardholder(String creditLimit, String currentBalance) {
        return new CardholderView(true, true, true, CARD, ACCOUNT, CUSTOMER, "Y",
                new BigDecimal(creditLimit), new BigDecimal("500.00"), new BigDecimal(currentBalance),
                "JOHN", "Q", "PUBLIC", "1 MAIN ST", null, null, "TX", "75001", "2145551212");
    }

    private static AuthorizationRequestMessage request(String amount, String transactionId) {
        return AuthorizationRequestMessage.parse(String.join(",",
                "240301", "134530", CARD, "01", "1225", "0100", "POS", "000000", amount,
                "5411", "840", "05", "MERCH000000001", "ACME STORE", "DALLAS", "TX", "75001",
                transactionId));
    }

    /** The first authorization of an account creates the PAUTSUM0 segment and one child. */
    @Test
    void anApprovedAuthorizationCreatesTheSummaryAndTheDetail() {
        given(cardholderLookupClient.byCardNumber(anyString()))
                .willReturn(cardholder("5000.00", "1000.00"));

        AuthorizationOutcome outcome =
                processor.process(request("250.75", "TRAN0000000001"), "CORR1", "reply.topic");

        assertThat(outcome.replayed()).isFalse();
        assertThat(outcome.reply().authRespCode()).isEqualTo("00");
        assertThat(outcome.reply().authRespReason()).isEqualTo("0000");
        assertThat(outcome.reply().approvedAmt()).isEqualByComparingTo("250.75");
        // MOVE PA-RQ-AUTH-TIME TO PA-RL-AUTH-ID-CODE
        assertThat(outcome.reply().authIdCode()).isEqualTo("134530");

        AuthorizationSummaryEntity summary = summaryRepository.findById(ACCOUNT).orElseThrow();
        assertThat(summary.getCustId()).isEqualTo(CUSTOMER);
        assertThat(summary.getApprovedAuthCnt()).isEqualTo((short) 1);
        assertThat(summary.getApprovedAuthAmt()).isEqualByComparingTo("250.75");
        assertThat(summary.getCreditBalance()).isEqualByComparingTo("250.75");
        // MOVE 0 TO PA-CASH-BALANCE on every approval, carried forward unchanged.
        assertThat(summary.getCashBalance()).isEqualByComparingTo("0.00");
        assertThat(summary.getCreditLimit()).isEqualByComparingTo("5000.00");
        assertThat(summary.getCashLimit()).isEqualByComparingTo("500.00");

        List<AuthorizationDetailEntity> details =
                detailRepository.findChildren(ACCOUNT, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(details).hasSize(1);
        AuthorizationDetailEntity detail = details.get(0);
        assertThat(detail.getCardNum().trim()).isEqualTo(CARD);
        assertThat(detail.getMatchStatus()).isEqualTo("P");
        assertThat(detail.getApprovedAmt()).isEqualByComparingTo("250.75");
        assertThat(detail.getTransactionAmt()).isEqualByComparingTo("250.75");
        assertThat(detail.getAuthOrigDate()).isEqualTo("240301");
        assertThat(detail.getId().getAuthKey()).hasSize(14);
        assertThat(detail.getAuthFraud()).isEqualTo(" ");
    }

    /** 5500-READ-AUTH-SUMMRY: the credit position comes from the segment once it exists. */
    @Test
    void anAuthorizationOverTheSummaryCreditIsDeclinedForInsufficientFunds() {
        given(cardholderLookupClient.byCardNumber(anyString()))
                .willReturn(cardholder("1000.00", "0.00"));
        processor.process(request("900.00", "TRAN0000000001"), "CORR1", "reply.topic");

        AuthorizationOutcome outcome =
                processor.process(request("200.00", "TRAN0000000002"), "CORR2", "reply.topic");

        assertThat(outcome.reply().authRespCode()).isEqualTo("05");
        assertThat(outcome.reply().authRespReason()).isEqualTo("4100");
        assertThat(outcome.reply().approvedAmt()).isEqualByComparingTo("0.00");

        AuthorizationSummaryEntity summary = summaryRepository.findById(ACCOUNT).orElseThrow();
        assertThat(summary.getApprovedAuthCnt()).isEqualTo((short) 1);
        assertThat(summary.getDeclinedAuthCnt()).isEqualTo((short) 1);
        // ADD PA-RQ-TRANSACTION-AMT TO PA-DECLINED-AUTH-AMT
        assertThat(summary.getDeclinedAuthAmt()).isEqualByComparingTo("200.00");
        assertThat(summary.getCreditBalance()).isEqualByComparingTo("900.00");
        assertThat(detailRepository.countByIdAcctId(ACCOUNT)).isEqualTo(2);
    }

    /**
     * {@code IF CARD-FOUND-XREF PERFORM 8000-WRITE-AUTH-TO-DB}: an unknown card is declined with
     * 3100 and leaves nothing in the authorization database.
     */
    @Test
    void anUnknownCardIsDeclinedAndWritesNoAuthorization() {
        given(cardholderLookupClient.byCardNumber(anyString()))
                .willReturn(CardholderView.notFound(CARD));

        AuthorizationOutcome outcome =
                processor.process(request("10.00", "TRAN0000000003"), "CORR3", "reply.topic");

        assertThat(outcome.reply().authRespCode()).isEqualTo("05");
        assertThat(outcome.reply().authRespReason()).isEqualTo("3100");
        assertThat(summaryRepository.count()).isZero();
        assertThat(detailRepository.count()).isZero();
        assertThat(requestLogRepository.count()).isEqualTo(1);
    }

    /**
     * The compensation for the at-least-once delivery Kafka gives and the MQ get inside the
     * syncpoint did not: the second delivery replays the stored reply and authorizes nothing.
     */
    @Test
    void aDuplicateDeliveryReplaysTheStoredReplyWithoutAuthorizingTwice() {
        given(cardholderLookupClient.byCardNumber(anyString()))
                .willReturn(cardholder("5000.00", "0.00"));
        AuthorizationOutcome first =
                processor.process(request("100.00", "TRAN0000000004"), "CORR4", "reply.topic");

        AuthorizationOutcome second =
                processor.process(request("100.00", "TRAN0000000004"), "CORR4", "reply.topic");

        assertThat(second.replayed()).isTrue();
        assertThat(second.reply().toBuffer()).isEqualTo(first.reply().toBuffer());
        AuthorizationSummaryEntity summary = summaryRepository.findById(ACCOUNT).orElseThrow();
        assertThat(summary.getApprovedAuthCnt()).isEqualTo((short) 1);
        assertThat(summary.getCreditBalance()).isEqualByComparingTo("100.00");
        assertThat(detailRepository.countByIdAcctId(ACCOUNT)).isEqualTo(1);
    }

    /** The reply is committed with the authorization, keyed by card number and carrying MQMD-CORRELID. */
    @Test
    void theReplyIsWrittenToTheOutboxInTheSameTransaction() {
        given(cardholderLookupClient.byCardNumber(anyString()))
                .willReturn(cardholder("5000.00", "0.00"));

        processor.process(request("75.00", "TRAN0000000005"), "CORR5", "acquirer.reply.topic");

        List<AuthorizationReplyOutboxEntity> pending = outboxRepository.findAll();
        assertThat(pending).hasSize(1);
        AuthorizationReplyOutboxEntity reply = pending.get(0);
        assertThat(reply.getReplyTopic()).isEqualTo("acquirer.reply.topic");
        assertThat(reply.getMessageKey()).isEqualTo(CARD);
        assertThat(reply.getCorrelationId()).isEqualTo("CORR5");
        assertThat(reply.getPublishedAt()).isNull();
        assertThat(reply.getPayload()).startsWith(CARD);
    }
}
