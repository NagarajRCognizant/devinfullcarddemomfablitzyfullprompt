package com.carddemo.batch.transaction;

import com.carddemo.persistence.entity.DailyTransactionRejectEntity;
import com.carddemo.persistence.entity.TransactionCategoryBalanceEntity;
import com.carddemo.persistence.entity.TransactionCategoryBalanceId;
import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.persistence.repository.DailyTransactionRejectRepository;
import com.carddemo.persistence.repository.TransactionCategoryBalanceRepository;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.BalanceAdjustment;
import com.carddemo.transaction.domain.Db2Timestamp;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Paragraphs 2000-POST-TRANSACTION, 2500-WRITE-REJECT-REC, 2700-UPDATE-TCATBAL,
 * 2800-UPDATE-ACCOUNT-REC and 2900-WRITE-TRANSACTION-FILE of CBTRN02C.
 *
 * <p>The order of the three updates is the source order: the category balance, then the account,
 * then the transaction record. The account rewrite is a call on the account service carrying an
 * idempotency key derived from the transaction identifier, so a chunk that is retried after a
 * failure between the call and the commit does not add the amount to the balance twice - the
 * source needed no such key because all three files were in one CICS unit of recovery.
 */
@Component
public class PostedTransactionWriter implements ItemWriter<PostingCandidate>, StepExecutionListener {

    private final TransactionRepository transactions;
    private final TransactionCategoryBalanceRepository categoryBalances;
    private final DailyTransactionRejectRepository rejects;
    private final AccountServiceClient accounts;
    private final Clock clock;

    private StepExecution stepExecution;

    public PostedTransactionWriter(TransactionRepository transactions,
            TransactionCategoryBalanceRepository categoryBalances,
            DailyTransactionRejectRepository rejects,
            AccountServiceClient accounts,
            Clock clock) {
        this.transactions = transactions;
        this.categoryBalances = categoryBalances;
        this.rejects = rejects;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
    }

    @Override
    public void write(Chunk<? extends PostingCandidate> chunk) {
        for (PostingCandidate candidate : chunk) {
            if (candidate.postable()) {
                post(candidate);
            } else {
                reject(candidate);
            }
        }
    }

    private void post(PostingCandidate candidate) {
        DailyTransaction source = candidate.transaction();
        updateCategoryBalance(candidate.accountId(), source);
        accounts.adjustBalance(candidate.accountId(),
                BalanceAdjustment.posting(idempotencyKey(source), source.amount()));
        transactions.save(transactionRecord(source));
    }

    /** 2700-UPDATE-TCATBAL: add to the balance of the triple, creating the row when it is absent. */
    private void updateCategoryBalance(Long accountId, DailyTransaction source) {
        TransactionCategoryBalanceId key =
                new TransactionCategoryBalanceId(accountId, source.typeCd(), source.catCd());
        TransactionCategoryBalanceEntity balance = categoryBalances.findById(key)
                .orElseGet(() -> new TransactionCategoryBalanceEntity(key, BigDecimal.ZERO.setScale(2)));
        balance.add(source.amount());
        categoryBalances.save(balance);
    }

    /** 2900-WRITE-TRANSACTION-FILE: the daily record with the run's processing timestamp. */
    private TransactionEntity transactionRecord(DailyTransaction source) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTranId(source.id());
        transaction.setTranTypeCd(source.typeCd());
        transaction.setTranCatCd(source.catCd());
        transaction.setTranSource(source.source());
        transaction.setTranDesc(source.description());
        transaction.setTranAmt(source.amount());
        transaction.setTranMerchantId(source.merchantId());
        transaction.setTranMerchantName(source.merchantName());
        transaction.setTranMerchantCity(source.merchantCity());
        transaction.setTranMerchantZip(source.merchantZip());
        transaction.setTranCardNum(source.cardNumber());
        transaction.setTranOrigTs(source.originalTimestamp());
        transaction.setTranProcTs(Db2Timestamp.now(clock));
        return transaction;
    }

    /** 2500-WRITE-REJECT-REC: the unaltered input record plus the reason code and its description. */
    private void reject(PostingCandidate candidate) {
        DailyTransaction source = candidate.transaction();
        rejects.save(new DailyTransactionRejectEntity(source.id(), source.rawRecord(),
                candidate.failReason(), candidate.failReasonDescription(), Instant.now(clock)));
        if (stepExecution != null) {
            stepExecution.getExecutionContext()
                    .putLong(PostTransactionsJobConfig.REJECT_COUNT, rejectCount() + 1);
        }
    }

    private long rejectCount() {
        return stepExecution.getExecutionContext()
                .getLong(PostTransactionsJobConfig.REJECT_COUNT, 0L);
    }

    /** The identifier is unique on the daily file, so a replay of the same record is the same key. */
    private static String idempotencyKey(DailyTransaction source) {
        return "POSTTRAN-" + source.id().trim();
    }
}
