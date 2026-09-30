package com.carddemo.authorization.messaging;

import com.carddemo.cobol.CobolText;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The authorization reply of copybook CCPAURLY as 6000-BUILD-RESPONSE strings it into
 * {@code W02-PUT-BUFFER}.
 *
 * <p>The six fields keep the fixed widths of the copybook and each is followed by a comma, because
 * the requesting systems read the reply positionally. The approved amount keeps the edited form of
 * {@code PIC -zzzzzzzzz9.99}, so a positive amount carries a leading blank and a negative one a
 * hyphen.
 */
public record AuthorizationReplyMessage(
        String cardNum,
        String transactionId,
        String authIdCode,
        String authRespCode,
        String authRespReason,
        BigDecimal approvedAmt) {

    /** {@code PIC -zzzzzzzzz9.99} is fourteen characters wide. */
    static final int EDITED_AMOUNT_WIDTH = 14;

    /**
     * Reads back a reply that was stored when the request was first decided.
     *
     * <p>Needed only because Kafka can deliver a request twice: the stored buffer is replayed
     * verbatim so a duplicate delivery cannot produce a different answer from the first one.
     */
    public static AuthorizationReplyMessage parse(String buffer) {
        String[] fields = (buffer == null ? "" : buffer).split(",", -1);
        if (fields.length < 6) {
            throw new IllegalArgumentException("Stored authorization reply is not a reply buffer");
        }
        String amount = CobolText.trim(fields[5]);
        return new AuthorizationReplyMessage(
                CobolText.trim(fields[0]),
                CobolText.trim(fields[1]),
                CobolText.trim(fields[2]),
                CobolText.trim(fields[3]),
                CobolText.trim(fields[4]),
                amount.isEmpty() ? BigDecimal.ZERO : new BigDecimal(amount));
    }

    /** 6000-BUILD-RESPONSE: every field is followed by a comma, including the last one. */
    public String toBuffer() {
        return CobolText.padRight(cardNum, 16) + ','
                + CobolText.padRight(transactionId, 15) + ','
                + CobolText.padRight(authIdCode, 6) + ','
                + CobolText.padRight(authRespCode, 2) + ','
                + CobolText.padRight(authRespReason, 4) + ','
                + editedAmount() + ',';
    }

    /** The {@code MOVE WS-APPROVED-AMT TO WS-APPROVED-AMT-DIS} numeric edit. */
    public String editedAmount() {
        BigDecimal value = approvedAmt == null
                ? BigDecimal.ZERO
                : approvedAmt.setScale(2, RoundingMode.HALF_UP);
        String digits = value.abs().toPlainString();
        String sign = value.signum() < 0 ? "-" : " ";
        return sign + CobolText.padLeft(digits, EDITED_AMOUNT_WIDTH - 1);
    }
}
