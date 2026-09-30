package com.carddemo.cobol.migration;

import com.carddemo.cobol.CobolRecordBuilder;
import com.carddemo.cobol.CobolRecordReader;
import java.nio.charset.Charset;

/**
 * Encodes and decodes the 500 byte branch migration export record of {@code app/cpy/CVEXPORT.cpy}.
 *
 * <p>CBEXPORT writes the record and CBIMPORT reads it back, so one class owns both directions and
 * the offsets are stated once. The record starts with a nine field header shared by every record
 * type ({@code EXPORT-REC-TYPE}, {@code EXPORT-TIMESTAMP}, the COMP sequence number, the branch id
 * and the region code) followed by the 460 byte redefined data area.
 */
public final class MigrationExportRecord {

    public static final int RECORD_LENGTH = 500;
    public static final int DATA_OFFSET = 40;

    public static final char TYPE_CUSTOMER = 'C';
    public static final char TYPE_ACCOUNT = 'A';
    public static final char TYPE_XREF = 'X';
    public static final char TYPE_TRANSACTION = 'T';
    public static final char TYPE_CARD = 'D';

    private final byte[] record;
    private final Charset charset;
    private final CobolRecordReader reader;

    public MigrationExportRecord(byte[] record, Charset charset) {
        if (record.length != RECORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Export record must be " + RECORD_LENGTH + " bytes, was " + record.length);
        }
        this.record = record;
        this.charset = charset;
        this.reader = new CobolRecordReader(record, charset);
    }

    public byte[] bytes() {
        return record.clone();
    }

    public Charset charset() {
        return charset;
    }

    public char recordType() {
        return new String(record, 0, 1, charset).charAt(0);
    }

    public String timestamp() {
        return reader.picX(1, 26);
    }

    public long sequenceNumber() {
        return reader.binary(27, 9, 0).longValueExact();
    }

    public String branchId() {
        return reader.picX(31, 4);
    }

    public String regionCode() {
        return reader.picX(35, 5);
    }

    /** {@code EXPORT-CUSTOMER-DATA}. */
    public MigrationExportData.Customer customer() {
        return new MigrationExportData.Customer(
                reader.binary(DATA_OFFSET, 9, 0).longValueExact(),
                reader.picX(DATA_OFFSET + 4, 25),
                reader.picX(DATA_OFFSET + 29, 25),
                reader.picX(DATA_OFFSET + 54, 25),
                reader.picX(DATA_OFFSET + 79, 50),
                reader.picX(DATA_OFFSET + 129, 50),
                reader.picX(DATA_OFFSET + 179, 50),
                reader.picX(DATA_OFFSET + 229, 2),
                reader.picX(DATA_OFFSET + 231, 3),
                reader.picX(DATA_OFFSET + 234, 10),
                reader.picX(DATA_OFFSET + 244, 15),
                reader.picX(DATA_OFFSET + 259, 15),
                reader.pic9(DATA_OFFSET + 274, 9),
                reader.picX(DATA_OFFSET + 283, 20),
                reader.picX(DATA_OFFSET + 303, 10),
                reader.picX(DATA_OFFSET + 313, 10),
                reader.picX(DATA_OFFSET + 323, 1),
                reader.packed(DATA_OFFSET + 324, 3, 0).intValueExact());
    }

    /** {@code EXPORT-ACCOUNT-DATA}. */
    public MigrationExportData.Account account() {
        return new MigrationExportData.Account(
                reader.pic9(DATA_OFFSET, 11),
                reader.picX(DATA_OFFSET + 11, 1),
                reader.packed(DATA_OFFSET + 12, 10, 2),
                reader.zoned(DATA_OFFSET + 19, 10, 2),
                reader.packed(DATA_OFFSET + 31, 10, 2),
                reader.picX(DATA_OFFSET + 38, 10),
                reader.picX(DATA_OFFSET + 48, 10),
                reader.picX(DATA_OFFSET + 58, 10),
                reader.zoned(DATA_OFFSET + 68, 10, 2),
                reader.binary(DATA_OFFSET + 80, 10, 2),
                reader.picX(DATA_OFFSET + 88, 10),
                reader.picX(DATA_OFFSET + 98, 10));
    }

    /** {@code EXPORT-CARD-XREF-DATA}. */
    public MigrationExportData.Xref xref() {
        return new MigrationExportData.Xref(
                reader.picX(DATA_OFFSET, 16),
                reader.pic9(DATA_OFFSET + 16, 9),
                reader.binary(DATA_OFFSET + 25, 11, 0).longValueExact());
    }

    /** {@code EXPORT-TRANSACTION-DATA}. */
    public MigrationExportData.Transaction transaction() {
        return new MigrationExportData.Transaction(
                reader.picX(DATA_OFFSET, 16),
                reader.picX(DATA_OFFSET + 16, 2),
                (int) reader.pic9(DATA_OFFSET + 18, 4),
                reader.picX(DATA_OFFSET + 22, 10),
                reader.picX(DATA_OFFSET + 32, 100),
                reader.packed(DATA_OFFSET + 132, 9, 2),
                reader.binary(DATA_OFFSET + 138, 9, 0).longValueExact(),
                reader.picX(DATA_OFFSET + 142, 50),
                reader.picX(DATA_OFFSET + 192, 50),
                reader.picX(DATA_OFFSET + 242, 10),
                reader.picX(DATA_OFFSET + 252, 16),
                reader.picX(DATA_OFFSET + 268, 26),
                reader.picX(DATA_OFFSET + 294, 26));
    }

