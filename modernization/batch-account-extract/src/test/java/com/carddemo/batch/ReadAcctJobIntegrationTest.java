package com.carddemo.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.PostgresIntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/** The converted READACCT job end to end against the seeded database. */
@SpringBatchTest
class ReadAcctJobIntegrationTest extends PostgresIntegrationTest {

    private static final int SAMPLE_ACCOUNTS = 50;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired
    private Job readAcctJob;
    @Value("${carddemo.batch.readacct.output-directory}")
    private String outputDirectory;

    @Test
    void theJobWritesOneRecordPerAccountIntoEachExtract() throws Exception {
        JobExecution execution = run();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions()).singleElement()
                .satisfies(step -> assertThat(step.getReadCount()).isEqualTo(SAMPLE_ACCOUNTS));

        assertThat(size(ReadAcctJobConfig.COMP_FILE))
                .isEqualTo((long) SAMPLE_ACCOUNTS * AcctCompRecordAggregator.RECORD_LENGTH);
        assertThat(size(ReadAcctJobConfig.ARRAY_FILE))
                .isEqualTo((long) SAMPLE_ACCOUNTS * AcctArrayRecordAggregator.RECORD_LENGTH);
        assertThat(size(ReadAcctJobConfig.VB_FILE)).isEqualTo((long) SAMPLE_ACCOUNTS
                * (4 + AcctVbRecordAggregator.VB1_LENGTH + 4 + AcctVbRecordAggregator.VB2_LENGTH));
    }

    /** The extract is written in ascending account id order, as the sequential KSDS read was. */
    @Test
    void theFirstAndLastCompRecordsCarryTheLowestAndHighestKey() throws Exception {
        run();

        byte[] extract = Files.readAllBytes(file(ReadAcctJobConfig.COMP_FILE));
        int length = AcctCompRecordAggregator.RECORD_LENGTH;

        assertThat(key(extract, 0)).isEqualTo("00000000001");
        assertThat(key(extract, (SAMPLE_ACCOUNTS - 1) * length)).isEqualTo("00000000050");
    }

    /**
     * Re-running the job on a fresh instance recreates the three files, which is the PREDEL step of
     * the JCL: the output is the same for the same input, so a rerun is idempotent.
     */
    @Test
    void aRerunReplacesTheExtractRatherThanAppendingToIt() throws Exception {
        run();
        long firstRun = size(ReadAcctJobConfig.COMP_FILE);
        byte[] firstBytes = Files.readAllBytes(file(ReadAcctJobConfig.COMP_FILE));

        run();

        assertThat(size(ReadAcctJobConfig.COMP_FILE)).isEqualTo(firstRun);
        assertThat(Files.readAllBytes(file(ReadAcctJobConfig.COMP_FILE))).isEqualTo(firstBytes);
    }

    private JobExecution run() throws Exception {
        jobLauncherTestUtils.setJob(readAcctJob);
        JobParameters parameters = new JobParametersBuilder()
                .addLong("run.id", System.nanoTime())
                .toJobParameters();
        return jobLauncherTestUtils.launchJob(parameters);
    }

    private Path file(String name) {
        return Path.of(outputDirectory, name);
    }

    private long size(String name) throws IOException {
        return Files.size(file(name));
    }

    private static String key(byte[] extract, int offset) {
        return new String(extract, offset, 11, StandardCharsets.ISO_8859_1);
    }
}
