package com.carddemo.batch;

import com.carddemo.cobol.CobolRecordBuilder;
import com.carddemo.cobol.CobolText;
import com.carddemo.persistence.entity.AccountEntity;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.batch.item.file.transform.LineAggregator;

/**
 * VBRCFILE records of CBACT01C: paragraphs 1500-POPUL-VBRC-RECORD, 1550-WRITE-VB1-RECORD and
 * 1575-WRITE-VB2-RECORD, LRECL 84 variable block.
 *
 * <p>Every account produces two records of different lengths, written into an X(80) area whose
 * length is set through WS-RECD-LEN: a 12 byte record holding the account id and the active status,
 * and a 39 byte record holding the account id, the current balance, the credit limit and the four
 * digit year of the reissue date. Because one input item yields two output records, both are
 * aggregated into one write, each prefixed with the four byte record descriptor word that a
 * variable blocked data set carries on disk (length including the descriptor, then two zero bytes).
 *
 * <p>Only the first WS-RECD-LEN bytes of the 80 byte area are moved, so the remainder of the area
 * keeps its previous content; since neither record is shorter than what precedes it in the same
 * position and only the moved prefix is written, the trailing content never reaches the file.
 */
public class AcctVbRecordAggregator implements LineAggregator<AccountEntity> {

    public static final int MAX_RECORD_LENGTH = 84;
    public static final int VB1_LENGTH = 12;
    public static final int VB2_LENGTH = 39;

    private final Charset charset;

    public AcctVbRecordAggregator(Charset charset) {
        this.charset = charset;
    }

    @Override
    public String aggregate(AccountEntity account) {
        byte[] first = new CobolRecordBuilder(charset)
                .pic9(account.getAcctId(), 11)
                .picX(account.getActiveStatus(), 1)
                .build();
        byte[] second = new CobolRecordBuilder(charset)
                .pic9(account.getAcctId(), 11)
                .zoned(account.getCurrBal(), 10, 2)
                .zoned(account.getCreditLimit(), 10, 2)
                .picX(reissueYear(account), 4)
                .build();
        return descriptor(first) + new String(first, StandardCharsets.ISO_8859_1)
                + descriptor(second) + new String(second, StandardCharsets.ISO_8859_1);
    }

    /** WS-ACCT-REISSUE-YYYY, the first four bytes of the stored reissue date. */
    private static String reissueYear(AccountEntity account) {
        return CobolText.padRight(account.getReissueDate(), 10).substring(0, 4);
    }

    private static String descriptor(byte[] record) {
        int length = record.length + 4;
        return new String(new byte[] {(byte) (length >> 8), (byte) length, 0, 0},
                StandardCharsets.ISO_8859_1);
    }
}
