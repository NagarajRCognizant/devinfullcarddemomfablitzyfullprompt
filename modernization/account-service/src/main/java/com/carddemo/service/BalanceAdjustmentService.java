package com.carddemo.service;

import com.carddemo.api.dto.BalanceAdjustmentRequest;
import com.carddemo.api.dto.BalanceAdjustmentResponse;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.persistence.entity.AccountEntity;
import com.carddemo.persistence.entity.BalanceAdjustmentEntity;
import com.carddemo.persistence.repository.AccountRepository;
import com.carddemo.persistence.repository.BalanceAdjustmentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ACCTDAT rewrites that the transaction programs performed for themselves.
 *
 * <p>COBIL00C, CBTRN02C 2800-UPDATE-ACCOUNT-REC and CBACT04C 1050-UPDATE-ACCOUNT all read the
 * account for update and rewrote it in the same unit of recovery as their TRANSACT write. The
 * transaction context owns TRANSACT and this context owns ACCTDAT, so the rewrite arrives here as
 * a call. Two things replace the single syncpoint:
 *
 * <ul>
 *   <li>the row is locked for update, so concurrent posting cannot interleave the read and the
 *       rewrite, which is what the CICS read for update gave the source;
 *   <li>the caller's key is stored with the outcome, so a retry after a lost reply returns the
 *       first outcome instead of moving the balance twice.
 * </ul>
 */
@Service
public class BalanceAdjustmentService {

    /** CBTRN02C 2800-UPDATE-ACCOUNT-REC. */
    public static final String POSTING = "POSTING";
    /** COBIL00C: the payment clears the balance and leaves the cycle amounts alone. */
    public static final String BILL_PAYMENT = "BILL_PAYMENT";
    /** CBACT04C 1050-UPDATE-ACCOUNT: add the interest, then zero both cycle amounts. */
    public static final String INTEREST_SETTLEMENT = "INTEREST_SETTLEMENT";

    private final AccountRepository accountRepository;
    private final BalanceAdjustmentRepository adjustmentRepository;
    private final Clock clock;

    public BalanceAdjustmentService(AccountRepository accountRepository,
                                    BalanceAdjustmentRepository adjustmentRepository,
                                    Clock clock) {
        this.accountRepository = accountRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.clock = clock;
    }

    @Transactional
    public BalanceAdjustmentResponse apply(long accountId, BalanceAdjustmentRequest request) {
        Optional<BalanceAdjustmentEntity> alreadyApplied =
                adjustmentRepository.findById(request.idempotencyKey());
        if (alreadyApplied.isPresent()) {
            BalanceAdjustmentEntity applied = alreadyApplied.get();
            return new BalanceAdjustmentResponse(applied.getAcctId(), false,
                    applied.getResultingBalance(), applied.getResultingCycCredit(),
                    applied.getResultingCycDebit());
        }

        AccountEntity account = accountRepository.lockByAcctId(accountId)
                .orElseThrow(() -> new RecordNotFoundException(
                        "Account ID NOT found...", Map.of()));

        switch (request.kind()) {
            case POSTING -> post(account, request.amount());
            case BILL_PAYMENT -> pay(account, request.amount());
            case INTEREST_SETTLEMENT -> settleInterest(account, request.amount());
            default -> throw new IllegalArgumentException("Unknown adjustment " + request.kind());
        }

        adjustmentRepository.save(new BalanceAdjustmentEntity(request.idempotencyKey(), accountId,
                request.kind(), request.amount(), account.getCurrBal(), account.getCurrCycCredit(),
                account.getCurrCycDebit(), clock.instant()));

        return new BalanceAdjustmentResponse(accountId, true, account.getCurrBal(),
                account.getCurrCycCredit(), account.getCurrCycDebit());
    }

    /**
     * CBTRN02C 2800-UPDATE-ACCOUNT-REC: {@code ADD DALYTRAN-AMT TO ACCT-CURR-BAL} and then the
     * amount to the cycle credit when it is not negative, otherwise to the cycle debit.
     */
    private void post(AccountEntity account, BigDecimal amount) {
        account.setCurrBal(account.getCurrBal().add(amount));
        if (amount.signum() >= 0) {
            account.setCurrCycCredit(account.getCurrCycCredit().add(amount));
        } else {
            account.setCurrCycDebit(account.getCurrCycDebit().add(amount));
        }
    }

    /**
     * COBIL00C: {@code SUBTRACT TRAN-AMT FROM ACCT-CURR-BAL}, where the payment amount is the whole
     * balance, so the balance becomes zero and no cycle field is moved.
     */
    private void pay(AccountEntity account, BigDecimal amount) {
        account.setCurrBal(account.getCurrBal().subtract(amount));
    }

    /** CBACT04C 1050-UPDATE-ACCOUNT. */
    private void settleInterest(AccountEntity account, BigDecimal totalInterest) {
        account.setCurrBal(account.getCurrBal().add(totalInterest));
        account.setCurrCycCredit(BigDecimal.ZERO);
        account.setCurrCycDebit(BigDecimal.ZERO);
    }
}
