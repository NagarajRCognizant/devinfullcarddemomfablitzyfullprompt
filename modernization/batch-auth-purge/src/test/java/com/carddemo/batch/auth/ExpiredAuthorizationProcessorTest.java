package com.carddemo.batch.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.carddemo.authorization.domain.AuthorizationKey;
import com.carddemo.persistence.entity.AuthorizationDetailEntity;
import com.carddemo.persistence.entity.AuthorizationDetailId;
import com.carddemo.persistence.entity.AuthorizationSummaryEntity;
import com.carddemo.persistence.repository.AuthorizationDetailRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The working-storage arithmetic of 4000-CHECK-IF-EXPIRED on segments whose numeric fields were
 * never populated. PAUTSUM0 and PAUTDTL1 segments loaded by PAUDBLOD carry low values in the
 * counter and amount fields, which COBOL read as zero; the relational columns are nullable for the
 * same reason, so the reversal arithmetic must treat a null as the zero the source saw.
 */
@ExtendWith(MockitoExtension.class)
class ExpiredAuthorizationProcessorTest {

    private static final long ACCOUNT_ID = 77L;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2025-09-01T12:00:00Z"), ZoneOffset.UTC);
    private static final int TODAY = AuthorizationKey.julianYyddd(LocalDate.of(2025, 9, 1));

    @Mock
    private AuthorizationDetailRepository detailRepository;

    @Test
    void aSummaryWithUnpopulatedCountersAndAnUnpopulatedDetailDateReversesFromZero() {
        AuthorizationSummaryEntity summary = new AuthorizationSummaryEntity();
        summary.setAcctId(ACCOUNT_ID);
        summary.setApprovedAuthCnt(null);
        summary.setDeclinedAuthCnt(null);
        summary.setApprovedAuthAmt(null);
        summary.setDeclinedAuthAmt(null);

        // A missing response code is not the approved code, so the reversal takes the declined
        // side, and a missing transaction amount subtracts the zero COBOL read from low values.
        AuthorizationDetailEntity expired = new AuthorizationDetailEntity();
        expired.setId(new AuthorizationDetailId(ACCOUNT_ID, "KEY1"));
        expired.setAuthDate9c(AuthorizationKey.DATE_COMPLEMENT_BASE - (TODAY - 10));
        expired.setAuthRespCode(null);
        expired.setTransactionAmt(null);

        // A missing complemented date recovers as 99999, a date in the future of the Julian scale,
        // so the difference is negative and the detail is not yet expired.
        AuthorizationDetailEntity undated = new AuthorizationDetailEntity();
        undated.setId(new AuthorizationDetailId(ACCOUNT_ID, "KEY2"));
        undated.setAuthDate9c(null);
        undated.setAuthRespCode("00");
        when(detailRepository.findChildren(anyLong(), any()))
                .thenReturn(List.of(expired, undated));

        SummaryPurgePlan plan = processor(true).process(summary);

        assertThat(plan.expiredAuthKeys()).containsExactly("KEY1");
        assertThat(plan.detailsRead()).isEqualTo(2);
        assertThat(plan.approvedAuthCnt()).isZero();
        assertThat(plan.declinedAuthCnt()).isEqualTo((short) -1);
        assertThat(plan.approvedAuthAmt()).isEqualByComparingTo("0");
        assertThat(plan.declinedAuthAmt()).isEqualByComparingTo("0");
        assertThat(plan.deleteSummary()).isTrue();
    }

    @Test
    void anAuthorizationInsideTheRetentionWindowIsLeftAloneAndItsRootIsKept() {
        AuthorizationSummaryEntity summary = new AuthorizationSummaryEntity();
        summary.setAcctId(ACCOUNT_ID);
        summary.setApprovedAuthCnt((short) 2);
        summary.setDeclinedAuthCnt((short) 1);
        summary.setApprovedAuthAmt(new BigDecimal("300.00"));
        summary.setDeclinedAuthAmt(new BigDecimal("40.00"));

        AuthorizationDetailEntity detail = new AuthorizationDetailEntity();
        detail.setId(new AuthorizationDetailId(ACCOUNT_ID, "KEY1"));
        detail.setAuthDate9c(AuthorizationKey.DATE_COMPLEMENT_BASE - TODAY);
        detail.setAuthRespCode("00");
        detail.setApprovedAmt(new BigDecimal("100.00"));
        when(detailRepository.findChildren(anyLong(), any())).thenReturn(List.of(detail));

        SummaryPurgePlan plan = processor(false).process(summary);

        assertThat(plan.expiredAuthKeys()).isEmpty();
        assertThat(plan.detailsRead()).isEqualTo(1);
        assertThat(plan.approvedAuthCnt()).isEqualTo((short) 2);
        assertThat(plan.approvedAuthAmt()).isEqualByComparingTo("300.00");
        assertThat(plan.deleteSummary()).isFalse();
    }

    private ExpiredAuthorizationProcessor processor(boolean debug) {
        AuthPurgeProperties properties = new AuthPurgeProperties();
        properties.setExpiryDays(5);
        properties.setDebug(debug);
        return new ExpiredAuthorizationProcessor(detailRepository, properties, CLOCK);
    }
}
