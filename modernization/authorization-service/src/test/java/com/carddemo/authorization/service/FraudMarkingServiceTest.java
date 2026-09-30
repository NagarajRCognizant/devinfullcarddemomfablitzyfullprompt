package com.carddemo.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.carddemo.authorization.domain.AuthorizationMessages;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.persistence.entity.AuthorizationDetailEntity;
import com.carddemo.persistence.entity.AuthorizationDetailId;
import com.carddemo.persistence.entity.FraudReportEntity;
import com.carddemo.persistence.repository.AuthorizationDetailRepository;
import com.carddemo.persistence.repository.FraudReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The two data shapes COPAUS2C had to tolerate when it built the AUTHFRDS row: a segment whose
 * complemented time is absent, which reconstructs to midnight rather than failing, and a blank
 * POS-ENTRY-MODE, which DB2 held as a null rather than as a zero. Both are the SQL host-variable
 * behaviour of the source and are asserted here on the row that is written.
 */
@ExtendWith(MockitoExtension.class)
class FraudMarkingServiceTest {

    private static final long ACCOUNT_ID = 77L;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2025-09-01T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AuthorizationDetailRepository detailRepository;

    @Mock
    private FraudReportRepository fraudReportRepository;

    @Mock
    private AuthorizationInquiryService inquiryService;

    @Test
    void aSegmentWithoutAPosEntryModeWritesTheFraudRowWithTheNullHostVariable() {
        AuthorizationDetailEntity detail = detail();
        detail.setPosEntryMode("  ");
        when(detailRepository.findById(new AuthorizationDetailId(ACCOUNT_ID, "KEY1")))
                .thenReturn(Optional.of(detail));
        when(fraudReportRepository.findById(any())).thenReturn(Optional.empty());

        service().toggleFraud(ACCOUNT_ID, "KEY1");

        ArgumentCaptor<FraudReportEntity> report = ArgumentCaptor.forClass(FraudReportEntity.class);
        org.mockito.Mockito.verify(fraudReportRepository).save(report.capture());
        assertThat(report.getValue().getPosEntryMode()).isNull();
        assertThat(report.getValue().getId().getAuthTs().toLocalDate())
                .isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(detail.getAuthFraud()).isEqualTo("F");
        assertThat(detail.getFraudRptDate()).isEqualTo("09/01/25");
        org.mockito.Mockito.verify(inquiryService)
                .detailWithMessage(detail, AuthorizationMessages.FRAUD_MARKED);
    }

    @Test
    void aSuppliedPosEntryModeIsCarriedAsTheNumericHostVariableOfTheSource() {
        AuthorizationDetailEntity detail = detail();
        detail.setPosEntryMode("05");
        when(detailRepository.findById(new AuthorizationDetailId(ACCOUNT_ID, "KEY1")))
                .thenReturn(Optional.of(detail));
        when(fraudReportRepository.findById(any())).thenReturn(Optional.empty());

        service().toggleFraud(ACCOUNT_ID, "KEY1");

        ArgumentCaptor<FraudReportEntity> report = ArgumentCaptor.forClass(FraudReportEntity.class);
        org.mockito.Mockito.verify(fraudReportRepository).save(report.capture());
        assertThat(report.getValue().getPosEntryMode()).isEqualTo((short) 5);
    }

    @Test
    void aSegmentWhoseComplementedTimeIsAbsentIsRejectedRatherThanTimestampedWrongly() {
        AuthorizationDetailEntity detail = detail();
        detail.setAuthTime9c(null);
        when(detailRepository.findById(new AuthorizationDetailId(ACCOUNT_ID, "KEY1")))
                .thenReturn(Optional.of(detail));
        FraudMarkingService service = service();

        // The complement of an absent time is 999999999, which is not a time of day: the source
        // could only produce this from a segment written outside 4000-WRITE-AUTH-DETAIL, and no
        // fraud row may be written from it.
        assertThatThrownBy(() -> service.toggleFraud(ACCOUNT_ID, "KEY1"))
                .isInstanceOf(java.time.DateTimeException.class);
    }

    @Test
    void anAuthorizationThatIsNoLongerThereReturnsTheSourceMessage() {
        when(detailRepository.findById(new AuthorizationDetailId(ACCOUNT_ID, "KEY9")))
                .thenReturn(Optional.empty());
        FraudMarkingService service = service();

        assertThatThrownBy(() -> service.toggleFraud(ACCOUNT_ID, "KEY9"))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessage(AuthorizationMessages.authorizationNotFound("KEY9"));
    }

    private FraudMarkingService service() {
        return new FraudMarkingService(detailRepository, fraudReportRepository, inquiryService,
                CLOCK);
    }

    private static AuthorizationDetailEntity detail() {
        AuthorizationDetailEntity detail = new AuthorizationDetailEntity();
        detail.setId(new AuthorizationDetailId(ACCOUNT_ID, "KEY1"));
        detail.setCardNum("4111111111111111");
        detail.setAuthOrigDate("250901");
        detail.setAuthOrigTime("120000");
        detail.setAuthTime9c(956399000L);
        detail.setAuthType("01");
        detail.setAuthRespCode("00");
        detail.setAuthRespReason("0000");
        detail.setMatchStatus("P");
        return detail;
    }
}
