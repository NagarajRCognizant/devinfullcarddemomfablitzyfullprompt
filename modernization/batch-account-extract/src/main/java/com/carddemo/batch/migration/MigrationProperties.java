package com.carddemo.batch.migration;

import java.nio.charset.Charset;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local settings for the converted CBEXPORT and CBIMPORT jobs.
 *
 * <p>The EXPFILE of the export job and the six output DD statements of the import job become files
 * in {@code directory}. As with the READACCT extract the record character set is configurable
 * because the export file is a byte image of a legacy record: IBM037 reproduces the mainframe
 * EBCDIC file byte for byte while the default matches the supplied ASCII sample data.
 */
@ConfigurationProperties(prefix = "carddemo.batch.migration")
public class MigrationProperties {

    private String directory = "target/migration";
    private String exportFile = "expfile.acct";
    private String importFiles = "expfile.acct,expfile.tran";
    private String customerFile = "custfile.dat";
    private String accountFile = "acctfile.dat";
    private String xrefFile = "xreffile.dat";
    private String transactionFile = "transact.dat";
    private String cardFile = "cardfile.dat";
    private String errorFile = "errfile.dat";
    private String recordCharset = "ISO-8859-1";
    private int chunkSize = 100;

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String getExportFile() {
        return exportFile;
    }

    public void setExportFile(String exportFile) {
        this.exportFile = exportFile;
    }

    public String getImportFiles() {
        return importFiles;
    }

    public void setImportFiles(String importFiles) {
        this.importFiles = importFiles;
    }

    public String getCustomerFile() {
        return customerFile;
    }

    public void setCustomerFile(String customerFile) {
        this.customerFile = customerFile;
    }

    public String getAccountFile() {
        return accountFile;
    }

    public void setAccountFile(String accountFile) {
        this.accountFile = accountFile;
    }

    public String getXrefFile() {
        return xrefFile;
    }

    public void setXrefFile(String xrefFile) {
        this.xrefFile = xrefFile;
    }

    public String getTransactionFile() {
        return transactionFile;
    }

    public void setTransactionFile(String transactionFile) {
        this.transactionFile = transactionFile;
    }

    public String getCardFile() {
        return cardFile;
    }

    public void setCardFile(String cardFile) {
        this.cardFile = cardFile;
    }

    public String getErrorFile() {
        return errorFile;
    }

    public void setErrorFile(String errorFile) {
        this.errorFile = errorFile;
    }

    public String getRecordCharset() {
        return recordCharset;
    }

    public void setRecordCharset(String recordCharset) {
        this.recordCharset = recordCharset;
    }

    public Charset charset() {
        return Charset.forName(recordCharset);
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    /** The absolute path of a configured file name. */
    public String path(String fileName) {
        return directory + "/" + fileName;
    }
}
