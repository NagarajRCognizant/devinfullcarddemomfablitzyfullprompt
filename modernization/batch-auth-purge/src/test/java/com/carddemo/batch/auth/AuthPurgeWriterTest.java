package com.carddemo.batch.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.persistence.entity.AuthorizationDetailId;
import com.carddemo.persistence.entity.AuthorizationSummaryEntity;
import com.carddemo.persistence.repository.AuthorizationDetailRepository;
import com.carddemo.persistence.repository.AuthorizationSummaryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;

/**
 * The delete and counter behaviour of 5000-DELETE-AUTH-DTL and 6000-DELETE-AUTH-SUMMARY, including
 * the AS-IS comparison mode. The source computed the counter reversals and then discarded them
 * because it never rewrote PAUTSUM0; that path is kept switchable so a dual run can reproduce the
 * mainframe totals, and both paths are asserted here.
 */
@ExtendWith(MockitoExtension.class)
class AuthPurgeWriterTest {

    private static final long ACCOUNT_ID = 77L;

    @Mock
    private AuthorizationSummaryRepository summaryRepository;

    @Mock
    private AuthorizationDetailRepository detailRepository;

    @Test
    void aRootWhoseApprovedAuthorizationsAllExpiredIsDeletedWithItsWholeHierarchy() {
        StepExecution stepExecution = stepExecution();
        AuthPurgeWriter writer = writer(true, true);
        writer.bindStepExecution(stepExecution);

        writer.write(Chunk.of(plan(List.of("KEY1"), (short) 0, true)));

        verify(detailRepository).deleteById(new AuthorizationDetailId(ACCOUNT_ID, "KEY1"));
        verify(detailRepository).deleteByAcctId(ACCOUNT_ID);
        verify(summaryRepository).deleteById(ACCOUNT_ID);
        verify(summaryRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(stepExecution.getExecutionContext().getLong(AuthPurgeWriter.SUMMARY_DELETED))
                .isEqualTo(1L);
        assertThat(AuthPurgeWriter.report(stepExecution))
                .contains("# DETAILS REC DELETED :1");
    }

    @Test
    void theAsIsModeDiscardsTheCounterReversalsTheSourceNeverRewrote() {
        StepExecution stepExecution = stepExecution();
        AuthPurgeWriter writer = writer(false, false);
        writer.bindStepExecution(stepExecution);

        writer.write(Chunk.of(plan(List.of("KEY1"), (short) 1, false)));

        verify(detailRepository).deleteById(new AuthorizationDetailId(ACCOUNT_ID, "KEY1"));
        verify(summaryRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(stepExecution.getExecutionContext().getLong(AuthPurgeWriter.SUMMARY_ADJUSTED, 0L))
                .isZero();
    }

    @Test
    void aRootThatKeepsChildrenHasItsCountersRewrittenInTheRemediatedMode() {
        StepExecution stepExecution = stepExecution();
        AuthorizationSummaryEntity summary = new AuthorizationSummaryEntity();
        summary.setAcctId(ACCOUNT_ID);
        when(summaryRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(summary));
        AuthPurgeWriter writer = writer(false, true);
        writer.bindStepExecution(stepExecution);

        writer.write(Chunk.of(plan(List.of("KEY1"), (short) 1, false)));

        verify(summaryRepository).save(summary);
        assertThat(summary.getApprovedAuthCnt()).isEqualTo((short) 1);
        assertThat(summary.getApprovedAuthAmt()).isEqualByComparingTo("200.00");
        assertThat(stepExecution.getExecutionContext().getLong(AuthPurgeWriter.SUMMARY_ADJUSTED))
                .isEqualTo(1L);
    }

    /**
     * A writer used outside a step - the way the job's own restart bookkeeping is bypassed in a
     * unit context - must still perform the deletes and simply have no counters to keep.
     */
    @Test
    void withoutAStepExecutionTheDeletesStillHappenAndNoCountersAreKept() {
        AuthPurgeWriter writer = writer(false, true);
        when(summaryRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(new AuthorizationSummaryEntity()));

        writer.write(Chunk.of(plan(List.of("KEY1"), (short) 1, false)));

        verify(detailRepository).deleteById(new AuthorizationDetailId(ACCOUNT_ID, "KEY1"));
    }

    @Test
    void aRootWithNoExpiredChildrenIsNeitherDeletedNorRewritten() {
        StepExecution stepExecution = stepExecution();
        AuthPurgeWriter writer = writer(false, true);
        writer.bindStepExecution(stepExecution);

        writer.write(Chunk.of(plan(List.of(), (short) 2, false)));

        verify(detailRepository, never())
                .deleteById(org.mockito.ArgumentMatchers.any(AuthorizationDetailId.class));
        verify(summaryRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(stepExecution.getExecutionContext().getLong(AuthPurgeWriter.DETAIL_DELETED, 0L))
                .isZero();
    }

    private AuthPurgeWriter writer(boolean debug, boolean persistAdjustments) {
        AuthPurgeProperties properties = new AuthPurgeProperties();
        properties.setDebug(debug);
        properties.setPersistSummaryAdjustments(persistAdjustments);
        return new AuthPurgeWriter(summaryRepository, detailRepository, properties);
    }

    private static SummaryPurgePlan plan(List<String> expiredKeys, short approvedCnt,
                                         boolean deleteSummary) {
        return new SummaryPurgePlan(ACCOUNT_ID, 2, expiredKeys, approvedCnt,
                new BigDecimal("200.00"), (short) 0, BigDecimal.ZERO, deleteSummary);
    }

    private static StepExecution stepExecution() {
        return org.springframework.batch.test.MetaDataInstanceFactory
                .createStepExecution("purgeExpiredAuthorizationsStep", 1L);
    }
}
