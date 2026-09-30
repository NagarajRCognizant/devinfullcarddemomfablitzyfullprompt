package com.carddemo.batch;

import com.carddemo.persistence.entity.CardEntity;
import com.carddemo.persistence.entity.CardXrefEntity;
import com.carddemo.persistence.entity.CustomerEntity;
import jakarta.persistence.EntityManagerFactory;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The three master file print programs: CBACT02C (CARDDAT), CBACT03C (CARDXREF) and CBCUS01C
 * (CUSTDAT).
 *
 * <p>All three have the same shape - open the key sequenced data set, read it to end of file,
 * DISPLAY every record, close - so each becomes a chunk oriented step whose reader is a paged
 * query ordered by the record key, which is the order the data set delivered. The DISPLAY is the
 * job's only output, so it is written to the job log with the record laid out exactly as the
 * copybook describes it.
 *
 * <p>The file status handling of the source is preserved by the framework rather than reproduced:
 * status '00' continues, '10' ends the step, and any other status fails the step, which is the
 * CEE3ABD abend path of the three programs.
 */
@Configuration
public class MasterFilePrintJobConfig {

    public static final String CARD_JOB_NAME = "printCardFileJob";
    public static final String XREF_JOB_NAME = "printCardXrefFileJob";
    public static final String CUSTOMER_JOB_NAME = "printCustomerFileJob";

    private static final Logger CARD_LOG = LoggerFactory.getLogger("CBACT02C");
    private static final Logger XREF_LOG = LoggerFactory.getLogger("CBACT03C");
    private static final Logger CUSTOMER_LOG = LoggerFactory.getLogger("CBCUS01C");

    private static final int CHUNK_SIZE = 100;

    @Bean
    public Job printCardFileJob(JobRepository jobRepository, Step printCardFileStep) {
        return new JobBuilder(CARD_JOB_NAME, jobRepository).start(printCardFileStep).build();
    }

    @Bean
    public Step printCardFileStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  JpaPagingItemReader<CardEntity> cardFileReader) {
        return new StepBuilder("printCardFileStep", jobRepository)
                .<CardEntity, CardEntity>chunk(CHUNK_SIZE, transactionManager)
                .reader(cardFileReader)
                .writer(displayWriter(CARD_LOG, MasterFileRecords::cardRecord))
                .build();
    }

    /** 1000-CARDFILE-GET-NEXT: ascending read of the CARDDAT cluster, keyed on the card number. */
    @Bean
    public JpaPagingItemReader<CardEntity> cardFileReader(EntityManagerFactory entityManagerFactory) {
        return reader("cardFileReader", entityManagerFactory,
                "select c from CardEntity c order by c.cardNum asc");
    }

    @Bean
    public Job printCardXrefFileJob(JobRepository jobRepository, Step printCardXrefFileStep) {
        return new JobBuilder(XREF_JOB_NAME, jobRepository).start(printCardXrefFileStep).build();
    }

    @Bean
    public Step printCardXrefFileStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      JpaPagingItemReader<CardXrefEntity> cardXrefFileReader) {
        return new StepBuilder("printCardXrefFileStep", jobRepository)
                .<CardXrefEntity, CardXrefEntity>chunk(CHUNK_SIZE, transactionManager)
                .reader(cardXrefFileReader)
                .writer(displayWriter(XREF_LOG, MasterFileRecords::xrefRecord))
                .build();
    }

    /** 1000-XREFFILE-GET-NEXT: ascending read of CARDXREF, keyed on the card number. */
    @Bean
    public JpaPagingItemReader<CardXrefEntity> cardXrefFileReader(
            EntityManagerFactory entityManagerFactory) {
        return reader("cardXrefFileReader", entityManagerFactory,
                "select x from CardXrefEntity x order by x.xrefCardNum asc");
    }

    @Bean
    public Job printCustomerFileJob(JobRepository jobRepository, Step printCustomerFileStep) {
        return new JobBuilder(CUSTOMER_JOB_NAME, jobRepository).start(printCustomerFileStep).build();
    }

    @Bean
    public Step printCustomerFileStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      JpaPagingItemReader<CustomerEntity> customerFileReader) {
        return new StepBuilder("printCustomerFileStep", jobRepository)
                .<CustomerEntity, CustomerEntity>chunk(CHUNK_SIZE, transactionManager)
                .reader(customerFileReader)
                .writer(displayWriter(CUSTOMER_LOG, MasterFileRecords::customerRecord))
                .build();
    }

    /** 1000-CUSTFILE-GET-NEXT: ascending read of CUSTDAT, keyed on the customer id. */
    @Bean
    public JpaPagingItemReader<CustomerEntity> customerFileReader(
            EntityManagerFactory entityManagerFactory) {
        return reader("customerFileReader", entityManagerFactory,
                "select c from CustomerEntity c order by c.custId asc");
    }

    private static <T> JpaPagingItemReader<T> reader(String name,
                                                     EntityManagerFactory entityManagerFactory,
                                                     String query) {
        return new JpaPagingItemReaderBuilder<T>()
                .name(name)
                .entityManagerFactory(entityManagerFactory)
                .queryString(query)
                .pageSize(CHUNK_SIZE)
                .saveState(true)
                .build();
    }

    private static <T> ItemWriter<T> displayWriter(Logger log, Function<T, String> image) {
        return chunk -> chunk.getItems().forEach(item -> log.info("{}", image.apply(item)));
    }
}
