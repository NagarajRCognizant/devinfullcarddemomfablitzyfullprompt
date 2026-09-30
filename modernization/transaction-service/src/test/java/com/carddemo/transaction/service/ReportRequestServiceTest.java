package com.carddemo.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.exception.ScreenValidationException;
import com.carddemo.persistence.entity.ReportRequestEntity;
import com.carddemo.persistence.repository.ReportRequestRepository;
import com.carddemo.transaction.api.dto.ReportRequestForm;
import com.carddemo.transaction.api.dto.ReportRequestResponse;
import com.carddemo.transaction.domain.TransactionMessages;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** CORPT00C, the transaction report request. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportRequestServiceTest {

    @Mock
    private ReportRequestRepository requests;

    private ReportRequestService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T05:07:00Z"), ZoneOffset.UTC);
        service = new ReportRequestService(requests, clock);
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void noReportTypeIsRejected() {
        assertThatThrownBy(() -> service.submit(form("", null, null, null), "USER0001"))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(TransactionMessages.SELECT_REPORT_TYPE);
    }

    @Test
    void theMonthlyReportCoversTheCurrentMonth() {
        ReportRequestResponse response = service.submit(form("Monthly", null, null, null), "U1");

        assertThat(response.startDate()).isEqualTo("2026-09-01");
        assertThat(response.endDate()).isEqualTo("2026-09-30");
        assertThat(response.submitted()).isFalse();
        assertThat(response.message()).isEqualTo(TransactionMessages.confirmPrint("Monthly"));
    }

    @Test
    void theYearlyReportCoversTheCurrentYear() {
        ReportRequestResponse response = service.submit(
                new ReportRequestForm("Yearly", null, null, null, null, null, null, "Y"), "U1");

        assertThat(response.startDate()).isEqualTo("2026-01-01");
        assertThat(response.endDate()).isEqualTo("2026-12-31");
        assertThat(response.submitted()).isTrue();
        assertThat(response.message()).isEqualTo(TransactionMessages.reportSubmitted("Yearly"));
    }

    @Test
    void theCustomReportEditsEveryDatePartInTheOrderOfTheSource() {
        assertMessage(custom("", "10", "2022", "06", "30", "2022"),
                TransactionMessages.START_DATE_MONTH_EMPTY);
        assertMessage(custom("06", "", "2022", "06", "30", "2022"),
                TransactionMessages.START_DATE_DAY_EMPTY);
        assertMessage(custom("06", "10", "", "06", "30", "2022"),
                TransactionMessages.START_DATE_YEAR_EMPTY);
        assertMessage(custom("13", "10", "2022", "06", "30", "2022"),
                TransactionMessages.START_DATE_MONTH_INVALID);
        assertMessage(custom("06", "32", "2022", "06", "30", "2022"),
                TransactionMessages.START_DATE_DAY_INVALID);
        assertMessage(custom("06", "10", "20AA", "06", "30", "2022"),
                TransactionMessages.START_DATE_YEAR_INVALID);
        assertMessage(custom("02", "30", "2022", "06", "30", "2022"),
                TransactionMessages.START_DATE_INVALID);
        assertMessage(custom("06", "10", "2022", "", "30", "2022"),
                TransactionMessages.END_DATE_MONTH_EMPTY);
        assertMessage(custom("06", "10", "2022", "06", "31", "2022"),
                TransactionMessages.END_DATE_INVALID);
    }

    @Test
    void aConfirmedCustomReportIsRecordedWithItsKeyedRange() {
        ReportRequestResponse response = service.submit(
                new ReportRequestForm("Custom", "06", "10", "2022", "06", "30", "2022", "Y"),
                "USER0001");

        assertThat(response.startDate()).isEqualTo("2022-06-10");
        assertThat(response.endDate()).isEqualTo("2022-06-30");
        assertThat(response.submitted()).isTrue();
        verify(requests).save(any(ReportRequestEntity.class));
    }

    @Test
    void aRefusedConfirmationSubmitsNothing() {
        ReportRequestResponse response = service.submit(
                new ReportRequestForm("Monthly", null, null, null, null, null, null, "N"), "U1");

        assertThat(response.submitted()).isFalse();
        assertThat(response.message()).isEmpty();
        verify(requests, never()).save(any());
    }

    @Test
    void anythingOtherThanYesOrNoIsRejectedWithTheKeyedValue() {
        assertThatThrownBy(() -> service.submit(
                new ReportRequestForm("Monthly", null, null, null, null, null, null, "X"), "U1"))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(TransactionMessages.invalidPrintConfirmation("X"));
    }

    private void assertMessage(ReportRequestForm form, String message) {
        assertThatThrownBy(() -> service.submit(form, "USER0001"))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(message);
    }

    private static ReportRequestForm form(String type, String month, String day, String year) {
        return new ReportRequestForm(type, month, day, year, null, null, null, "");
    }

    private static ReportRequestForm custom(String startMonth, String startDay, String startYear,
            String endMonth, String endDay, String endYear) {
        return new ReportRequestForm("Custom", startMonth, startDay, startYear, endMonth, endDay,
                endYear, "Y");
    }
}
