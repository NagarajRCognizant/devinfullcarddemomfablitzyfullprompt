package com.carddemo.batch.transaction;

import java.nio.charset.Charset;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The DD statements and the PARM of the converted jobs.
 *
 * @param inputDirectory where the DALYTRAN sequential input is read from
 * @param outputDirectory where the DALYREJS reject file and the TRANREPT report are written
 * @param dailyTransactionFile the DALYTRAN DD (AWS.M2.CARDDEMO.DALYTRAN.PS)
 * @param rejectFile the DALYREJS DD (AWS.M2.CARDDEMO.DALYREJS)
 * @param reportFile the TRANREPT DD (AWS.M2.CARDDEMO.TRANREPT)
 * @param statementFile the STMTFILE DD of CBSTM03A (AWS.M2.CARDDEMO.STATEMNT.PS)
 * @param statementHtmlFile the HTMLFILE DD of CBSTM03A (AWS.M2.CARDDEMO.STATEMNT.HTML)
 * @param migrationExportFile the transaction part of the EXPFILE DD of CBEXPORT
 * @param crossReferencePageSize how many cross reference records are fetched per call, which has
 *     no source equivalent because the source read the cluster one record at a time
 * @param recordCharset the single byte encoding the supplied ASCII sample files use
 * @param chunkSize how many records are posted per transaction, which has no source equivalent
 */
@ConfigurationProperties(prefix = "carddemo.batch.transaction")
public record TransactionBatchProperties(
        String inputDirectory,
        String outputDirectory,
        String dailyTransactionFile,
        String rejectFile,
        String reportFile,
        String statementFile,
        String statementHtmlFile,
        String migrationExportFile,
        String recordCharset,
        int chunkSize,
        int crossReferencePageSize) {

    public Charset charset() {
        return Charset.forName(recordCharset);
    }
}
