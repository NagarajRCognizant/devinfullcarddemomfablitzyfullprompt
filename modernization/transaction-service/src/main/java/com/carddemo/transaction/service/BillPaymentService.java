package com.carddemo.transaction.service;

import com.carddemo.cobol.CobolText;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.exception.UpdateFailedException;
import com.carddemo.persistence.entity.BillPaymentEntity;
import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.persistence.repository.BillPaymentRepository;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.api.dto.BillPaymentRequest;
import com.carddemo.transaction.api.dto.BillPaymentResponse;
import com.carddemo.transaction.api.dto.MoneyValue;
import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.AccountServiceException;
import com.carddemo.transaction.client.BalanceAdjustment;
import com.carddemo.transaction.client.BalanceAdjustmentResult;
import com.carddemo.transaction.client.CardholderView;
import com.carddemo.transaction.domain.Db2Timestamp;
import com.carddemo.transaction.domain.TransactionIdentifiers;
import com.carddemo.transaction.domain.TransactionMessages;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * COBIL00C, the online bill payment.
 *
 * <p>The source pays the whole current balance: it reads ACCTDAT, refuses a balance that is not
 * positive, writes a TRANSACT record built from fixed literals and rewrites the account with the
 * balance reduced by that amount, all inside one CICS unit of recovery.
 *
 * <p>The account now lives in another service, so the write and the rewrite can no longer share a
 * unit of recovery. The transaction is written first and the account is adjusted with an
 * idempotency key; if the adjustment fails the transaction is removed again and the payment is
 * recorded as compensated, so the pair still ends in one of the two states the source could reach.
 * This is the one place where the source's atomicity is replaced rather than preserved, and it is
 * carried in the consistency-model delta register.
 */
@Service
public class BillPaymentService {

    /** The literals COBIL00C moves into the transaction record. */
    static final String TRAN_TYPE_CD = "02";
    static final int TRAN_CAT_CD = 2;
    static final String TRAN_SOURCE = "POS TERM";
    static final String TRAN_DESC = "BILL PAYMENT - ONLINE";
    static final long MERCHANT_ID = 999999999L;
    static final String MERCHANT_NAME = "BILL PAYMENT";
    static final String MERCHANT_CITY = "N/A";
    static final String MERCHANT_ZIP = "N/A";

    private static final Logger log = LoggerFactory.getLogger(BillPaymentService.class);

    private final TransactionRepository transactions;
    private final BillPaymentRepository billPayments;
    private final AccountServiceClient accounts;
    private final Clock clock;

    public BillPaymentService(TransactionRepository transactions, BillPaymentRepository billPayments,
                              AccountServiceClient accounts, Clock clock) {
        this.transactions = transactions;
        this.billPayments = billPayments;
        this.accounts = accounts;
        this.clock = clock;
    }

    /** PROCESS-ENTER-KEY: the balance enquiry that precedes the confirmation. */
    @Transactional(readOnly = true)
    public BillPaymentResponse balance(String accountId) {
        CardholderView account = readAccount(accountId);
        BigDecimal currentBalance = balanceOf(account);
        if (currentBalance.signum() <= 0) {
            throw new ScreenValidationException("NOTHING_TO_PAY",
                    TransactionMessages.NOTHING_TO_PAY, Map.of());
        }
        return new BillPaymentResponse(CobolText.trim(accountId), MoneyValue.of(currentBalance),
                null, false, TransactionMessages.CONFIRM_PAYMENT);
    }

