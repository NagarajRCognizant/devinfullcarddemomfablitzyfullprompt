package com.carddemo.batch.auth;

import com.carddemo.persistence.entity.AuthorizationDetailId;
import com.carddemo.persistence.entity.AuthorizationSummaryEntity;
import com.carddemo.persistence.repository.AuthorizationDetailRepository;
import com.carddemo.persistence.repository.AuthorizationSummaryRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/**
 * Paragraphs 5000-DELETE-AUTH-DTL, 6000-DELETE-AUTH-SUMMARY and the counter part of
 * 4000-CHECK-IF-EXPIRED.
 *
 * <p>All writes of a chunk run in the step transaction, so the commit that Spring Batch performs at
 * the chunk boundary is the IMS CHKP of paragraph 9000-TAKE-CHECKPOINT: work before it is durable,
 * and a restart resumes from the saved reader position. The totals the program DISPLAYs at the end
 * are kept in the step execution context so that they survive a restart the way the checkpointed
 * counters did.
 */
public class AuthPurgeWriter implements ItemWriter<SummaryPurgePlan> {

    /** Keys of the run totals, named after the counters CBPAUP0C displays. */
    public static final String SUMMARY_READ = "summaryRead";
    public static final String SUMMARY_DELETED = "summaryDeleted";
    public static final String DETAIL_READ = "detailRead";
    public static final String DETAIL_DELETED = "detailDeleted";
    public static final String SUMMARY_ADJUSTED = "summaryAdjusted";

    private static final Logger log = LoggerFactory.getLogger(AuthPurgeWriter.class);

    private final AuthorizationSummaryRepository summaryRepository;
    private final AuthorizationDetailRepository detailRepository;
    private final AuthPurgeProperties properties;

    private StepExecution stepExecution;

    public AuthPurgeWriter(AuthorizationSummaryRepository summaryRepository,
                           AuthorizationDetailRepository detailRepository,
                           AuthPurgeProperties properties) {
        this.summaryRepository = summaryRepository;
        this.detailRepository = detailRepository;
        this.properties = properties;
    }

    @BeforeStep
    public void bindStepExecution(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
    }

    @Override
    public void write(Chunk<? extends SummaryPurgePlan> chunk) {
        for (SummaryPurgePlan plan : chunk) {
            apply(plan);
        }
    }

    private void apply(SummaryPurgePlan plan) {
        increment(SUMMARY_READ, 1);
        increment(DETAIL_READ, plan.detailsRead());

        for (String authKey : plan.expiredAuthKeys()) {
            detailRepository.deleteById(new AuthorizationDetailId(plan.acctId(), authKey));
        }
        increment(DETAIL_DELETED, plan.expiredAuthKeys().size());

        if (plan.deleteSummary()) {
            // DLET of the HIDAM root removes the whole hierarchy under it, so the children that
            // did not expire go with the root; the relational model deletes them explicitly.
            detailRepository.deleteByAcctId(plan.acctId());
            summaryRepository.deleteById(plan.acctId());
            increment(SUMMARY_DELETED, 1);
            if (properties.isDebug()) {
                log.info("DEBUG: AUTH SMRY DLET : {}", plan.acctId());
            }
            return;
        }

        if (plan.hasExpiredDetails() && properties.isPersistSummaryAdjustments()) {
            // DEF-AUTH-03 remediation: the source computes these reversals and then discards them
            // because it never rewrites PAUTSUM0. Persisting them is what makes the released
            // authorization amount available again, which is the documented purpose of the job.
            AuthorizationSummaryEntity summary = summaryRepository.findById(plan.acctId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Authorization summary " + plan.acctId() + " disappeared mid-purge"));
            summary.setApprovedAuthCnt(plan.approvedAuthCnt());
            summary.setApprovedAuthAmt(plan.approvedAuthAmt());
            summary.setDeclinedAuthCnt(plan.declinedAuthCnt());
            summary.setDeclinedAuthAmt(plan.declinedAuthAmt());
            summaryRepository.save(summary);
            increment(SUMMARY_ADJUSTED, 1);
        }
    }

    private void increment(String key, long delta) {
        if (stepExecution == null || delta == 0) {
            return;
        }
        var context = stepExecution.getExecutionContext();
        context.putLong(key, context.getLong(key, 0L) + delta);
    }

    /** The end of run report of MAIN-PARA, read back from the checkpointed counters. */
    public static List<String> report(StepExecution stepExecution) {
        var context = stepExecution.getExecutionContext();
        return List.of(
                "*-------------------------------------*",
                "# TOTAL SUMMARY READ  :" + context.getLong(SUMMARY_READ, 0L),
                "# SUMMARY REC DELETED :" + context.getLong(SUMMARY_DELETED, 0L),
                "# TOTAL DETAILS READ  :" + context.getLong(DETAIL_READ, 0L),
                "# DETAILS REC DELETED :" + context.getLong(DETAIL_DELETED, 0L),
                "# SUMMARY REC ADJUSTED:" + context.getLong(SUMMARY_ADJUSTED, 0L),
                "*-------------------------------------*");
    }
}
