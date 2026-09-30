package com.carddemo.authorization.service;

import com.carddemo.authorization.client.CardholderLookupClient;
import com.carddemo.authorization.client.CardholderView;
import com.carddemo.authorization.domain.AuthorizationDecision;
import com.carddemo.authorization.domain.AuthorizationKey;
import com.carddemo.authorization.domain.AuthorizationDecisionEngine;
import com.carddemo.authorization.domain.AuthorizationReadState;
import com.carddemo.authorization.domain.FraudStatus;
import com.carddemo.authorization.messaging.AuthorizationReplyMessage;
import com.carddemo.authorization.messaging.AuthorizationRequestMessage;
import com.carddemo.persistence.entity.AuthorizationDetailEntity;
import com.carddemo.persistence.entity.AuthorizationDetailId;
import com.carddemo.persistence.entity.AuthorizationReplyOutboxEntity;
import com.carddemo.persistence.entity.AuthorizationRequestLogEntity;
import com.carddemo.persistence.entity.AuthorizationSummaryEntity;
import com.carddemo.persistence.repository.AuthorizationDetailRepository;
import com.carddemo.persistence.repository.AuthorizationReplyOutboxRepository;
import com.carddemo.persistence.repository.AuthorizationRequestLogRepository;
import com.carddemo.persistence.repository.AuthorizationSummaryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * COPAUA0C paragraphs 5000-PROCESS-AUTH, 8400-UPDATE-SUMMARY and 8500-INSERT-AUTH.
 *
 * <p>The source ran one CICS unit of work per message: the IMS updates and the MQ reply were
 * coordinated by the region's two-phase commit and a SYNCPOINT per message. Here the unit of work
 * is one local database transaction that writes the summary, the detail, the idempotency record and
 * the reply row of a transactional outbox together; the reply is published from the outbox after
 * the commit. That keeps "decided and stored" and "replied" from diverging in either direction,
 * which is the compensating mechanism recorded in the consistency-model delta register for the lost
 * IMS/DB2/MQ two-phase commit.
 */
