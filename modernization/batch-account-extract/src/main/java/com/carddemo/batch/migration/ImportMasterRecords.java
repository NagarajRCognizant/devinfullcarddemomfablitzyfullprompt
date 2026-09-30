package com.carddemo.batch.migration;

import com.carddemo.cobol.CobolText;
import com.carddemo.cobol.ZonedDecimalCodec;
import com.carddemo.cobol.migration.MigrationExportData;

/**
 * The master file records CBIMPORT writes, one output layout per export record type.
 *
 * <p>The source program moves the export fields into the record of the matching copybook and
 * writes it unchanged: CUSTOMER-RECORD of CVCUS01Y (500 bytes), ACCOUNT-RECORD of CVACT01Y (300),
 * CARD-XREF-RECORD of CVACT03Y (50), TRAN-RECORD of CVTRA05Y (350) and CARD-RECORD of CVACT02Y
 * (150), plus the pipe delimited WS-ERROR-RECORD (130) for a record type the program does not
 * recognise. The trailing FILLER of each layout is part of the record, so it is written too.
 */
public final class ImportMasterRecords {

    public static final int CUSTOMER_LENGTH = 500;
    public static final int ACCOUNT_LENGTH = 300;
    public static final int XREF_LENGTH = 50;
    public static final int TRANSACTION_LENGTH = 350;
    public static final int CARD_LENGTH = 150;
    public static final int ERROR_LENGTH = 130;

    /** 2700-PROCESS-UNKNOWN-RECORD, the only message the program writes. */
    public static final String UNKNOWN_RECORD_TYPE = "Unknown record type encountered";

    private ImportMasterRecords() {
    }

    /** 2300-PROCESS-CUSTOMER-RECORD. */
    public static String customerRecord(MigrationExportData.Customer customer) {
        return CobolText.padLeftZero(Long.toString(customer.custId()), 9)
                + CobolText.padRight(customer.firstName(), 25)
                + CobolText.padRight(customer.middleName(), 25)
                + CobolText.padRight(customer.lastName(), 25)
                + CobolText.padRight(customer.addrLine1(), 50)
                + CobolText.padRight(customer.addrLine2(), 50)
                + CobolText.padRight(customer.addrLine3(), 50)
                + CobolText.padRight(customer.addrStateCd(), 2)
                + CobolText.padRight(customer.addrCountryCd(), 3)
                + CobolText.padRight(customer.addrZip(), 10)
                + CobolText.padRight(customer.phoneNum1(), 15)
                + CobolText.padRight(customer.phoneNum2(), 15)
                + CobolText.padLeftZero(Long.toString(customer.ssn()), 9)
                + CobolText.padRight(customer.govtIssuedId(), 20)
                + CobolText.padRight(customer.dobYyyyMmDd(), 10)
                + CobolText.padRight(customer.eftAccountId(), 10)
                + CobolText.padRight(customer.priCardHolderInd(), 1)
                + CobolText.padLeftZero(Integer.toString(customer.ficoCreditScore()), 3)
                + " ".repeat(168);
    }

    /** 2400-PROCESS-ACCOUNT-RECORD. */
    public static String accountRecord(MigrationExportData.Account account) {
        return CobolText.padLeftZero(Long.toString(account.acctId()), 11)
                + CobolText.padRight(account.activeStatus(), 1)
                + ZonedDecimalCodec.encode(account.currBal(), 12, 2)
                + ZonedDecimalCodec.encode(account.creditLimit(), 12, 2)
                + ZonedDecimalCodec.encode(account.cashCreditLimit(), 12, 2)
                + CobolText.padRight(account.openDate(), 10)
                + CobolText.padRight(account.expiraionDate(), 10)
                + CobolText.padRight(account.reissueDate(), 10)
                + ZonedDecimalCodec.encode(account.currCycCredit(), 12, 2)
                + ZonedDecimalCodec.encode(account.currCycDebit(), 12, 2)
                + CobolText.padRight(account.addrZip(), 10)
                + CobolText.padRight(account.groupId(), 10)
                + " ".repeat(178);
    }

    /** 2500-PROCESS-XREF-RECORD. */
    public static String xrefRecord(MigrationExportData.Xref xref) {
        return CobolText.padRight(xref.cardNum(), 16)
                + CobolText.padLeftZero(Long.toString(xref.custId()), 9)
                + CobolText.padLeftZero(Long.toString(xref.acctId()), 11)
                + " ".repeat(14);
    }

    /** 2600-PROCESS-TRAN-RECORD. */
    public static String transactionRecord(MigrationExportData.Transaction transaction) {
        return CobolText.padRight(transaction.tranId(), 16)
                + CobolText.padRight(transaction.typeCd(), 2)
                + CobolText.padLeftZero(Integer.toString(transaction.catCd()), 4)
                + CobolText.padRight(transaction.source(), 10)
                + CobolText.padRight(transaction.description(), 100)
                + ZonedDecimalCodec.encode(transaction.amount(), 11, 2)
                + CobolText.padLeftZero(Long.toString(transaction.merchantId()), 9)
                + CobolText.padRight(transaction.merchantName(), 50)
                + CobolText.padRight(transaction.merchantCity(), 50)
                + CobolText.padRight(transaction.merchantZip(), 10)
                + CobolText.padRight(transaction.cardNum(), 16)
                + CobolText.padRight(transaction.origTs(), 26)
                + CobolText.padRight(transaction.procTs(), 26)
                + " ".repeat(20);
    }

    /** 2650-PROCESS-CARD-RECORD. */
    public static String cardRecord(MigrationExportData.Card card) {
        return CobolText.padRight(card.cardNum(), 16)
                + CobolText.padLeftZero(Long.toString(card.acctId()), 11)
                + CobolText.padLeftZero(Integer.toString(card.cvvCd()), 3)
                + CobolText.padRight(card.embossedName(), 50)
                + CobolText.padRight(card.expiraionDate(), 10)
                + CobolText.padRight(card.activeStatus(), 1)
                + " ".repeat(59);
    }

    /** WS-ERROR-RECORD written by 2750-WRITE-ERROR. */
    public static String errorRecord(String timestamp, char recordType, long sequence,
                                     String message) {
        return CobolText.padRight(timestamp, 26)
                + "|"
                + recordType
                + "|"
                + CobolText.padLeftZero(Long.toString(sequence), 7)
                + "|"
                + CobolText.padRight(message, 50)
                + " ".repeat(43);
    }
}
