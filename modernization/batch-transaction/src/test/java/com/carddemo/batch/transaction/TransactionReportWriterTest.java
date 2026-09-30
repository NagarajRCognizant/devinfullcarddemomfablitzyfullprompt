package com.carddemo.batch.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carddemo.persistence.entity.TransactionCategoryEntity;
import com.carddemo.persistence.entity.TransactionCategoryId;
import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.persistence.entity.TransactionTypeEntity;
import com.carddemo.persistence.repository.TransactionCategoryRepository;
import com.carddemo.persistence.repository.TransactionTypeRepository;
import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.CardholderView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/** The page, account and grand totals of the daily transaction report (CBTRN03C). */
class TransactionReportWriterTest {

    private static final String CARD = "4111111111111111";

    private final List<String> printed = new ArrayList<>();
    private final ItemWriter<String> lines = chunk -> printed.addAll(chunk.getItems());
    private final TransactionTypeRepository types = mock(TransactionTypeRepository.class);
    private final TransactionCategoryRepository categories = mock(TransactionCategoryRepository.class);
    private final AccountServiceClient accounts = mock(AccountServiceClient.class);

    private TransactionReportWriter writer;

    @BeforeEach
    void setUp() {
        when(types.findById(anyString()))
                .thenReturn(Optional.of(new TransactionTypeEntity("01", "Purchase")));
        when(categories.findById(new TransactionCategoryId("01", 1)))
                .thenReturn(Optional.of(new TransactionCategoryEntity(
                        new TransactionCategoryId("01", 1), "Regular Sales Draft")));
        when(accounts.byCardNumber(anyString())).thenReturn(
                CardholderView.found(CARD, 11L, new BigDecimal("100.00")));
        writer = new TransactionReportWriter(lines, types, categories, accounts, "2022-06-01",
                "2022-06-30");
    }

    @Test
    void opensWithTheHeadersAndPrintsOneDetailLinePerTransaction() throws Exception {
        writer.write(new Chunk<>(transaction("0000000000000001", CARD, "100.00")));

        assertThat(printed).hasSize(5);
        assertThat(printed).allMatch(line -> line.length() == ReportLines.LINE_LENGTH);
        assertThat(printed.get(0)).startsWith("DALYREPT");
        assertThat(printed.get(0)).contains("Date Range: 2022-06-01 to 2022-06-30");
        assertThat(printed.get(2)).startsWith("Transaction ID   Account ID  Transaction Type");
        assertThat(printed.get(3)).isEqualTo(ReportLines.RULE);
        assertThat(printed.get(4))
                .startsWith("0000000000000001 00000000011 01-Purchase        0001-Regular Sales Draft");
        assertThat(printed.get(4)).contains("       100.00");
    }

    @Test
    void closesTheReportWithThePageTotalAndTheGrandTotal() throws Exception {
        writer.write(new Chunk<>(transaction("0000000000000001", CARD, "100.00"),
                transaction("0000000000000002", CARD, "25.50")));
        writer.afterStep(new StepExecution("transactionReportStep", null));

        assertThat(printed.get(printed.size() - 1)).startsWith("Grand Total");
        assertThat(printed.get(printed.size() - 1)).contains("+       125.50");
        assertThat(printed.get(printed.size() - 3)).startsWith("Page Total");
        assertThat(printed.get(printed.size() - 3)).contains("+       125.50");
    }

    /** A change of card number closes the previous account with its own total. */
    @Test
    void printsAnAccountTotalWhenTheCardNumberChanges() throws Exception {
        String otherCard = "4222222222222222";
        when(accounts.byCardNumber(otherCard))
                .thenReturn(CardholderView.found(otherCard, 12L, new BigDecimal("50.00")));

        writer.write(new Chunk<>(transaction("0000000000000001", CARD, "100.00"),
                transaction("0000000000000002", otherCard, "25.50")));

        List<String> accountTotals = printed.stream()
                .filter(line -> line.startsWith("Account Total"))
                .toList();
        assertThat(accountTotals).hasSize(1);
        assertThat(accountTotals.get(0)).contains("+       100.00");
    }

    /** WS-PAGE-SIZE is twenty printed lines, after which a page total and new headers appear. */
    @Test
    void startsANewPageEveryTwentyPrintedLines() throws Exception {
        List<TransactionEntity> many = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            many.add(transaction(String.format("%016d", i), CARD, "1.00"));
        }

        writer.write(new Chunk<>(many));

        assertThat(printed.stream().filter(line -> line.startsWith("Page Total")).count())
                .isEqualTo(1);
        assertThat(printed.stream().filter(line -> line.startsWith("DALYREPT")).count())
                .isEqualTo(2);
    }

    private TransactionEntity transaction(String id, String cardNumber, String amount) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTranId(id);
        transaction.setTranTypeCd("01");
        transaction.setTranCatCd(1);
        transaction.setTranSource("POS TERM");
        transaction.setTranDesc("Purchase");
        transaction.setTranAmt(new BigDecimal(amount));
        transaction.setTranMerchantId(123L);
        transaction.setTranMerchantName("Merchant");
        transaction.setTranMerchantCity("City");
        transaction.setTranMerchantZip("12345");
        transaction.setTranCardNum(cardNumber);
        transaction.setTranOrigTs("2022-06-10 19:27:53.000000");
        transaction.setTranProcTs("2022-06-11 19:27:53.000000");
        return transaction;
    }
}
