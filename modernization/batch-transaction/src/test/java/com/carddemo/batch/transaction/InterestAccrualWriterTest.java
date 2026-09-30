package com.carddemo.batch.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.persistence.entity.DisclosureGroupEntity;
import com.carddemo.persistence.entity.DisclosureGroupId;
import com.carddemo.persistence.entity.TransactionCategoryBalanceEntity;
import com.carddemo.persistence.entity.TransactionCategoryBalanceId;
import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.persistence.repository.DisclosureGroupRepository;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.BalanceAdjustment;
import com.carddemo.transaction.client.CardholderView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;

/** The interest accrual and account settlement of CBACT04C. */
class InterestAccrualWriterTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2022-07-01T10:15:30Z"), ZoneOffset.UTC);
    private static final String PARM_DATE = "2022-07-01";

    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final DisclosureGroupRepository disclosureGroups = mock(DisclosureGroupRepository.class);
    private final AccountServiceClient accounts = mock(AccountServiceClient.class);

    private final InterestAccrualWriter writer = new InterestAccrualWriter(transactions,
            disclosureGroups, accounts, CLOCK, PARM_DATE);

    @Test
    void writesOneInterestTransactionPerCategoryAndSettlesTheAccountOnce() {
        account(11L, "ZEROAPR");
        rate("ZEROAPR", "01", 1, "12.00");
        rate("ZEROAPR", "02", 2, "24.00");

        writer.write(new Chunk<>(balance(11L, "01", 1, "1000.00"), balance(11L, "02", 2, "500.00")));
        writer.afterStep(new StepExecution("interestCalculationStep", null));

        ArgumentCaptor<TransactionEntity> written = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactions, org.mockito.Mockito.times(2)).save(written.capture());
        TransactionEntity first = written.getAllValues().get(0);
        assertThat(first.getTranId()).isEqualTo("2022-07-01000001");
        assertThat(first.getTranTypeCd()).isEqualTo("01");
        assertThat(first.getTranCatCd()).isEqualTo(5);
        assertThat(first.getTranSource()).isEqualTo("System");
        assertThat(first.getTranDesc()).isEqualTo("Int. for a/c 00000000011");
        assertThat(first.getTranAmt()).isEqualByComparingTo("10.00");
        assertThat(written.getAllValues().get(1).getTranAmt()).isEqualByComparingTo("10.00");

        ArgumentCaptor<BalanceAdjustment> settlement =
                ArgumentCaptor.forClass(BalanceAdjustment.class);
        verify(accounts).adjustBalance(anyLong(), settlement.capture());
        assertThat(settlement.getValue().amount()).isEqualByComparingTo("20.00");
        assertThat(settlement.getValue().idempotencyKey()).isEqualTo("INTCALC-2022-07-01-11");
    }

    /** A group without its own row falls back to the DEFAULT disclosure group. */
    @Test
    void fallsBackToTheDefaultDisclosureGroup() {
        account(11L, "SPECIAL");
        rate(InterestAccrualWriter.DEFAULT_GROUP, "01", 1, "18.00");

        writer.write(new Chunk<>(balance(11L, "01", 1, "1200.00")));

        ArgumentCaptor<TransactionEntity> written = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactions).save(written.capture());
        assertThat(written.getValue().getTranAmt()).isEqualByComparingTo("18.00");
    }

    /** A zero rate accrues nothing: the source skips both the interest and the fee paragraphs. */
    @Test
    void skipsCategoriesWithoutAnInterestRate() {
        account(11L, "ZEROAPR");

        writer.write(new Chunk<>(balance(11L, "01", 1, "1000.00")));
        writer.afterStep(new StepExecution("interestCalculationStep", null));

        verify(transactions, never()).save(any());
        ArgumentCaptor<BalanceAdjustment> settlement =
                ArgumentCaptor.forClass(BalanceAdjustment.class);
        verify(accounts).adjustBalance(anyLong(), settlement.capture());
        assertThat(settlement.getValue().amount()).isEqualByComparingTo("0.00");
    }

    /** A change of account id is the key break that settles the previous account. */
    @Test
    void settlesEachAccountAtItsKeyBreak() {
        account(11L, "ZEROAPR");
        account(12L, "ZEROAPR");
        rate("ZEROAPR", "01", 1, "12.00");

        writer.write(new Chunk<>(balance(11L, "01", 1, "1000.00"), balance(12L, "01", 1, "2400.00")));
        writer.afterStep(new StepExecution("interestCalculationStep", null));

        ArgumentCaptor<Long> settled = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<BalanceAdjustment> amounts = ArgumentCaptor.forClass(BalanceAdjustment.class);
        verify(accounts, org.mockito.Mockito.times(2))
                .adjustBalance(settled.capture(), amounts.capture());
        assertThat(settled.getAllValues()).containsExactly(11L, 12L);
        assertThat(amounts.getAllValues().get(0).amount()).isEqualByComparingTo("10.00");
        assertThat(amounts.getAllValues().get(1).amount()).isEqualByComparingTo("24.00");
    }

    private void account(long accountId, String group) {
        when(accounts.byAccountId(accountId)).thenReturn(new CardholderView(true, true, true,
                "411111111111111" + accountId, accountId, new BigDecimal("100.00"),
                new BigDecimal("5000.00"), BigDecimal.ZERO, BigDecimal.ZERO, "2025-12-31", group));
    }

    private void rate(String group, String typeCd, int catCd, String rate) {
        DisclosureGroupId key = new DisclosureGroupId(group, typeCd, catCd);
        when(disclosureGroups.findById(key))
                .thenReturn(Optional.of(new DisclosureGroupEntity(key, new BigDecimal(rate))));
    }

    private TransactionCategoryBalanceEntity balance(long accountId, String typeCd, int catCd,
            String amount) {
        return new TransactionCategoryBalanceEntity(
                new TransactionCategoryBalanceId(accountId, typeCd, catCd), new BigDecimal(amount));
    }
}
