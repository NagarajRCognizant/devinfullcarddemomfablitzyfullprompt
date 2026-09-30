package com.carddemo.transaction.service;

import com.carddemo.cobol.CobolText;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.persistence.entity.ReportRequestEntity;
import com.carddemo.persistence.repository.ReportRequestRepository;
import com.carddemo.transaction.api.dto.ReportRequestForm;
import com.carddemo.transaction.api.dto.ReportRequestResponse;
import com.carddemo.transaction.domain.TransactionDates;
import com.carddemo.transaction.domain.TransactionMessages;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CORPT00C, the transaction report request.
 *
 * <p>The source builds the JCL for TRANREPT with the date range the screen selected and writes it
 * to the JOBS transient data queue, which the internal reader turns into a batch job. There is no
 * internal reader here: the confirmed request is recorded instead, and the Spring Batch
 * transaction report job runs from it. The date derivations, the edits and the confirmation
 * handling are the source's.
 */
@Service
public class ReportRequestService {

    /** WS-REPORT-NAME, which is also the word the confirmation and success messages use. */
    public static final String MONTHLY = "Monthly";
    public static final String YEARLY = "Yearly";
    public static final String CUSTOM = "Custom";

    private final ReportRequestRepository requests;
    private final Clock clock;

    public ReportRequestService(ReportRequestRepository requests, Clock clock) {
        this.requests = requests;
        this.clock = clock;
    }

    /** PROCESS-ENTER-KEY: derive the range, edit it, confirm and submit. */
    @Transactional
    public ReportRequestResponse submit(ReportRequestForm form, String requestedBy) {
        String reportName = reportName(form.reportType());
        LocalDate today = LocalDate.now(clock);
        String startDate;
        String endDate;
        switch (reportName) {
            case MONTHLY -> {
                startDate = today.withDayOfMonth(1).toString();
                endDate = today.withDayOfMonth(today.lengthOfMonth()).toString();
            }
            case YEARLY -> {
                startDate = LocalDate.of(today.getYear(), 1, 1).toString();
                endDate = LocalDate.of(today.getYear(), 12, 31).toString();
            }
            default -> {
                startDate = customStartDate(form);
                endDate = customEndDate(form);
            }
        }

        String confirm = CobolText.trim(form.confirm());
        if (confirm.isEmpty()) {
            return new ReportRequestResponse(null, reportName, startDate, endDate, false,
                    TransactionMessages.confirmPrint(reportName));
        }
        if (confirm.equalsIgnoreCase("N")) {
            return new ReportRequestResponse(null, reportName, startDate, endDate, false, "");
        }
        if (!confirm.equalsIgnoreCase("Y")) {
            throw new ScreenValidationException("INVALID_CONFIRMATION",
                    TransactionMessages.invalidPrintConfirmation(confirm), Map.of());
        }

        ReportRequestEntity saved = requests.save(new ReportRequestEntity(reportName, startDate,
                endDate, requestedBy, Instant.now(clock)));

        return new ReportRequestResponse(saved.getRequestId(), reportName, startDate, endDate, true,
                TransactionMessages.reportSubmitted(reportName));
    }

    private static String reportName(String reportType) {
        String keyed = CobolText.trim(reportType);
        if (MONTHLY.equalsIgnoreCase(keyed)) {
            return MONTHLY;
        }
        if (YEARLY.equalsIgnoreCase(keyed)) {
            return YEARLY;
        }
        if (CUSTOM.equalsIgnoreCase(keyed)) {
            return CUSTOM;
        }
        throw new ScreenValidationException("REPORT_TYPE_REQUIRED",
                TransactionMessages.SELECT_REPORT_TYPE, Map.of());
    }

    /** The start date edits, in the order the source performs them. */
    private static String customStartDate(ReportRequestForm form) {
        requirePresent(form.startMonth(), TransactionMessages.START_DATE_MONTH_EMPTY);
        requirePresent(form.startDay(), TransactionMessages.START_DATE_DAY_EMPTY);
        requirePresent(form.startYear(), TransactionMessages.START_DATE_YEAR_EMPTY);
        requireMonth(form.startMonth(), TransactionMessages.START_DATE_MONTH_INVALID);
        requireDay(form.startDay(), TransactionMessages.START_DATE_DAY_INVALID);
        requireYear(form.startYear(), TransactionMessages.START_DATE_YEAR_INVALID);
        String date = isoDate(form.startYear(), form.startMonth(), form.startDay());
        if (!TransactionDates.isRealDate(date)) {
            throw new ScreenValidationException("START_DATE_INVALID",
                    TransactionMessages.START_DATE_INVALID, Map.of());
        }
        return date;
    }

    /** The end date edits, which the source runs after all of the start date ones. */
    private static String customEndDate(ReportRequestForm form) {
        requirePresent(form.endMonth(), TransactionMessages.END_DATE_MONTH_EMPTY);
        requirePresent(form.endDay(), TransactionMessages.END_DATE_DAY_EMPTY);
        requirePresent(form.endYear(), TransactionMessages.END_DATE_YEAR_EMPTY);
        requireMonth(form.endMonth(), TransactionMessages.END_DATE_MONTH_INVALID);
        requireDay(form.endDay(), TransactionMessages.END_DATE_DAY_INVALID);
        requireYear(form.endYear(), TransactionMessages.END_DATE_YEAR_INVALID);
        String date = isoDate(form.endYear(), form.endMonth(), form.endDay());
        if (!TransactionDates.isRealDate(date)) {
            throw new ScreenValidationException("END_DATE_INVALID",
                    TransactionMessages.END_DATE_INVALID, Map.of());
        }
        return date;
    }

    private static String isoDate(String year, String month, String day) {
        return String.format("%04d-%02d-%02d", Integer.parseInt(CobolText.trim(year)),
                Integer.parseInt(CobolText.trim(month)), Integer.parseInt(CobolText.trim(day)));
    }

    private static void requirePresent(String value, String message) {
        if (CobolText.isBlank(value)) {
            throw new ScreenValidationException("FIELD_REQUIRED", message, Map.of());
        }
    }

    /** The source compares the two keyed characters with '12', so 13 and above are rejected. */
    private static void requireMonth(String value, String message) {
        int month = numeric(value, message);
        if (month > 12) {
            throw new ScreenValidationException("FIELD_INVALID", message, Map.of());
        }
    }

    /** The same comparison against '31' for the day. */
    private static void requireDay(String value, String message) {
        int day = numeric(value, message);
        if (day > 31) {
            throw new ScreenValidationException("FIELD_INVALID", message, Map.of());
        }
    }

    private static void requireYear(String value, String message) {
        numeric(value, message);
    }

    private static int numeric(String value, String message) {
        String keyed = CobolText.trim(value);
        if (!CobolText.isNumeric(keyed)) {
            throw new ScreenValidationException("FIELD_INVALID", message, Map.of());
        }
        return Integer.parseInt(keyed);
    }
}
