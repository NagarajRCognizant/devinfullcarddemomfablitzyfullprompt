package com.carddemo.batch.transaction;

import com.carddemo.cobol.ZonedDecimalCodec;
import java.math.BigDecimal;

/** Builds DALYTRAN records at the source layout so tests can drive the jobs with real input. */
final class DailyTransactionFixtures {

    private DailyTransactionFixtures() {
    }

    static String record(String id, String cardNumber, String amount, String originalTimestamp) {
        StringBuilder record = new StringBuilder();
        append(record, id, 16);
        append(record, "01", 2);
        append(record, "0001", 4);
        append(record, "POS TERM", 10);
        append(record, "Purchase", 100);
        record.append(ZonedDecimalCodec.encode(new BigDecimal(amount), 11, 2));
        append(record, "000000123", 9);
        append(record, "Merchant", 50);
        append(record, "City", 50);
        append(record, "12345", 10);
        append(record, cardNumber, 16);
        append(record, originalTimestamp, 26);
        append(record, "", 26);
        append(record, "", 20);
        return record.toString();
    }

    private static void append(StringBuilder record, String value, int width) {
        record.append(value.length() >= width ? value.substring(0, width)
                : value + " ".repeat(width - value.length()));
    }
}