    /** PROCESS-ENTER-KEY with a confirmation keyed: the payment itself. */
    @Transactional
    public BillPaymentResponse pay(BillPaymentRequest request) {
        CardholderView account = readAccount(request.accountId());
        BigDecimal currentBalance = balanceOf(account);
        if (currentBalance.signum() <= 0) {
            throw new ScreenValidationException("NOTHING_TO_PAY",
                    TransactionMessages.NOTHING_TO_PAY, Map.of());
        }

        String confirm = CobolText.trim(request.confirm());
        if (confirm.isEmpty()) {
            return new BillPaymentResponse(CobolText.trim(request.accountId()),
                    MoneyValue.of(currentBalance), null, false, TransactionMessages.CONFIRM_PAYMENT);
        }
        if (confirm.equalsIgnoreCase("N")) {
            return new BillPaymentResponse("", MoneyValue.of(BigDecimal.ZERO), null, false, "");
        }
        if (!confirm.equalsIgnoreCase("Y")) {
            throw new ScreenValidationException("INVALID_CONFIRMATION",
                    TransactionMessages.INVALID_CONFIRMATION, Map.of());
        }

        String idempotencyKey = CobolText.trim(request.idempotencyKey());
        if (idempotencyKey.isEmpty()) {
            throw new ScreenValidationException("IDEMPOTENCY_KEY_REQUIRED",
                    TransactionMessages.INVALID_CONFIRMATION, Map.of());
        }
        BillPaymentEntity existing = billPayments.findById(idempotencyKey).orElse(null);
        if (existing != null && BillPaymentEntity.POSTED.equals(existing.getStatus())) {
            return new BillPaymentResponse(String.format("%011d", existing.getAcctId()),
                    MoneyValue.of(currentBalance), CobolText.trim(existing.getTranId()), true,
                    TransactionMessages.paymentSuccessful(CobolText.trim(existing.getTranId())));
        }

        long acctId = account.accountId();
        String tranId = TransactionIdentifiers.next(
                transactions.findHighestTransactionId().orElse(null));
        String timestamp = Db2Timestamp.now(clock);

        TransactionEntity transaction = new TransactionEntity();
        transaction.setTranId(tranId);
        transaction.setTranTypeCd(TRAN_TYPE_CD);
        transaction.setTranCatCd(TRAN_CAT_CD);
        transaction.setTranSource(TRAN_SOURCE);
        transaction.setTranDesc(TRAN_DESC);
        transaction.setTranAmt(currentBalance);
        transaction.setTranMerchantId(MERCHANT_ID);
        transaction.setTranMerchantName(MERCHANT_NAME);
        transaction.setTranMerchantCity(MERCHANT_CITY);
        transaction.setTranMerchantZip(MERCHANT_ZIP);
        transaction.setTranCardNum(account.cardNumber());
        transaction.setTranOrigTs(timestamp);
        transaction.setTranProcTs(timestamp);
        transactions.saveAndFlush(transaction);

        BillPaymentEntity payment = new BillPaymentEntity(idempotencyKey, acctId, tranId,
                currentBalance, BillPaymentEntity.POSTED, Instant.now(clock));
        billPayments.saveAndFlush(payment);

        BalanceAdjustmentResult adjustment;
        try {
            adjustment = accounts.adjustBalance(acctId,
                    BalanceAdjustment.billPayment(idempotencyKey, currentBalance));
        } catch (AccountServiceException e) {
            log.error("Account {} could not be rewritten for bill payment {}; compensating",
                    acctId, tranId, e);
            transactions.delete(transaction);
            payment.setStatus(BillPaymentEntity.COMPENSATED);
            billPayments.save(payment);
            throw new UpdateFailedException(TransactionMessages.UNABLE_TO_UPDATE_ACCOUNT, e);
        }

        return new BillPaymentResponse(String.format("%011d", acctId),
                MoneyValue.of(adjustment.currentBalance()), tranId, true,
                TransactionMessages.paymentSuccessful(tranId));
    }

    private CardholderView readAccount(String accountId) {
        if (CobolText.isBlank(accountId)) {
            throw new ScreenValidationException("ACCT_ID_REQUIRED",
                    TransactionMessages.ACCT_ID_EMPTY, Map.of());
        }
        String keyed = CobolText.trim(accountId);
        if (!CobolText.isNumeric(keyed)) {
            throw new ScreenValidationException("ACCT_ID_NOT_NUMERIC",
                    TransactionMessages.ACCT_ID_EMPTY, Map.of());
        }
        CardholderView account = accounts.byAccountId(Long.parseLong(keyed));
        if (!account.accountFound() || account.accountId() == null) {
            throw new RecordNotFoundException(TransactionMessages.ACCOUNT_NOT_FOUND, Map.of());
        }
        return account;
    }

    private static BigDecimal balanceOf(CardholderView account) {
        return account.currentBalance() == null ? BigDecimal.ZERO : account.currentBalance();
    }
}
