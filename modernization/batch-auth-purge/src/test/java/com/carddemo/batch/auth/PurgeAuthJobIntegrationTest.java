package com.carddemo.batch.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.AuthorizationPostgresIntegrationTest;
import com.carddemo.authorization.domain.AuthorizationKey;
import com.carddemo.persistence.entity.AuthorizationDetailEntity;
import com.carddemo.persistence.entity.AuthorizationDetailId;
import com.carddemo.persistence.entity.AuthorizationSummaryEntity;
import com.carddemo.persistence.repository.AuthorizationDetailRepository;
import com.carddemo.persistence.repository.AuthorizationSummaryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * Job CBPAUP0J end to end against the authorization schema: expired PAUTDTL1 children are deleted,
 * the PAUTSUM0 counters are reversed and a root that qualifies is deleted, with the chunk commit
 * standing in for the IMS checkpoint.
 *
 * <p>{@code CURRENT-YYDDD} is the only input the source takes from the environment, so the clock is
 * fixed here and the stored nines-complement dates are computed relative to it. That makes the
 * expiry boundary of paragraph 4000-CHECK-IF-EXPIRED assertable instead of dependent on the day the
 * suite runs.
 */
@SpringBatchTest
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "carddemo.batch.authpurge.expiry-days=5",
        "carddemo.batch.authpurge.checkpoint-frequency=2"
})
class PurgeAuthJobIntegrationTest extends AuthorizationPostgresIntegrationTest {

    /** 2024-03-10, Julian 24070: the run date every stored authorization date is relative to. */
    private static final LocalDate RUN_DATE = LocalDate.of(2024, 3, 10);

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    private AuthorizationSummaryRepository summaryRepository;
    @Autowired
    private AuthorizationDetailRepository detailRepository;

    @TestConfiguration
    static class FixedRunDate {

