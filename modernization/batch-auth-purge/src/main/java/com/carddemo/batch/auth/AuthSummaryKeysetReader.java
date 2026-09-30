package com.carddemo.batch.auth;

import com.carddemo.persistence.entity.AuthorizationSummaryEntity;
import com.carddemo.persistence.repository.AuthorizationSummaryRepository;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.data.domain.PageRequest;

/**
 * Paragraph 2000-FIND-NEXT-AUTH-SUMMARY: the {@code GN SEGMENT(PAUTSUM0)} walk of the HIDAM roots.
 *
 * <p>The walk is expressed as "the next account ids greater than the last one processed" rather
 * than as an offset page, because the step deletes the very roots it is reading: an offset based
 * reader would skip a root for every root deleted before it, exactly as it would have skipped
 * segments if the BMP had restarted with a count instead of a key. The last processed root key is
 * the reader's restart state, so a restart resumes at the root after the last committed checkpoint,
 * which is what the IMS CHKP id gave the source.
 */
public class AuthSummaryKeysetReader implements ItemStreamReader<AuthorizationSummaryEntity> {

    /** The restart key: the root the last committed chunk finished on. */
    static final String LAST_ACCT_ID = "authSummaryReader.lastAcctId";

    private final AuthorizationSummaryRepository summaryRepository;
    private final int pageSize;
    private final Deque<AuthorizationSummaryEntity> buffer = new ArrayDeque<>();

    private long lastAcctId;

    public AuthSummaryKeysetReader(AuthorizationSummaryRepository summaryRepository, int pageSize) {
        this.summaryRepository = summaryRepository;
        this.pageSize = Math.max(pageSize, 1);
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        lastAcctId = executionContext.containsKey(LAST_ACCT_ID)
                ? executionContext.getLong(LAST_ACCT_ID)
                : 0L;
        buffer.clear();
    }

    @Override
    public AuthorizationSummaryEntity read() {
        if (buffer.isEmpty()) {
            List<AuthorizationSummaryEntity> next =
                    summaryRepository.findNextRoots(lastAcctId, PageRequest.of(0, pageSize));
            buffer.addAll(next);
        }
        // GB on the root walk: the database is exhausted and MAIN-PARA ends.
        AuthorizationSummaryEntity root = buffer.poll();
        if (root != null) {
            lastAcctId = root.getAcctId();
        }
        return root;
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putLong(LAST_ACCT_ID, lastAcctId);
    }

    @Override
    public void close() throws ItemStreamException {
        buffer.clear();
    }
}
