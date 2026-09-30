package com.carddemo.batch.transaction;

import com.carddemo.batch.transaction.StatementRenderer.Statement;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.StatementParty;
import java.nio.charset.Charset;
import java.nio.file.Path;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Converted statement job (program CBSTM03A, JCL CREASTMT).
 *
 * <p>The source walked the cross reference to end of file, read the customer and account records
 * for each entry and printed a statement from the transactions it had loaded into a working
 * storage table; here the cross reference, customer and account reads are one call on the account
 * service and the transactions are read from the transaction database by card number. CBSTM03B,
 * the generalised file handler the source called for every one of those reads, has no counterpart:
 * its open, read, keyed read and close operations are the repository and client calls themselves.
 */
@Configuration
public class StatementJobConfig {

    public static final String JOB_NAME = "accountStatementJob";
    public static final String STEP_NAME = "accountStatementStep";

    private final TransactionBatchProperties properties;

    public StatementJobConfig(TransactionBatchProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Job accountStatementJob(JobRepository jobRepository, Step accountStatementStep) {
        return new JobBuilder(JOB_NAME, jobRepository).start(accountStatementStep).build();
    }

    @Bean
    public Step accountStatementStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            StatementPartyReader statementPartyReader,
            ItemProcessor<StatementParty, Statement> statementProcessor,
            StatementWriter statementWriter,
            FlatFileItemWriter<String> statementFileWriter,
            FlatFileItemWriter<String> statementHtmlFileWriter) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<StatementParty, Statement>chunk(properties.chunkSize(), transactionManager)
                .reader(statementPartyReader)
                .processor(statementProcessor)
                .writer(statementWriter)
                .stream(statementFileWriter)
                .stream(statementHtmlFileWriter)
                .listener(statementPartyReader)
                .build();
    }

    @Bean
    public StatementPartyReader statementPartyReader(AccountServiceClient accounts) {
        return new StatementPartyReader(accounts, properties.crossReferencePageSize());
    }

    /**
     * 4000-TRNXFILE-GET: the transactions of the card the cross reference record names, in
     * transaction id order as the working storage table held them.
     */
    @Bean
    public ItemProcessor<StatementParty, Statement> statementProcessor(
            TransactionRepository transactions, StatementRenderer renderer) {
        return party -> renderer.render(party,
                transactions.findByTranCardNumOrderByTranIdAsc(party.cardNumber()));
    }

    @Bean
    public StatementWriter statementWriter(FlatFileItemWriter<String> statementFileWriter,
            FlatFileItemWriter<String> statementHtmlFileWriter) {
        return new StatementWriter(statementFileWriter, statementHtmlFileWriter);
    }

    /** The STMTFILE DD: 80 character records. */
    @Bean
    public FlatFileItemWriter<String> statementFileWriter() {
        return fileWriter("statementFileWriter", properties.statementFile());
    }

    /** The HTMLFILE DD: 100 character records. */
    @Bean
    public FlatFileItemWriter<String> statementHtmlFileWriter() {
        return fileWriter("statementHtmlFileWriter", properties.statementHtmlFile());
    }

    private FlatFileItemWriter<String> fileWriter(String name, String fileName) {
        Path output = Path.of(properties.outputDirectory(), fileName);
        return new FlatFileItemWriterBuilder<String>()
                .name(name)
                .resource(new FileSystemResource(output))
                .encoding(Charset.forName(properties.recordCharset()).name())
                .lineAggregator(line -> line)
                .build();
    }
}