        @Bean
        @Primary
        Clock clock() {
            return Clock.fixed(RUN_DATE.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
    }

    private AuthorizationSummaryEntity summary(long acctId, int approvedCnt, String approvedAmt,
                                               int declinedCnt, String declinedAmt) {
        AuthorizationSummaryEntity summary = new AuthorizationSummaryEntity();
        summary.setAcctId(acctId);
        summary.setCustId(55L);
        summary.setAuthStatus("Y");
        summary.setAccountStatus("Y");
        summary.setCreditLimit(new BigDecimal("5000.00"));
        summary.setCashLimit(new BigDecimal("500.00"));
        summary.setCreditBalance(new BigDecimal("300.00"));
        summary.setCashBalance(new BigDecimal("0.00"));
        summary.setApprovedAuthCnt((short) approvedCnt);
        summary.setApprovedAuthAmt(new BigDecimal(approvedAmt));
        summary.setDeclinedAuthCnt((short) declinedCnt);
        summary.setDeclinedAuthAmt(new BigDecimal(declinedAmt));
        return summaryRepository.save(summary);
    }

    /**
     * A child stored {@code ageInDays} before the run date. The key holds the nines complement of
     * the Julian date, exactly as 8000-WRITE-AUTH-TO-DB wrote it.
     */
    private AuthorizationDetailEntity detail(long acctId, int ageInDays, boolean approved,
                                             String amount, int sequence) {
        int authDate = AuthorizationKey.julianYyddd(RUN_DATE.minusDays(ageInDays));
        int date9c = AuthorizationKey.DATE_COMPLEMENT_BASE - authDate;
        long time9c = AuthorizationKey.TIME_COMPLEMENT_BASE - (100000000L + sequence);
        AuthorizationKey key = new AuthorizationKey(date9c, time9c);

        AuthorizationDetailEntity detail = new AuthorizationDetailEntity();
        detail.setId(new AuthorizationDetailId(acctId, key.value()));
        detail.setAuthDate9c(date9c);
        detail.setAuthTime9c(time9c);
        detail.setAuthOrigDate("240301");
        detail.setAuthOrigTime("101500");
        detail.setCardNum("4111111111111111");
        detail.setAuthType("01");
        detail.setCardExpiryDate("1225");
        detail.setMessageType("0100");
        detail.setMessageSource("POS");
        detail.setAuthIdCode("101500");
        detail.setAuthRespCode(approved ? "00" : "05");
        detail.setAuthRespReason(approved ? "0000" : "4100");
        detail.setProcessingCode("000000");
        detail.setTransactionAmt(new BigDecimal(amount));
        detail.setApprovedAmt(approved ? new BigDecimal(amount) : BigDecimal.ZERO);
        detail.setMerchantCategoryCode("5411");
        detail.setAcqrCountryCode("840");
        detail.setPosEntryMode("05");
        detail.setMerchantId("MERCH000000001");
        detail.setMerchantName("ACME STORE");
        detail.setMerchantCity("DALLAS");
        detail.setMerchantState("TX");
        detail.setMerchantZip("75001");
        detail.setTransactionId("TRAN" + sequence);
        detail.setMatchStatus(approved ? "P" : "D");
        detail.setAuthFraud(" ");
        detail.setFraudRptDate(" ");
        return detailRepository.save(detail);
    }

    /**
     * The run totals the step kept in its execution context, which are the counters CBPAUP0C
     * displays when MAIN-PARA ends.
     */
    private static long total(JobExecution execution, String counter) {
        ExecutionContext context =
                execution.getStepExecutions().iterator().next().getExecutionContext();
        // A counter that never moved was never put in the context, which is the source's zero.
        return context.containsKey(counter) ? context.getLong(counter) : 0L;
    }

    /**
     * 4000-CHECK-IF-EXPIRED and 5000-DELETE-AUTH-DTL: the authorization older than the expiry
     * window is deleted and its amount and count come off the approved totals; the one inside the
     * window is left alone, and the root survives because approved authorizations remain.
     */
    @Test
    void expiredChildrenArePurgedAndTheApprovedTotalsAreReversed() throws Exception {
        summary(1001L, 2, "300.00", 0, "0.00");
        detail(1001L, 9, true, "100.00", 1);
        detail(1001L, 1, true, "200.00", 2);

        JobExecution execution = jobLauncherTestUtils.launchJob();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        assertThat(detailRepository.countByIdAcctId(1001L)).isEqualTo(1);

        AuthorizationSummaryEntity purged = summaryRepository.findById(1001L).orElseThrow();
        assertThat(purged.getApprovedAuthCnt()).isEqualTo((short) 1);
        assertThat(purged.getApprovedAuthAmt()).isEqualByComparingTo("200.00");

        assertThat(total(execution, AuthPurgeWriter.DETAIL_DELETED)).isEqualTo(1);
        assertThat(total(execution, AuthPurgeWriter.SUMMARY_DELETED)).isZero();
    }

    /** The declined branch of 4000-CHECK-IF-EXPIRED reverses the declined count and amount. */
    @Test
    void anExpiredDeclinedAuthorizationReversesTheDeclinedTotals() throws Exception {
        summary(1002L, 1, "50.00", 2, "75.00");
        detail(1002L, 30, false, "25.00", 1);
        detail(1002L, 0, true, "50.00", 2);

        jobLauncherTestUtils.launchJob();

        AuthorizationSummaryEntity purged = summaryRepository.findById(1002L).orElseThrow();
        assertThat(purged.getDeclinedAuthCnt()).isEqualTo((short) 1);
        assertThat(purged.getDeclinedAuthAmt()).isEqualByComparingTo("50.00");
        assertThat(purged.getApprovedAuthCnt()).isEqualTo((short) 1);
        assertThat(detailRepository.countByIdAcctId(1002L)).isEqualTo(1);
    }

    /**
     * The AS-IS defect DEF-AUTH-02 preserved: because MAIN-PARA tests the approved counter twice
     * instead of testing the declined counter, a root whose approved authorizations have all
     * expired is deleted even though a declined authorization is still recorded against it. The
     * behaviour is asserted so the defect stays visible and is not corrected by accident.
     */
    @Test
    void aRootIsDeletedOnTheApprovedCounterAloneAsTheSourceDoes() throws Exception {
        summary(1003L, 1, "100.00", 1, "40.00");
        detail(1003L, 20, true, "100.00", 1);
        detail(1003L, 0, false, "40.00", 2);

        JobExecution execution = jobLauncherTestUtils.launchJob();

        assertThat(summaryRepository.findById(1003L)).isEmpty();
        // 6000-DELETE-AUTH-SUMMARY deletes the root, and the child chain goes with it.
        assertThat(detailRepository.countByIdAcctId(1003L)).isZero();
        assertThat(total(execution, AuthPurgeWriter.SUMMARY_DELETED)).isEqualTo(1);
    }

    /** A summary with no expired children is read, reported and left untouched. */
    @Test
    void anAuthorizationInsideTheExpiryWindowIsKept() throws Exception {
        summary(1004L, 1, "10.00", 0, "0.00");
        detail(1004L, 4, true, "10.00", 1);

        JobExecution execution = jobLauncherTestUtils.launchJob();

        assertThat(detailRepository.countByIdAcctId(1004L)).isEqualTo(1);
        assertThat(summaryRepository.findById(1004L).orElseThrow().getApprovedAuthCnt())
                .isEqualTo((short) 1);
        assertThat(total(execution, AuthPurgeWriter.DETAIL_DELETED)).isZero();
        assertThat(total(execution, AuthPurgeWriter.SUMMARY_READ)).isEqualTo(1);
    }

    /**
     * 9000-TAKE-CHECKPOINT as the chunk commit: with the checkpoint frequency set to two, five
     * roots are committed in three chunks, so a failure loses at most the roots of the chunk in
     * flight and a restart resumes from the last committed reader position.
     */
    @Test
    void rootsAreCommittedAtTheConfiguredCheckpointFrequency() throws Exception {
        for (long acctId = 2001L; acctId <= 2005L; acctId++) {
            summary(acctId, 1, "10.00", 0, "0.00");
            detail(acctId, 10, true, "10.00", 1);
        }

        JobExecution execution = jobLauncherTestUtils.launchJob();

        assertThat(execution.getStepExecutions().iterator().next().getReadCount()).isEqualTo(5);
        assertThat(execution.getStepExecutions().iterator().next().getCommitCount()).isEqualTo(3);
        assertThat(total(execution, AuthPurgeWriter.SUMMARY_DELETED)).isEqualTo(5);
        assertThat(summaryRepository.count()).isZero();
    }

    /**
     * Re-running the job is safe: the purged children are no longer there to be found, so the
     * second run deletes nothing and the totals are unchanged. This is the idempotency the source
     * relied on when a failed BMP was simply resubmitted.
     */
    @Test
    void reRunningTheJobPurgesNothingFurther() throws Exception {
        summary(3001L, 2, "300.00", 0, "0.00");
        detail(3001L, 9, true, "100.00", 1);
        detail(3001L, 1, true, "200.00", 2);

        jobLauncherTestUtils.launchJob();
        JobExecution second = jobLauncherTestUtils.launchJob();

        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(total(second, AuthPurgeWriter.DETAIL_DELETED)).isZero();
        assertThat(detailRepository.countByIdAcctId(3001L)).isEqualTo(1);
        AuthorizationSummaryEntity purged = summaryRepository.findById(3001L).orElseThrow();
        assertThat(purged.getApprovedAuthCnt()).isEqualTo((short) 1);
        assertThat(purged.getApprovedAuthAmt()).isEqualByComparingTo("200.00");
    }
}
