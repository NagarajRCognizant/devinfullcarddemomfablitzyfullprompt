package com.carddemo.batch.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.persistence.entity.DailyTransactionRejectEntity;
import com.carddemo.persistence.entity.TransactionCategoryBalanceEntity;
import com.carddemo.persistence.entity.TransactionCategoryBalanceId;
import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.persistence.repository.DailyTransactionRejectRepository;
import com.carddemo.persistence.repository.TransactionCategoryBalanceRepository;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.BalanceAdjustment;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.item.Chunk;

/** The three updates of an accepted transaction and the reject record of a failed one. */
class PostedTransactionWriterTest {

    private static final String CARD = "4111111111111111";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2022-07-01T10:15:30Z"), ZoneOffset.UTC);

    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final TransactionCategoryBalanceRepository categoryBalances =
            mock(TransactionCategoryBalanceRepository.class);
    private final DailyTransactionRejectRepository rejects =
            mock(DailyTransactionRejectRepository.class);
    private final AccountServiceClient accounts = mock(AccountServiceClient.class);

    private final PostedTransactionWriter writer = new PostedTransactionWriter(transactions,
            categoryBalances, rejects, accounts, CLOCK);

    @Test
    void postsTheCategoryBalanceTheAccountAndTheTransaction() {
        when(categoryBalances.findById(any())).thenReturn(Optional.empty());

        writer.write(new Chunk<>(PostingCandidate.valid(transaction("100.00"), 11L)));

        ArgumentCaptor<TransactionCategoryBalanceEntity> balance =
                ArgumentCaptor.forClass(TransactionCategoryBalanceEntity.class);
        verify(categoryBalances).save(balance.capture());
        assertThat(balance.getValue().getTranCatBal()).isEqualByComparingTo("100.00");
        assertThat(balance.getValue().getId())
                .isEqualTo(new TransactionCategoryBalanceId(11L, "01", 1));

        ArgumentCaptor<BalanceAdjustment> adjustment =
                ArgumentCaptor.forClass(BalanceAdjustment.class);
        verify(accounts).adjustBalance(anyLong(), adjustment.capture());
        assertThat(adjustment.getValue().amount()).isEqualByComparingTo("100.00");
        assertThat(adjustment.getValue().idempotencyKey())
                .isEqualTo("POSTTRAN-0000000000000001");

        ArgumentCaptor<TransactionEntity> posted = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactions).save(posted.capture());
        assertThat(posted.getValue().getTranId()).isEqualTo("0000000000000001");
        assertThat(posted.getValue().getTranCardNum()).isEqualTo(CARD);
        assertThat(posted.getValue().getTranProcTs()).isEqualTo("2022-07-01-10.15.30.000000");
    }

    @Test
    void addsToAnExistingCategoryBalance() {
        TransactionCategoryBalanceId key = new TransactionCategoryBalanceId(11L, "01", 1);
        when(categoryBalances.findById(key)).thenReturn(Optional.of(
                new TransactionCategoryBalanceEntity(key, new BigDecimal("25.00"))));

        writer.write(new Chunk<>(PostingCandidate.valid(transaction("100.00"), 11L)));

        ArgumentCaptor<TransactionCategoryBalanceEntity> balance =
                ArgumentCaptor.forClass(TransactionCategoryBalanceEntity.class);
        verify(categoryBalances).save(balance.capture());
        assertThat(balance.getValue().getTranCatBal()).isEqualByComparingTo("125.00");
    }

    @Test
    void writesTheOriginalRecordAndReasonForARejectedTransaction() {
        DailyTransaction source = transaction("100.00");

        writer.write(new Chunk<>(PostingCandidate.rejected(source, 11L,
                PostingCandidate.OVERLIMIT, "OVERLIMIT TRANSACTION")));

        ArgumentCaptor<DailyTransactionRejectEntity> rejected =
                ArgumentCaptor.forClass(DailyTransactionRejectEntity.class);
        verify(rejects).save(rejected.capture());
        assertThat(rejected.getValue().getRejectTranData()).isEqualTo(source.rawRecord());
        assertThat(rejected.getValue().getValidationFailReason())
                .isEqualTo(PostingCandidate.OVERLIMIT);
        verify(accounts, never()).adjustBalance(anyLong(), any());
        verify(transactions, never()).save(any());
    }

    private DailyTransaction transaction(String amount) {
        return DailyTransaction.parse(DailyTransactionFixtures.record("0000000000000001", CARD,
                amount, "2022-06-10 19:27:53.000000"));
    }
}
