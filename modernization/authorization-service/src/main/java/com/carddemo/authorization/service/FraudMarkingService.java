package com.carddemo.authorization.service;

import com.carddemo.authorization.api.dto.AuthorizationDetailResponse;
import com.carddemo.authorization.domain.AuthorizationKey;
import com.carddemo.authorization.domain.AuthorizationMessages;
import com.carddemo.authorization.domain.FraudStatus;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.persistence.entity.AuthorizationDetailEntity;
import com.carddemo.persistence.entity.AuthorizationDetailId;
import com.carddemo.persistence.entity.FraudReportEntity;
import com.carddemo.persistence.entity.FraudReportId;
import com.carddemo.persistence.repository.AuthorizationDetailRepository;
import com.carddemo.persistence.repository.FraudReportRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The fraud toggle of CPVD: MARK-AUTH-FRAUD of COPAUS1C together with the program it linked to,
 * COPAUS2C.
 *
 * <p>In the source the toggle spanned two resource managers - the DB2 row was written by COPAUS2C
 * and the IMS segment was replaced by COPAUS1C, both committed by one CICS SYNCPOINT and both
 * rolled back together on failure. Both tables now live in this service's own database, so the
 * source's two phase commit is preserved as a single local transaction and no compensating saga is
 * needed; that resolution is recorded in the consistency model delta register.
 */
@Service
public class FraudMarkingService {

    private static final DateTimeFormatter REPORT_DATE = DateTimeFormatter.ofPattern("MM/dd/yy");

    private final AuthorizationDetailRepository detailRepository;
    private final FraudReportRepository fraudReportRepository;
    private final AuthorizationInquiryService inquiryService;
    private final Clock clock;

    public FraudMarkingService(AuthorizationDetailRepository detailRepository,
                               FraudReportRepository fraudReportRepository,
                               AuthorizationInquiryService inquiryService,
                               Clock clock) {
        this.detailRepository = detailRepository;
        this.fraudReportRepository = fraudReportRepository;
        this.inquiryService = inquiryService;
        this.clock = clock;
    }

    /**
     * Toggles the fraud flag of one authorization, writes the fraud report row and returns the
     * redisplayed detail with the source message.
     */
    @Transactional
    public AuthorizationDetailResponse toggleFraud(long accountId, String authKey) {
        AuthorizationDetailEntity detail = detailRepository
                .findById(new AuthorizationDetailId(accountId, authKey))
                .orElseThrow(() -> new RecordNotFoundException(
                        AuthorizationMessages.authorizationNotFound(authKey), Map.of()));

        String nextStatus = FraudStatus.toggle(detail.getAuthFraud());
        String reportDate = LocalDate.now(clock).format(REPORT_DATE);

        upsertFraudReport(detail, nextStatus);

        detail.setAuthFraud(nextStatus);
        detail.setFraudRptDate(reportDate);
        detailRepository.save(detail);

        String message = FraudStatus.REMOVED.equals(nextStatus)
                ? AuthorizationMessages.FRAUD_REMOVED
                : AuthorizationMessages.FRAUD_MARKED;
        return inquiryService.detailWithMessage(detail, message);
    }

    /**
     * COPAUS2C: an INSERT that falls back to an UPDATE of AUTH_FRAUD and FRAUD_RPT_DATE when DB2
     * reports the duplicate key -803. The primary key is the card number and the authorization
     * timestamp rebuilt from the complemented time of the segment key, so the same authorization
     * always addresses the same row.
     */
    private void upsertFraudReport(AuthorizationDetailEntity detail, String fraudAction) {
        LocalDateTime authTs = authTimestamp(detail);
        FraudReportId id = new FraudReportId(detail.getCardNum(), authTs);
        Optional<FraudReportEntity> existing = fraudReportRepository.findById(id);

        FraudReportEntity report = existing.orElseGet(() -> newReport(detail, id));
        report.setAuthFraud(fraudAction);
        report.setFraudRptDate(LocalDate.now(clock));
        fraudReportRepository.save(report);
    }

    private FraudReportEntity newReport(AuthorizationDetailEntity detail, FraudReportId id) {
        FraudReportEntity report = new FraudReportEntity();
        report.setId(id);
        report.setAuthType(detail.getAuthType());
        report.setCardExpiryDate(detail.getCardExpiryDate());
        report.setMessageType(detail.getMessageType());
        report.setMessageSource(detail.getMessageSource());
        report.setAuthIdCode(detail.getAuthIdCode());
        report.setAuthRespCode(detail.getAuthRespCode());
        report.setAuthRespReason(detail.getAuthRespReason());
        report.setProcessingCode(detail.getProcessingCode());
        report.setTransactionAmt(detail.getTransactionAmt());
        report.setApprovedAmt(detail.getApprovedAmt());
        report.setMerchantCatagoryCode(detail.getMerchantCategoryCode());
        report.setAcqrCountryCode(detail.getAcqrCountryCode());
        report.setPosEntryMode(posEntryMode(detail.getPosEntryMode()));
        report.setMerchantId(detail.getMerchantId());
        report.setMerchantName(detail.getMerchantName());
        report.setMerchantCity(detail.getMerchantCity());
        report.setMerchantState(detail.getMerchantState());
        report.setMerchantZip(detail.getMerchantZip());
        report.setTransactionId(detail.getTransactionId());
        report.setMatchStatus(detail.getMatchStatus());
        report.setAcctId(BigDecimal.valueOf(detail.getId().getAcctId()));
        return report;
    }

    /**
     * COPAUS2C rebuilds AUTH_TS from PA-AUTH-ORIG-DATE and {@code 999999999 - PA-AUTH-TIME-9C}:
     * the date part is the original YYMMDD and the time part the uncomplemented HHMMSSmmm.
     */
    private static LocalDateTime authTimestamp(AuthorizationDetailEntity detail) {
        long timeWithMillis = AuthorizationKey.TIME_COMPLEMENT_BASE
                - (detail.getAuthTime9c() == null ? 0L : detail.getAuthTime9c());
        return AuthorizationKey.fraudTimestamp(detail.getAuthOrigDate(), timeWithMillis);
    }

    private static Short posEntryMode(String value) {
        String digits = value == null ? "" : value.trim();
        return digits.isEmpty() ? null : Short.valueOf(digits);
    }
}
