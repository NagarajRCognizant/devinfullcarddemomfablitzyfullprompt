package com.carddemo.batch.transaction;

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
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/**
 * The print logic of CBTRN03C: paragraphs 1100-WRITE-TRANSACTION-REPORT, 1110-WRITE-PAGE-TOTALS,
 * 1110-WRITE-GRAND-TOTALS, 1120-WRITE-ACCOUNT-TOTALS, 1120-WRITE-HEADERS and 1120-WRITE-DETAIL.
 *
 * <p>The three running totals and the line counter behave as they do in the source: a page total
 * every twenty printed lines, an account total whenever the card number changes and a grand total
 * when the last selected transaction has been printed. The counters are kept in the writer, which
 * is why the reader must deliver the transactions in key order.
 */
public class TransactionReportWriter
        implements ItemWriter<TransactionEntity>, StepExecutionListener {

    private final ItemWriter<String> lines;
    private final TransactionTypeRepository types;
    private final TransactionCategoryRepository categories;
    private final AccountServiceClient accounts;
    private final String startDate;
    private final String endDate;

    private String currentCardNumber;
    private Long currentAccountId;
    private boolean headersWritten;
    private long lineCounter;
    private BigDecimal pageTotal = BigDecimal.ZERO.setScale(2);
    private BigDecimal accountTotal = BigDecimal.ZERO.setScale(2);
    private BigDecimal grandTotal = BigDecimal.ZERO.setScale(2);

    public TransactionReportWriter(ItemWriter<String> lines, TransactionTypeRepository types,
            TransactionCategoryRepository categories, AccountServiceClient accounts,
            String startDate, String endDate) {
        this.lines = lines;
        this.types = types;
        this.categories = categories;
        this.accounts = accounts;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @Override
    public void write(Chunk<? extends TransactionEntity> chunk) throws Exception {
        List<String> printed = new ArrayList<>();
        for (TransactionEntity transaction : chunk) {
            printed.addAll(print(transaction));
        }
        lines.write(new Chunk<>(printed));
    }

    /** The EOF branch of the main loop: the last page total and the grand total. */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        try {
            if (headersWritten) {
                List<String> closing = new ArrayList<>(pageTotalLines());
                closing.add(ReportLines.grandTotal(grandTotal));
                lines.write(new Chunk<>(closing));
            }
        } catch (Exception e) {
            throw new IllegalStateException("ERROR WRITING REPTFILE", e);
        }
        return stepExecution.getExitStatus();
    }

    private List<String> print(TransactionEntity transaction) {
        List<String> printed = new ArrayList<>();
        if (!transaction.getTranCardNum().equals(currentCardNumber)) {
            if (headersWritten) {
                printed.addAll(accountTotalLines());
            }
            currentCardNumber = transaction.getTranCardNum();
            CardholderView view = accounts.byCardNumber(currentCardNumber);
            currentAccountId = view.accountId();
        }
        if (!headersWritten) {
            headersWritten = true;
            printed.addAll(headerLines());
        } else if (lineCounter % ReportLines.PAGE_SIZE == 0) {
            printed.addAll(pageTotalLines());
            printed.addAll(headerLines());
        }
        BigDecimal amount = transaction.getTranAmt();
        pageTotal = pageTotal.add(amount);
        accountTotal = accountTotal.add(amount);
        printed.add(detailLine(transaction));
        lineCounter++;
        return printed;
    }

    private String detailLine(TransactionEntity transaction) {
        String typeDesc = types.findById(transaction.getTranTypeCd())
                .map(TransactionTypeEntity::getTranTypeDesc)
                .orElse("");
        String catDesc = categories
                .findById(new TransactionCategoryId(transaction.getTranTypeCd(),
                        transaction.getTranCatCd()))
                .map(TransactionCategoryEntity::getTranCatTypeDesc)
                .orElse("");
        return ReportLines.detail(transaction.getTranId(), currentAccountId,
                transaction.getTranTypeCd(), typeDesc, transaction.getTranCatCd(), catDesc,
                transaction.getTranSource(), transaction.getTranAmt());
    }

    private List<String> headerLines() {
        lineCounter += 4;
        return List.of(ReportLines.nameHeader(startDate, endDate), ReportLines.blank(),
                ReportLines.columnHeader(), ReportLines.RULE);
    }

    private List<String> pageTotalLines() {
        List<String> printed = List.of(ReportLines.pageTotal(pageTotal), ReportLines.RULE);
        grandTotal = grandTotal.add(pageTotal);
        pageTotal = BigDecimal.ZERO.setScale(2);
        lineCounter += 2;
        return printed;
    }

    private List<String> accountTotalLines() {
        List<String> printed = List.of(ReportLines.accountTotal(accountTotal), ReportLines.RULE);
        accountTotal = BigDecimal.ZERO.setScale(2);
        lineCounter += 2;
        return printed;
    }
}
