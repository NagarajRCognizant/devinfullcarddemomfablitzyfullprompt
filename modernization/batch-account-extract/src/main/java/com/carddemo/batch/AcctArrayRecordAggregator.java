package com.carddemo.batch;

import com.carddemo.cobol.CobolRecordBuilder;
import com.carddemo.persistence.entity.AccountEntity;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.batch.item.file.transform.LineAggregator;

/**
 * ARRYFILE record of CBACT01C: paragraphs 1400-POPUL-ARRAY-RECORD and 1450-WRITE-ARRY-RECORD,
 * LRECL 110 fixed block.
 *
 * <p>Layout: account id 9(11) followed by five occurrences of a balance pair - signed display
 * S9(10)V99 plus COMP-3 S9(10)V99, 19 bytes each - and a four byte filler: 11 + 95 + 4 = 110.
 *
 * <p>The source populates only the first three occurrences and uses hard-coded literals for four of
 * the six values, leaving occurrences four and five at the values {@code INITIALIZE} sets (zero for
 * the numeric fields). Occurrence three ignores the account entirely. All of this is preserved and
 * registered: the literals require business sign-off and the unpopulated occurrences are an
 * output layout only partly filled.
 */
public class AcctArrayRecordAggregator implements LineAggregator<AccountEntity> {

    public static final int RECORD_LENGTH = 110;

    /** Hard-coded literals of paragraph 1400 - demo values, require business sign-off. */
    public static final BigDecimal OCCURRENCE_1_DEBIT = new BigDecimal("1005.00");
    public static final BigDecimal OCCURRENCE_2_DEBIT = new BigDecimal("1525.00");
    public static final BigDecimal OCCURRENCE_3_BALANCE = new BigDecimal("-1025.00");
    public static final BigDecimal OCCURRENCE_3_DEBIT = new BigDecimal("-2500.00");

    private final Charset charset;

    public AcctArrayRecordAggregator(Charset charset) {
        this.charset = charset;
    }

    @Override
    public String aggregate(AccountEntity account) {
        CobolRecordBuilder builder = new CobolRecordBuilder(charset)
                .pic9(account.getAcctId(), 11)
                .zoned(account.getCurrBal(), 10, 2)
                .packed(OCCURRENCE_1_DEBIT, 10, 2)
                .zoned(account.getCurrBal(), 10, 2)
                .packed(OCCURRENCE_2_DEBIT, 10, 2)
                .zoned(OCCURRENCE_3_BALANCE, 10, 2)
                .packed(OCCURRENCE_3_DEBIT, 10, 2);
        // Occurrences four and five keep the values INITIALIZE ARR-ARRAY-REC left behind.
        for (int occurrence = 4; occurrence <= 5; occurrence++) {
            builder.zoned(BigDecimal.ZERO, 10, 2).packed(BigDecimal.ZERO, 10, 2);
        }
        byte[] record = builder.picX("", 4).build();
        return new String(record, StandardCharsets.ISO_8859_1);
    }
}
