package com.carddemo.transaction.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Paragraph Z-GET-DB2-FORMAT-TIMESTAMP, shared by every program that stamps a transaction.
 *
 * <p>The source builds {@code YYYY-MM-DD-HH.MM.SS.mmmmmm} from the CICS ASKTIME or the COBOL
 * {@code CURRENT-DATE} and moves the 26 characters into TRAN-ORIG-TS and TRAN-PROC-TS, so the
 * stored value is text of exactly that shape rather than a timestamp column.
 */
public final class Db2Timestamp {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd-HH.mm.ss.SSSSSS");

    private Db2Timestamp() {
    }

    public static String now(Clock clock) {
        return format(Instant.now(clock), clock);
    }

    public static String format(Instant instant, Clock clock) {
        return LocalDateTime.ofInstant(instant, clock.getZone()).format(FORMAT);
    }
}
