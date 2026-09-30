package com.carddemo.batch.transaction;

import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.StatementParty;
import com.carddemo.transaction.client.StatementPartyPage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemReader;

/**
 * 1000-XREFFILE-GET-NEXT: the cross reference read to end of file, one record at a time.
 *
 * <p>The account service delivers the records a page at a time in key order, so the reader holds
 * the current page and asks for the next one when it is exhausted. End of file is the source's
 * return code '10'; a record whose customer or account read missed is the source's abend path,
 * which was a fatal condition there and is a failed step here.
 */
public class StatementPartyReader implements ItemReader<StatementParty>, StepExecutionListener {

    private final AccountServiceClient accounts;
    private final int pageSize;

    private final List<StatementParty> buffered = new ArrayList<>();
    private int nextPage;
    private boolean endOfFile;

    public StatementPartyReader(AccountServiceClient accounts, int pageSize) {
        this.accounts = accounts;
        this.pageSize = pageSize;
    }

    /** Each run opens the cross reference at its first record, as a fresh run of the job did. */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        buffered.clear();
        nextPage = 0;
        endOfFile = false;
    }

    @Override
    public StatementParty read() {
        if (buffered.isEmpty() && !endOfFile) {
            StatementPartyPage page = accounts.statementParties(nextPage, pageSize);
            nextPage++;
            endOfFile = page.lastPage();
            buffered.addAll(page.rows());
        }
        if (buffered.isEmpty()) {
            return null;
        }
        StatementParty party = buffered.remove(0);
        if (!party.customerFound()) {
            throw new IllegalStateException("ERROR READING CUSTFILE for customer "
                    + party.customerId());
        }
        if (!party.accountFound()) {
            throw new IllegalStateException("ERROR READING ACCTFILE for account "
                    + party.accountId());
        }
        return party;
    }
}
