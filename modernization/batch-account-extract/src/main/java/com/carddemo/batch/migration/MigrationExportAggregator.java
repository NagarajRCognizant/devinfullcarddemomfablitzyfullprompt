package com.carddemo.batch.migration;

import com.carddemo.cobol.CobolText;
import com.carddemo.cobol.migration.MigrationExportData;
import com.carddemo.cobol.migration.MigrationExportHeader;
import com.carddemo.cobol.migration.MigrationExportRecord;
import com.carddemo.persistence.entity.AccountEntity;
import com.carddemo.persistence.entity.CardEntity;
import com.carddemo.persistence.entity.CardXrefEntity;
import com.carddemo.persistence.entity.CustomerEntity;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.file.transform.LineAggregator;

/**
 * The {@code nn00-CREATE-...-EXP-REC} paragraphs of CBEXPORT.
 *
 * <p>Each input record becomes one 500 byte export record: the shared header carries the run
 * timestamp of {@code 1050-GENERATE-TIMESTAMP}, the branch and region literals and a sequence
 * number incremented once per record across all record types, and the record data area is the
 * layout of the record type.
 *
 * <p>The record image is handed to the writer through ISO-8859-1 so that the COMP and COMP-3 bytes
 * of the record reach the file unchanged whatever character set the character fields use.
 */
public class MigrationExportAggregator implements LineAggregator<Object>, StepExecutionListener {

    private final Charset charset;
    private LocalDateTime runTime;
    private long sequenceCounter;

    public MigrationExportAggregator(Charset charset) {
        this.charset = charset;
    }

    /**
     * 1050-GENERATE-TIMESTAMP and the initial value of WS-SEQUENCE-COUNTER: every run stamps its
     * own timestamp and numbers its records from one, as a fresh execution of the program did.
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        runTime = LocalDateTime.now();
        sequenceCounter = 0;
    }

    /** WS-SEQUENCE-COUNTER after the last written record. */
    public long sequenceCounter() {
        return sequenceCounter;
    }

    @Override
    public String aggregate(Object item) {
        MigrationExportHeader header = MigrationExportHeader.of(runTime, ++sequenceCounter);
        byte[] record = switch (item) {
            case CustomerEntity customer ->
                    MigrationExportRecord.encode(header, customer(customer), charset);
            case AccountEntity account ->
                    MigrationExportRecord.encode(header, account(account), charset);
            case CardXrefEntity xref -> MigrationExportRecord.encode(header, xref(xref), charset);
            case CardEntity card -> MigrationExportRecord.encode(header, card(card), charset);
            default -> throw new IllegalArgumentException(
                    "Unsupported export item: " + item.getClass().getName());
        };
        return new String(record, StandardCharsets.ISO_8859_1);
    }

    /** 2200-CREATE-CUSTOMER-EXP-REC. */
    static MigrationExportData.Customer customer(CustomerEntity customer) {
        return new MigrationExportData.Customer(customer.getCustId(), customer.getFirstName(),
                customer.getMiddleName(), customer.getLastName(), customer.getAddrLine1(),
                customer.getAddrLine2(), customer.getAddrLine3(), customer.getAddrStateCd(),
                customer.getAddrCountryCd(), customer.getAddrZip(), customer.getPhoneNum1(),
                customer.getPhoneNum2(), ssn(customer.getSsn()), customer.getGovtIssuedId(),
                customer.getDobYyyyMmDd(), customer.getEftAccountId(),
                customer.getPriCardHolderInd(), customer.getFicoCreditScore());
    }

    /** 3200-CREATE-ACCOUNT-EXP-REC. */
    static MigrationExportData.Account account(AccountEntity account) {
        return new MigrationExportData.Account(account.getAcctId(), account.getActiveStatus(),
                account.getCurrBal(), account.getCreditLimit(), account.getCashCreditLimit(),
                account.getOpenDate(), account.getExpirationDate(), account.getReissueDate(),
                account.getCurrCycCredit(), account.getCurrCycDebit(), account.getAddrZip(),
                account.getGroupId());
    }

    /** 4200-CREATE-XREF-EXPORT-RECORD. */
    static MigrationExportData.Xref xref(CardXrefEntity xref) {
        return new MigrationExportData.Xref(xref.getXrefCardNum(), xref.getXrefCustId(),
                xref.getXrefAcctId());
    }

    /** 5700-CREATE-CARD-EXPORT-RECORD. */
    static MigrationExportData.Card card(CardEntity card) {
        return new MigrationExportData.Card(card.getCardNum(), card.getCardAcctId(),
                card.getCardCvvCd(), card.getCardEmbossedName(), card.getCardExpiraionDate(),
                card.getCardActiveStatus());
    }

    /** CUST-SSN is a display numeric field, stored as the digits of the sample data. */
    private static long ssn(String ssn) {
        String digits = CobolText.trim(ssn);
        return digits.isEmpty() ? 0L : Long.parseLong(digits);
    }
}
