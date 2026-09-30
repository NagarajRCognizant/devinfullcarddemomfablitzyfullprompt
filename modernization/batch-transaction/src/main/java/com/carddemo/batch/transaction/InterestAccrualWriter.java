package com.carddemo.batch.transaction;

import com.carddemo.persistence.entity.DisclosureGroupEntity;
import com.carddemo.persistence.entity.DisclosureGroupId;
import com.carddemo.persistence.entity.TransactionCategoryBalanceEntity;
import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.persistence.repository.DisclosureGroupRepository;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.BalanceAdjustment;
import com.carddemo.transaction.client.CardholderView;
import com.carddemo.transaction.domain.Db2Timestamp;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/**
 * The body of CBACT04C: one interest transaction per category balance and one account settlement
 * per account.
 *
 * <p>The source reads TCATBAL in key order and treats a change of account id as the end of the
 * previous account, so the running total is added to the account balance and both cycle amounts
 * are zeroed at that break and again when the file ends. That key break is kept here, which is
 * why the writer is stateful and why the reader must deliver the rows in account order.
 *
 * <p>The monthly interest is the annual rate applied to the category balance for one month; the
 * source COMPUTE has no ROUNDED phrase, so the result is truncated to the two decimal places of
 * the receiving field rather than rounded.
 *
 * <p>Two source behaviours are deliberately not reproduced and are carried in the migration notes:
 * CBACT04C opens TRANSACT as OUTPUT, which on a sequential data set discards every transaction
 * already on file, and it rewrites the account it last read even when the file was empty. Interest
 * transactions are appended here and an empty run settles nothing.
 */
public class InterestAccrualWriter
        implements ItemWriter<TransactionCategoryBalanceEntity>, StepExecutionListener {

    /** 1200-A-GET-DEFAULT-INT-RATE: the group read when the account's own group has no row. */
    public static final String DEFAULT_GROUP = "DEFAULT";
    /** The divisor of 1300-COMPUTE-INTEREST: an annual percentage rate applied for one month. */
    private static final BigDecimal MONTHS_AND_PERCENT = new BigDecimal("1200");

    private static final Logger log = LoggerFactory.getLogger(InterestAccrualWriter.class);

    private final TransactionRepository transactions;
    private final DisclosureGroupRepository disclosureGroups;
    private final AccountServiceClient accounts;
    private final Clock clock;
    private final String parmDate;

    private Long currentAccountId;
    private CardholderView currentAccount;
    private BigDecimal totalInterest = BigDecimal.ZERO.setScale(2);
    private int tranIdSuffix;

    public InterestAccrualWriter(TransactionRepository transactions,
            DisclosureGroupRepository disclosureGroups,
            AccountServiceClient accounts,
            Clock clock,
            String parmDate) {
        this.transactions = transactions;
        this.disclosureGroups = disclosureGroups;
        this.accounts = accounts;
        this.clock = clock;
        this.parmDate = parmDate;
    }

    @Override
    public void write(Chunk<? extends TransactionCategoryBalanceEntity> chunk) {
        for (TransactionCategoryBalanceEntity balance : chunk) {
            Long accountId = balance.getId().getTrancatAcctId();
            if (!accountId.equals(currentAccountId)) {
                settleCurrentAccount();
                currentAccountId = accountId;
                currentAccount = accounts.byAccountId(accountId);
                totalInterest = BigDecimal.ZERO.setScale(2);
                if (!currentAccount.accountFound()) {
                    log.warn("ACCOUNT NOT FOUND: {}", accountId);
                }
            }
            accrue(balance);
        }
    }

    /** The EOF branch of the main loop, which settles the account the last record belonged to. */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        settleCurrentAccount();
        return stepExecution.getExitStatus();
    }

    private void accrue(TransactionCategoryBalanceEntity balance) {
        BigDecimal rate = interestRate(balance);
        if (rate.signum() == 0) {
            return;
        }
        BigDecimal monthlyInterest = balance.getTranCatBal()
                .multiply(rate)
                .divide(MONTHS_AND_PERCENT, 2, RoundingMode.DOWN);
        totalInterest = totalInterest.add(monthlyInterest);
        transactions.save(interestTransaction(monthlyInterest));
    }

    /** 1200-GET-INTEREST-RATE with its 1200-A fallback on the DEFAULT disclosure group. */
    private BigDecimal interestRate(TransactionCategoryBalanceEntity balance) {
        String group = currentAccount.accountGroupId();
        String typeCd = balance.getId().getTrancatTypeCd();
        Integer catCd = balance.getId().getTrancatCd();
        return disclosureGroups.findById(new DisclosureGroupId(group, typeCd, catCd))
                .or(() -> disclosureGroups.findById(new DisclosureGroupId(DEFAULT_GROUP, typeCd, catCd)))
                .map(DisclosureGroupEntity::getDisIntRate)
                .orElse(BigDecimal.ZERO);
    }

    /** 1300-B-WRITE-TX: the identifier is the run date followed by a six digit sequence. */
    private TransactionEntity interestTransaction(BigDecimal amount) {
        tranIdSuffix++;
        String timestamp = Db2Timestamp.now(clock);
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTranId(parmDate + String.format("%06d", tranIdSuffix));
        transaction.setTranTypeCd("01");
        transaction.setTranCatCd(5);
        transaction.setTranSource("System");
        transaction.setTranDesc("Int. for a/c " + String.format("%011d", currentAccountId));
        transaction.setTranAmt(amount);
        transaction.setTranMerchantId(0L);
        transaction.setTranMerchantName("");
        transaction.setTranMerchantCity("");
        transaction.setTranMerchantZip("");
        transaction.setTranCardNum(currentAccount.cardNumber());
        transaction.setTranOrigTs(timestamp);
        transaction.setTranProcTs(timestamp);
        return transaction;
    }

    /** 1050-UPDATE-ACCOUNT: the interest total is added and both cycle amounts are cleared. */
    private void settleCurrentAccount() {
        if (currentAccountId == null) {
            return;
        }
        accounts.adjustBalance(currentAccountId, BalanceAdjustment.interestSettlement(
                "INTCALC-" + parmDate + "-" + currentAccountId, totalInterest));
        currentAccountId = null;
        currentAccount = null;
        totalInterest = BigDecimal.ZERO.setScale(2);
    }
}
