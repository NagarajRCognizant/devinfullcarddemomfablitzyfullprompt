package com.carddemo.batch.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.carddemo.persistence.entity.AuthorizationSummaryEntity;
import com.carddemo.persistence.repository.AuthorizationSummaryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.ExecutionContext;

/**
 * The restart state of the root walk. The IMS CHKP id let the BMP resume at the root after the
 * last committed checkpoint; the reader keeps the same position as the last root key, so a restart
 * asks for the roots after it rather than re-reading from the top of the database.
 */
@ExtendWith(MockitoExtension.class)
class AuthSummaryKeysetReaderTest {

    @Mock
    private AuthorizationSummaryRepository summaryRepository;

    @Test
    void aRestartResumesAtTheRootAfterTheLastCheckpointedOne() {
        ExecutionContext context = new ExecutionContext();
        context.putLong(AuthSummaryKeysetReader.LAST_ACCT_ID, 77L);
        when(summaryRepository.findNextRoots(eq(77L), any())).thenReturn(List.of(root(78L)));
        AuthSummaryKeysetReader reader = new AuthSummaryKeysetReader(summaryRepository, 5);

        reader.open(context);

        assertThat(reader.read().getAcctId()).isEqualTo(78L);
        reader.update(context);
        assertThat(context.getLong(AuthSummaryKeysetReader.LAST_ACCT_ID)).isEqualTo(78L);
        reader.close();
    }

    @Test
    void aFirstRunStartsAtTheTopOfTheDatabaseAndEndsWhenTheWalkIsExhausted() {
        when(summaryRepository.findNextRoots(eq(0L), any())).thenReturn(List.of());
        AuthSummaryKeysetReader reader = new AuthSummaryKeysetReader(summaryRepository, 0);

        reader.open(new ExecutionContext());

        // GB on the root walk.
        assertThat(reader.read()).isNull();
    }

    private static AuthorizationSummaryEntity root(long acctId) {
        AuthorizationSummaryEntity summary = new AuthorizationSummaryEntity();
        summary.setAcctId(acctId);
        return summary;
    }
}