    /** {@code EXPORT-CARD-DATA}. */
    public MigrationExportData.Card card() {
        return new MigrationExportData.Card(
                reader.picX(DATA_OFFSET, 16),
                reader.binary(DATA_OFFSET + 16, 11, 0).longValueExact(),
                reader.binary(DATA_OFFSET + 24, 3, 0).intValueExact(),
                reader.picX(DATA_OFFSET + 26, 50),
                reader.picX(DATA_OFFSET + 76, 10),
                reader.picX(DATA_OFFSET + 86, 1));
    }

    /** {@code 2200-CREATE-CUSTOMER-EXP-REC}. */
    public static byte[] encode(MigrationExportHeader header, MigrationExportData.Customer customer,
                               Charset charset) {
        CobolRecordBuilder builder = header(header, TYPE_CUSTOMER, charset)
                .binary(java.math.BigDecimal.valueOf(customer.custId()), 9, 0)
                .picX(customer.firstName(), 25)
                .picX(customer.middleName(), 25)
                .picX(customer.lastName(), 25)
                .picX(customer.addrLine1(), 50)
                .picX(customer.addrLine2(), 50)
                .picX(customer.addrLine3(), 50)
                .picX(customer.addrStateCd(), 2)
                .picX(customer.addrCountryCd(), 3)
                .picX(customer.addrZip(), 10)
                .picX(customer.phoneNum1(), 15)
                .picX(customer.phoneNum2(), 15)
                .pic9(customer.ssn(), 9)
                .picX(customer.govtIssuedId(), 20)
                .picX(customer.dobYyyyMmDd(), 10)
                .picX(customer.eftAccountId(), 10)
                .picX(customer.priCardHolderInd(), 1)
                .packed(java.math.BigDecimal.valueOf(customer.ficoCreditScore()), 3, 0);
        return pad(builder, charset);
    }

    /** {@code 3200-CREATE-ACCOUNT-EXP-REC}. */
    public static byte[] encode(MigrationExportHeader header, MigrationExportData.Account account,
                               Charset charset) {
        CobolRecordBuilder builder = header(header, TYPE_ACCOUNT, charset)
                .pic9(account.acctId(), 11)
                .picX(account.activeStatus(), 1)
                .packed(account.currBal(), 10, 2)
                .zoned(account.creditLimit(), 10, 2)
                .packed(account.cashCreditLimit(), 10, 2)
                .picX(account.openDate(), 10)
                .picX(account.expiraionDate(), 10)
                .picX(account.reissueDate(), 10)
                .zoned(account.currCycCredit(), 10, 2)
                .binary(account.currCycDebit(), 10, 2)
                .picX(account.addrZip(), 10)
                .picX(account.groupId(), 10);
        return pad(builder, charset);
    }

    /** {@code 4200-CREATE-XREF-EXPORT-RECORD}. */
    public static byte[] encode(MigrationExportHeader header, MigrationExportData.Xref xref,
                               Charset charset) {
        CobolRecordBuilder builder = header(header, TYPE_XREF, charset)
                .picX(xref.cardNum(), 16)
                .pic9(xref.custId(), 9)
                .binary(java.math.BigDecimal.valueOf(xref.acctId()), 11, 0);
        return pad(builder, charset);
    }

    /** {@code 5200-CREATE-TRAN-EXP-REC}. */
    public static byte[] encode(MigrationExportHeader header,
                               MigrationExportData.Transaction transaction, Charset charset) {
        CobolRecordBuilder builder = header(header, TYPE_TRANSACTION, charset)
                .picX(transaction.tranId(), 16)
                .picX(transaction.typeCd(), 2)
                .pic9(transaction.catCd(), 4)
                .picX(transaction.source(), 10)
                .picX(transaction.description(), 100)
                .packed(transaction.amount(), 9, 2)
                .binary(java.math.BigDecimal.valueOf(transaction.merchantId()), 9, 0)
                .picX(transaction.merchantName(), 50)
                .picX(transaction.merchantCity(), 50)
                .picX(transaction.merchantZip(), 10)
                .picX(transaction.cardNum(), 16)
                .picX(transaction.origTs(), 26)
                .picX(transaction.procTs(), 26);
        return pad(builder, charset);
    }

    /** {@code 5700-CREATE-CARD-EXPORT-RECORD}. */
    public static byte[] encode(MigrationExportHeader header, MigrationExportData.Card card,
                               Charset charset) {
        CobolRecordBuilder builder = header(header, TYPE_CARD, charset)
                .picX(card.cardNum(), 16)
                .binary(java.math.BigDecimal.valueOf(card.acctId()), 11, 0)
                .binary(java.math.BigDecimal.valueOf(card.cvvCd()), 3, 0)
                .picX(card.embossedName(), 50)
                .picX(card.expiraionDate(), 10)
                .picX(card.activeStatus(), 1);
        return pad(builder, charset);
    }

    private static CobolRecordBuilder header(MigrationExportHeader header, char recordType,
                                             Charset charset) {
        return new CobolRecordBuilder(charset)
                .picX(String.valueOf(recordType), 1)
                .picX(header.timestamp(), 26)
                .binary(java.math.BigDecimal.valueOf(header.sequenceNumber()), 9, 0)
                .picX(header.branchId(), 4)
                .picX(header.regionCode(), 5);
    }

    /** The trailing FILLER of every layout: INITIALIZE leaves the unused data area spaces. */
    private static byte[] pad(CobolRecordBuilder builder, Charset charset) {
        byte[] written = builder.build();
        if (written.length > RECORD_LENGTH) {
            throw new IllegalStateException("Export record overflowed: " + written.length);
        }
        byte[] record = new byte[RECORD_LENGTH];
        System.arraycopy(written, 0, record, 0, written.length);
        byte space = " ".getBytes(charset)[0];
        java.util.Arrays.fill(record, written.length, RECORD_LENGTH, space);
        return record;
    }
}
