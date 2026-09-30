package com.carddemo.cobol.migration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The fields CBEXPORT moves into every export record before the type specific mapping.
 *
 * <p>{@code 1050-GENERATE-TIMESTAMP} builds a 26 character {@code YYYY-MM-DD HH:MM:SS.00} stamp
 * from the run date and time, and every {@code nn00-CREATE-...} paragraph moves the hard coded
 * branch id '0001' and region code 'NORTH' - demo placeholder values that need business sign off
 * before a real branch migration - together with a sequence number incremented once per record
 * across all record types.
 */
public record MigrationExportHeader(String timestamp,
                                    long sequenceNumber,
                                    String branchId,
                                    String regionCode) {

    /** Hard-coded literal of {@code MOVE '0001' TO EXPORT-BRANCH-ID}. */
    public static final String BRANCH_ID = "0001";

    /** Hard-coded literal of {@code MOVE 'NORTH' TO EXPORT-REGION-CODE}. */
    public static final String REGION_CODE = "NORTH";

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** {@code 1050-GENERATE-TIMESTAMP} followed by the common MOVEs of the create paragraphs. */
    public static MigrationExportHeader of(LocalDateTime runTime, long sequenceNumber) {
        return new MigrationExportHeader(TIMESTAMP.format(runTime) + ".00", sequenceNumber,
                BRANCH_ID, REGION_CODE);
    }
}
