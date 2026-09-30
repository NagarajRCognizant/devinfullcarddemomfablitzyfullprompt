package com.carddemo.batch.transaction;

import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.CardholderView;
import java.math.BigDecimal;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Paragraph 1500-VALIDATE-TRAN of CBTRN02C.
 *
 * <p>The two keyed reads of CXACAIX and ACCTDAT are one call on the account service, which owns
 * both files now; everything the edits examine comes back on that one response so the batch does
 * not read another context's tables.
 *
 * <p>The order of the two account edits is the source order, and the source deliberately does not
 * stop after the first failure: an expired account that is also over its limit is reported as
 * reason 103, because the second MOVE overwrites the first.
 */
@Component
public class DailyTransactionValidator implements ItemProcessor<DailyTransaction, PostingCandidate> {

    private final AccountServiceClient accounts;

    public DailyTransactionValidator(AccountServiceClient accounts) {
        this.accounts = accounts;
    }

    @Override
    public PostingCandidate process(DailyTransaction transaction) {
        CardholderView view = accounts.byCardNumber(transaction.cardNumber());
        if (!view.cardFound()) {
            return PostingCandidate.rejected(transaction, null, PostingCandidate.INVALID_CARD_NUMBER,
                    "INVALID CARD NUMBER FOUND");
        }
        if (!view.accountFound()) {
            return PostingCandidate.rejected(transaction, view.accountId(),
                    PostingCandidate.ACCOUNT_NOT_FOUND, "ACCOUNT RECORD NOT FOUND");
        }
        int reason = 0;
        String description = "";
        BigDecimal projectedBalance = view.currentCycleCredit()
                .subtract(view.currentCycleDebit())
                .add(transaction.amount());
        if (view.creditLimit().compareTo(projectedBalance) < 0) {
            reason = PostingCandidate.OVERLIMIT;
            description = "OVERLIMIT TRANSACTION";
        }
        if (view.expirationDate().compareTo(transaction.originalDate()) < 0) {
            reason = PostingCandidate.AFTER_EXPIRATION;
            description = "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";
        }
        return reason == 0
                ? PostingCandidate.valid(transaction, view.accountId())
                : PostingCandidate.rejected(transaction, view.accountId(), reason, description);
    }
}