@Service
public class AuthorizationRequestProcessor {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationRequestProcessor.class);

    private final CardholderLookupClient cardholderLookupClient;
    private final AuthorizationSummaryRepository summaryRepository;
    private final AuthorizationDetailRepository detailRepository;
    private final AuthorizationRequestLogRepository requestLogRepository;
    private final AuthorizationReplyOutboxRepository outboxRepository;
    private final Clock clock;

    public AuthorizationRequestProcessor(CardholderLookupClient cardholderLookupClient,
                                         AuthorizationSummaryRepository summaryRepository,
                                         AuthorizationDetailRepository detailRepository,
                                         AuthorizationRequestLogRepository requestLogRepository,
                                         AuthorizationReplyOutboxRepository outboxRepository,
                                         Clock clock) {
        this.cardholderLookupClient = cardholderLookupClient;
        this.summaryRepository = summaryRepository;
        this.detailRepository = detailRepository;
        this.requestLogRepository = requestLogRepository;
        this.outboxRepository = outboxRepository;
        this.clock = clock;
    }

    /**
     * Processes one authorization request and stores the reply for publication.
     *
     * @param request       the parsed CCPAURQY message
     * @param correlationId MQMD-CORRELID of the request, carried through to the reply
     * @param replyTopic    the reply destination, the equivalent of MQMD-REPLYTOQ
     */
    @Transactional
    public AuthorizationOutcome process(AuthorizationRequestMessage request, String correlationId,
                                        String replyTopic) {
        // Duplicate delivery: MQ gave the source at-most-once delivery inside the syncpoint, Kafka
        // gives at-least-once. A request already decided is never decided twice; the stored reply is
        // returned so the caller still gets an answer.
        Optional<AuthorizationRequestLogEntity> seen =
                requestLogRepository.findById(request.requestId());
        if (seen.isPresent()) {
            log.info("Duplicate authorization request {}, replaying stored reply", request.requestId());
            return new AuthorizationOutcome(
                    AuthorizationReplyMessage.parse(seen.get().getReplyPayload()), true);
        }

        CardholderView cardholder = cardholderLookupClient.byCardNumber(request.cardNum());
        // 5500-READ-AUTH-SUMMRY is only reached when the card was found, and the row is locked
        // because the update of 8400 belongs to this same unit of work.
        Optional<AuthorizationSummaryEntity> summary =
                cardholder.cardFound() && cardholder.accountId() != null
                        ? summaryRepository.findByIdForUpdate(cardholder.accountId())
                        : Optional.empty();
        AuthorizationReadState state = readState(cardholder, summary);
        AuthorizationDecision decision = AuthorizationDecisionEngine.decide(state, request.transactionAmt());

        AuthorizationReplyMessage reply = new AuthorizationReplyMessage(
                request.cardNum(),
                request.transactionId(),
                // MOVE PA-RQ-AUTH-TIME TO PA-RL-AUTH-ID-CODE: the acquirer's time is the auth id.
                request.authTime(),
                decision.responseCode(),
                decision.responseReason(),
                decision.approvedAmount());

        AuthorizationKey authKey = AuthorizationKey.of(LocalDateTime.now(clock));
        // IF CARD-FOUND-XREF PERFORM 8000-WRITE-AUTH-TO-DB: an unknown card leaves no trace in the
        // authorization database, only the declined reply.
        if (cardholder.cardFound()) {
            AuthorizationSummaryEntity stored = updateSummary(cardholder, decision, request, summary);
            insertDetail(stored.getAcctId(), authKey, request, reply, decision);
        }

        recordRequest(request, correlationId, reply, cardholder, authKey);
        enqueueReply(request, correlationId, replyTopic, reply);
        return new AuthorizationOutcome(reply, false);
    }

    private AuthorizationReadState readState(CardholderView cardholder,
                                             Optional<AuthorizationSummaryEntity> summary) {
        if (!cardholder.cardFound()) {
            return AuthorizationReadState.cardNotFound();
        }
        return new AuthorizationReadState(
                true,
                cardholder.accountFound(),
                cardholder.customerFound(),
                summary.isPresent(),
                summary.map(AuthorizationSummaryEntity::getCreditLimit).orElse(null),
                summary.map(AuthorizationSummaryEntity::getCreditBalance).orElse(null),
                cardholder.creditLimit(),
                cardholder.currentBalance());
    }

    /**
     * 8400-UPDATE-SUMMARY: the segment is created on the first authorization of an account and
     * replaced afterwards, and the credit and cash limits are always refreshed from the account
     * master.
     */
    private AuthorizationSummaryEntity updateSummary(CardholderView cardholder,
                                                     AuthorizationDecision decision,
                                                     AuthorizationRequestMessage request,
                                                     Optional<AuthorizationSummaryEntity> existing) {
        AuthorizationSummaryEntity summary = existing.orElseGet(() -> {
            // INITIALIZE PENDING-AUTH-SUMMARY REPLACING NUMERIC DATA BY ZERO, then the account and
            // customer id of the cross reference record.
            AuthorizationSummaryEntity created = new AuthorizationSummaryEntity();
            created.setAcctId(cardholder.accountId());
            created.setCustId(cardholder.customerId());
            return created;
        });
        summary.setCreditLimit(orZero(cardholder.creditLimit()));
        summary.setCashLimit(orZero(cardholder.cashCreditLimit()));

        if (decision.approved()) {
            summary.setApprovedAuthCnt((short) (orZero(summary.getApprovedAuthCnt()) + 1));
            summary.setApprovedAuthAmt(
                    orZero(summary.getApprovedAuthAmt()).add(decision.approvedAmount()));
            summary.setCreditBalance(
                    orZero(summary.getCreditBalance()).add(decision.approvedAmount()));
            // MOVE 0 TO PA-CASH-BALANCE on every approval. Carried forward unchanged: no source
            // evidence explains it, so it is logged as a review-required item rather than altered.
            summary.setCashBalance(BigDecimal.ZERO);
        } else {
            summary.setDeclinedAuthCnt((short) (orZero(summary.getDeclinedAuthCnt()) + 1));
            // The source adds PA-TRANSACTION-AMT here, which 8500-INSERT-AUTH has not yet loaded
            // for this message, so it accrues the previous message's amount. The declined total is
            // meant to be the sum of the declined details' amounts - CBPAUP0C subtracts exactly
            // that field when it purges one - so the defect is remediated with the current request
            // amount and recorded in the parity evidence.
            summary.setDeclinedAuthAmt(
                    orZero(summary.getDeclinedAuthAmt()).add(orZero(request.transactionAmt())));
        }
        return summaryRepository.save(summary);
    }

    /** 8500-INSERT-AUTH: the request fields plus the decision, keyed by the complemented timestamp. */
    private void insertDetail(long acctId, AuthorizationKey authKey,
                              AuthorizationRequestMessage request, AuthorizationReplyMessage reply,
                              AuthorizationDecision decision) {
        AuthorizationDetailEntity detail = new AuthorizationDetailEntity();
        detail.setId(new AuthorizationDetailId(acctId, authKey.value()));
        detail.setAuthDate9c(authKey.date9c());
        detail.setAuthTime9c(authKey.time9c());
        detail.setAuthOrigDate(request.authDate());
        detail.setAuthOrigTime(request.authTime());
        detail.setCardNum(request.cardNum());
        detail.setAuthType(request.authType());
        detail.setCardExpiryDate(request.cardExpiryDate());
        detail.setMessageType(request.messageType());
        detail.setMessageSource(request.messageSource());
        detail.setProcessingCode(request.processingCode());
        detail.setTransactionAmt(orZero(request.transactionAmt()));
        detail.setMerchantCategoryCode(request.merchantCategoryCode());
        detail.setAcqrCountryCode(request.acqrCountryCode());
        detail.setPosEntryMode(request.posEntryMode());
        detail.setMerchantId(request.merchantId());
        detail.setMerchantName(request.merchantName());
        detail.setMerchantCity(request.merchantCity());
        detail.setMerchantState(request.merchantState());
        detail.setMerchantZip(request.merchantZip());
        detail.setTransactionId(request.transactionId());
        detail.setAuthIdCode(reply.authIdCode());
        detail.setAuthRespCode(reply.authRespCode());
        detail.setAuthRespReason(reply.authRespReason());
        detail.setApprovedAmt(orZero(decision.approvedAmount()));
        detail.setMatchStatus(decision.matchStatus());
        detail.setAuthFraud(FraudStatus.NONE);
        detail.setFraudRptDate(null);
        detailRepository.save(detail);
    }

    private void recordRequest(AuthorizationRequestMessage request, String correlationId,
                               AuthorizationReplyMessage reply, CardholderView cardholder,
                               AuthorizationKey authKey) {
        AuthorizationRequestLogEntity entity = new AuthorizationRequestLogEntity();
        entity.setRequestId(request.requestId());
        entity.setCardNum(request.cardNum());
        entity.setCorrelationId(correlationId);
        entity.setAcctId(cardholder.cardFound() ? cardholder.accountId() : null);
        entity.setAuthKey(cardholder.cardFound() ? authKey.value() : null);
        entity.setReplyPayload(reply.toBuffer());
        entity.setProcessedAt(Instant.now(clock));
        requestLogRepository.save(entity);
    }

    /**
     * 7100-SEND-RESPONSE, deferred to the outbox.
     *
     * <p>The source put the reply with MQPMO-NO-SYNCPOINT before writing to IMS, so a failed write
     * still answered the acquirer. Writing the reply inside the transaction and publishing after
     * commit is the deliberate change: an authorization is never answered as approved unless the
     * balance that approved it was actually stored.
     */
    private void enqueueReply(AuthorizationRequestMessage request, String correlationId,
                              String replyTopic, AuthorizationReplyMessage reply) {
        AuthorizationReplyOutboxEntity outbox = new AuthorizationReplyOutboxEntity();
        outbox.setRequestId(request.requestId());
        outbox.setReplyTopic(replyTopic);
        // The card number is the partition key, so replies for one card stay ordered.
        outbox.setMessageKey(request.cardNum());
        outbox.setCorrelationId(correlationId);
        outbox.setPayload(reply.toBuffer());
        outbox.setCreatedAt(Instant.now(clock));
        outboxRepository.save(outbox);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static short orZero(Short value) {
        return value == null ? 0 : value;
    }
}
