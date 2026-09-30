package com.carddemo.batch.auth;

import com.carddemo.authorization.domain.AuthorizationKey;
import com.carddemo.persistence.entity.AuthorizationDetailEntity;
import com.carddemo.persistence.entity.AuthorizationSummaryEntity;
import com.carddemo.persistence.repository.AuthorizationDetailRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.data.domain.Pageable;

/**
 * Paragraphs 3000-FIND-NEXT-AUTH-DTL, 4000-CHECK-IF-EXPIRED and the root delete test of MAIN-PARA.
 *
 * <p>The IMS GNP walk over the children of the current root becomes a query for the children of the
 * account; one item is one root with its whole child chain, which keeps the "process every child,
 * then decide about the root" sequence of the source and keeps a root and its children inside one
 * transaction.
 */
public class ExpiredAuthorizationProcessor
        implements ItemProcessor<AuthorizationSummaryEntity, SummaryPurgePlan> {

    /** The response code that marks an approved authorization in PAUTDTL1. */
    private static final String APPROVED_RESPONSE_CODE = "00";

    private static final Logger log = LoggerFactory.getLogger(ExpiredAuthorizationProcessor.class);

    private final AuthorizationDetailRepository detailRepository;
    private final AuthPurgeProperties properties;
    private final Clock clock;

    public ExpiredAuthorizationProcessor(AuthorizationDetailRepository detailRepository,
                                         AuthPurgeProperties properties,
                                         Clock clock) {
        this.detailRepository = detailRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public SummaryPurgePlan process(AuthorizationSummaryEntity summary) {
        int currentYyddd = AuthorizationKey.julianYyddd(LocalDate.now(clock));
        List<AuthorizationDetailEntity> details =
                detailRepository.findChildren(summary.getAcctId(), Pageable.unpaged());

        short approvedCnt = orZero(summary.getApprovedAuthCnt());
        short declinedCnt = orZero(summary.getDeclinedAuthCnt());
        BigDecimal approvedAmt = orZero(summary.getApprovedAuthAmt());
        BigDecimal declinedAmt = orZero(summary.getDeclinedAuthAmt());
        List<String> expiredKeys = new ArrayList<>();

        for (AuthorizationDetailEntity detail : details) {
            if (!isExpired(detail, currentYyddd)) {
                continue;
            }
            expiredKeys.add(detail.getId().getAuthKey());
            if (APPROVED_RESPONSE_CODE.equals(trim(detail.getAuthRespCode()))) {
                approvedCnt--;
                approvedAmt = approvedAmt.subtract(orZero(detail.getApprovedAmt()));
            } else {
                declinedCnt--;
                declinedAmt = declinedAmt.subtract(orZero(detail.getTransactionAmt()));
            }
            if (properties.isDebug()) {
                log.info("DEBUG: AUTH DTL DLET : {}", summary.getAcctId());
            }
        }

        // AS-IS condition carried forward unchanged from MAIN-PARA:
        //
        //     IF PA-APPROVED-AUTH-CNT <= 0 AND PA-APPROVED-AUTH-CNT <= 0
        //
        // The second operand repeats the approved counter instead of testing the declined counter,
        // so a summary whose approved authorizations have all expired is deleted even when declined
        // authorizations remain. The correct intent cannot be established from the source evidence,
        // so the defect is preserved and recorded as DEF-AUTH-02 / REVIEW REQUIRED rather than
        // silently corrected.
        boolean deleteSummary = approvedCnt <= 0 && approvedCnt <= 0;

        return new SummaryPurgePlan(summary.getAcctId(), details.size(), expiredKeys,
                approvedCnt, approvedAmt, declinedCnt, declinedAmt, deleteSummary);
    }

    /**
     * 4000-CHECK-IF-EXPIRED: the stored key holds the nines complement of the authorization date,
     * so the date is recovered as {@code 99999 - PA-AUTH-DATE-9C} and compared with today's Julian
     * date. A detail expires once that difference reaches the configured number of days.
     */
    private boolean isExpired(AuthorizationDetailEntity detail, int currentYyddd) {
        int authDate = AuthorizationKey.DATE_COMPLEMENT_BASE - orZero(detail.getAuthDate9c());
        int dayDifference = currentYyddd - authDate;
        return dayDifference >= properties.getExpiryDays();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static short orZero(Short value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
