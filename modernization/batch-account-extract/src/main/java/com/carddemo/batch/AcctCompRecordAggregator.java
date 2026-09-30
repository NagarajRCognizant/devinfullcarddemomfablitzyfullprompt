package com.carddemo.batch;

import com.carddemo.cobol.CobdatftDateFormatter;
import com.carddemo.cobol.CobolRecordBuilder;
import com.carddemo.cobol.CobolText;
import com.carddemo.persistence.entity.AccountEntity;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.batch.item.file.transform.LineAggregator;

/**
 * OUTFILE record of CBACT01C: paragraphs 1300-POPUL-ACCT-RECORD and 1350-WRITE-ACCT-RECORD,
 * LRECL 107 fixed block.
 *
 * <p>Layout: account id 9(11), status X(1), current balance, credit limit and cash credit limit as
 * signed display S9(10)V99, the three dates X(10), current cycle credit as signed display and
 * current cycle debit as COMP-3, then group id X(10) - 11+1+12+12+12+10+10+10+12+7+10 = 107 bytes.
 *
 * <p>Two source behaviours are preserved rather than corrected, both registered as REVIEW REQUIRED.
 * The current cycle debit field is only populated when the stored value is zero, in which case the
 * hard-coded literal 2525.00 is written; for any other value the output field keeps whatever the
 * record area held from the previous record, and for the first record of the run that content is
 * uninitialised. The reissue date is passed through COBDATFT with type '2' in and '2' out, whose
 * eight digit result is then truncated into a ten byte field.
 *
 * <p>The aggregated line is a byte-for-byte image of the record mapped through ISO-8859-1, so the
 * packed field survives the writer unchanged while restartability of the file writer is retained.
 */
public class AcctCompRecordAggregator implements LineAggregator<AccountEntity> {

    /** Hard-coded default of paragraph 1300 - demo value, requires business sign-off. */
    public static final BigDecimal ZERO_DEBIT_DEFAULT = new BigDecimal("2525.00");

    public static final int RECORD_LENGTH = 107;
    private static final int DEBIT_FIELD_BYTES = 7;

    private final Charset charset;

    /** The record area of the FD, which the source never re-initialises between records. */
    private byte[] currentCycleDebitField;

    public AcctCompRecordAggregator(Charset charset) {
        this.charset = charset;
        this.currentCycleDebitField = " ".repeat(DEBIT_FIELD_BYTES).getBytes(charset);
    }

    @Override
    public String aggregate(AccountEntity account) {
        String reissueDate = reissueDate(account);
        byte[] record = new CobolRecordBuilder(charset)
                .pic9(account.getAcctId(), 11)
                .picX(account.getActiveStatus(), 1)
                .zoned(account.getCurrBal(), 10, 2)
                .zoned(account.getCreditLimit(), 10, 2)
                .zoned(account.getCashCreditLimit(), 10, 2)
                .picX(account.getOpenDate(), 10)
                .picX(account.getExpirationDate(), 10)
                .picX(reissueDate, 10)
                .zoned(account.getCurrCycCredit(), 10, 2)
                .raw(currentCycleDebitField(account))
                .picX(account.getGroupId(), 10)
                .build();
        return new String(record, StandardCharsets.ISO_8859_1);
    }

    private byte[] currentCycleDebitField(AccountEntity account) {
        BigDecimal debit = account.getCurrCycDebit();
        if (debit != null && debit.signum() == 0) {
            currentCycleDebitField = new CobolRecordBuilder(charset)
                    .packed(ZERO_DEBIT_DEFAULT, 10, 2)
                    .build();
        }
        return currentCycleDebitField;
    }

    /** {@code CALL 'COBDATFT'} with CODATECN-TYPE '2' and CODATECN-OUTTYPE '2'. */
    private String reissueDate(AccountEntity account) {
        CobdatftDateFormatter.Result result = CobdatftDateFormatter.convert(
                '2', '2', CobolText.padRight(account.getReissueDate(), 20), " ".repeat(20));
        return result.outputDate().substring(0, 10);
    }
}
