package com.carddemo.transaction.api.dto;

/**
 * The CORPT0A map as it is keyed.
 *
 * <p>The source had three separate one-byte selection fields and six date fields; the report type
 * is carried as one value here because the map allowed only one of the three to be set, and the
 * date parts are kept separate because each has its own message.
 */
public record ReportRequestForm(
        String reportType,
        String startMonth,
        String startDay,
        String startYear,
        String endMonth,
        String endDay,
        String endYear,
        String confirm) {
}
