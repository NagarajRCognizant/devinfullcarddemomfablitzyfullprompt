package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One confirmed report request of CORPT00C.
 *
 * <p>The source wrote a job card to the transient data queue JOBS so the internal reader ran
 * TRANREPT with the chosen dates. There is no internal reader here: the confirmed request is
 * recorded and the transaction report job is launched with the same range, so the submission is
 * visible rather than only observable in the job log.
 */
@Entity
@Table(name = "report_request")
public class ReportRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "report_name", length = 20, nullable = false)
    private String reportName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "start_date", length = 10, nullable = false, columnDefinition = "char(10)")
    private String startDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "end_date", length = 10, nullable = false, columnDefinition = "char(10)")
    private String endDate;

    @Column(name = "requested_by", length = 8, nullable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    protected ReportRequestEntity() {
    }

    public ReportRequestEntity(String reportName, String startDate, String endDate, String requestedBy,
            Instant requestedAt) {
        this.reportName = reportName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
    }

    public Long getRequestId() {
        return requestId;
    }

    public String getReportName() {
        return reportName;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }
}
