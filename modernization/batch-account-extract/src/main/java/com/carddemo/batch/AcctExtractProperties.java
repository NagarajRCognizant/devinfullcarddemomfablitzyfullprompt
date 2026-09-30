package com.carddemo.batch;

import java.nio.charset.Charset;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local settings for the converted READACCT job.
 *
 * <p>The three DD statements of the job step become three files in {@code outputDirectory}. The
 * character set is configurable because the extract is a byte image of the legacy record: IBM037
 * reproduces the mainframe EBCDIC file byte for byte, while the default matches the supplied ASCII
 * sample data and is readable locally.
 */
@ConfigurationProperties(prefix = "carddemo.batch.readacct")
public class AcctExtractProperties {

    private String outputDirectory = "target/readacct";
    private String recordCharset = "ISO-8859-1";
    private int chunkSize = 100;

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
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
}
