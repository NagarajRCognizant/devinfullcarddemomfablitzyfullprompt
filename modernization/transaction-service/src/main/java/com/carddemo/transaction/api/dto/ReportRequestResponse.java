package com.carddemo.transaction.api.dto;

/** The report request the screen submitted, and the message it showed. */
public record ReportRequestResponse(
        Long requestId,
        String reportName,
        String startDate,
        String endDate,
        boolean submitted,
        String message) {
}
