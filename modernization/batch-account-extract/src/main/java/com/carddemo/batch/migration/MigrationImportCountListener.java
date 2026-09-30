package com.carddemo.batch.migration;

import com.carddemo.cobol.migration.MigrationExportRecord;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.item.Chunk;

/**
 * The counters CBIMPORT keeps and DISPLAYs in {@code 4000-FINALIZE}.
 *
 * <p>Every record read is counted, each record type is counted separately and unrecognised types
 * are counted twice over, once as an error record written and once as an unknown record type, which
 * is what the source program reports at the end of the run.
 */
public class MigrationImportCountListener
        implements ItemWriteListener<MigrationExportRecord>, StepExecutionListener {

    private final Logger log;
    private final Map<Character, Long> counts = new HashMap<>();
    private long totalRecordsRead;
    private long unknownRecordTypes;

    public MigrationImportCountListener(Logger log) {
        this.log = log;
    }

    @Override
    public void afterWrite(Chunk<? extends MigrationExportRecord> items) {
        for (MigrationExportRecord record : items) {
            totalRecordsRead++;
            char type = record.recordType();
            switch (type) {
                case MigrationExportRecord.TYPE_CUSTOMER, MigrationExportRecord.TYPE_ACCOUNT,
                     MigrationExportRecord.TYPE_XREF, MigrationExportRecord.TYPE_TRANSACTION,
                     MigrationExportRecord.TYPE_CARD -> counts.merge(type, 1L, Long::sum);
                default -> unknownRecordTypes++;
            }
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("CBIMPORT: Total Records Read: {}", totalRecordsRead);
        log.info("CBIMPORT: Customers Imported: {}", count(MigrationExportRecord.TYPE_CUSTOMER));
        log.info("CBIMPORT: Accounts Imported: {}", count(MigrationExportRecord.TYPE_ACCOUNT));
        log.info("CBIMPORT: XRefs Imported: {}", count(MigrationExportRecord.TYPE_XREF));
        log.info("CBIMPORT: Transactions Imported: {}",
                count(MigrationExportRecord.TYPE_TRANSACTION));
        log.info("CBIMPORT: Cards Imported: {}", count(MigrationExportRecord.TYPE_CARD));
        log.info("CBIMPORT: Errors Written: {}", unknownRecordTypes);
        log.info("CBIMPORT: Unknown Record Types: {}", unknownRecordTypes);
        return stepExecution.getExitStatus();
    }

    public long count(char recordType) {
        return counts.getOrDefault(recordType, 0L);
    }

    public long totalRecordsRead() {
        return totalRecordsRead;
    }

    public long unknownRecordTypes() {
        return unknownRecordTypes;
    }
}
