package com.carddemo.authorization.messaging;

import com.carddemo.cobol.CobolText;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The authorization request message of copybook CCPAURQY as COPAUA0C receives it on
 * {@code AWS.M2.CARDDEMO.PAUTH.REQUEST}.
 *
 * <p>The message stays the comma delimited text the source defines instead of becoming JSON: the
 * request is produced by systems outside this modernization scope, so the wire contract is part of
 * the preserved behaviour. The record carries the fields already trimmed, plus the raw text so the
 * request log can keep exactly what arrived.
 */
public record AuthorizationRequestMessage(
        String authDate,
        String authTime,
        String cardNum,
        String authType,
        String cardExpiryDate,
        String messageType,
        String messageSource,
        String processingCode,
        BigDecimal transactionAmt,
        String merchantCategoryCode,
        String acqrCountryCode,
        String posEntryMode,
        String merchantId,
        String merchantName,
        String merchantCity,
        String merchantState,
        String merchantZip,
        String transactionId) {

    /** {@code 01 W01-GET-BUFFER PIC X(500)} - a longer message is a malformed request. */
    public static final int MAX_BUFFER_LENGTH = 500;

    private static final int FIELD_COUNT = 18;

    /**
     * 2100-PROCESS-REQUEST: {@code UNSTRING ... DELIMITED BY ','} followed by
     * {@code FUNCTION NUMVAL} on the amount.
     *
     * <p>UNSTRING assigns the fields it finds and leaves the rest as they were initialised, so a
     * short message is not an error in the source; it is rejected here because a truncated
     * authorization request cannot be decided correctly and the Kafka listener has a dead letter
     * path the MQ program did not have.
     */
    public static AuthorizationRequestMessage parse(String buffer) {
        if (buffer == null || buffer.isBlank()) {
            throw new MalformedAuthorizationRequestException("Authorization request message is empty");
        }
        if (buffer.length() > MAX_BUFFER_LENGTH) {
            throw new MalformedAuthorizationRequestException(
                    "Authorization request message exceeds W01-GET-BUFFER length of " + MAX_BUFFER_LENGTH);
        }
        String[] fields = buffer.split(",", -1);
        if (fields.length < FIELD_COUNT) {
            throw new MalformedAuthorizationRequestException(
                    "Authorization request message has " + fields.length
                            + " comma delimited fields, CCPAURQY defines " + FIELD_COUNT);
        }
        return new AuthorizationRequestMessage(
                CobolText.trim(fields[0]),
                CobolText.trim(fields[1]),
                CobolText.trim(fields[2]),
                CobolText.trim(fields[3]),
                CobolText.trim(fields[4]),
                CobolText.trim(fields[5]),
                CobolText.trim(fields[6]),
                CobolText.trim(fields[7]),
                numval(fields[8]),
                CobolText.trim(fields[9]),
                CobolText.trim(fields[10]),
                CobolText.trim(fields[11]),
                CobolText.trim(fields[12]),
                CobolText.trim(fields[13]),
                CobolText.trim(fields[14]),
                CobolText.trim(fields[15]),
                CobolText.trim(fields[16]),
                CobolText.trim(fields[17]));
    }

    /**
     * {@code FUNCTION NUMVAL} of the transaction amount field, stored into a
     * {@code PIC +9(10).99} item: the value is scaled to two decimals and anything NUMVAL could
     * not have read is a malformed request rather than a silent zero.
     */
    private static BigDecimal numval(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new MalformedAuthorizationRequestException("Transaction amount is empty");
        }
        try {
            return new BigDecimal(text).setScale(2, RoundingMode.DOWN);
        } catch (NumberFormatException e) {
            throw new MalformedAuthorizationRequestException("Transaction amount is not numeric: " + text);
        }
    }

    /**
     * The idempotency key of the request: the card number, the authorization date and time the
     * originating system stamped, and the transaction id. Section 8.5.1 of the delta register
     * records why this replaces the once only delivery of the MQ read.
     */
    public String requestId() {
        return cardNum + "|" + authDate + authTime + "|" + transactionId;
    }

    /**
     * The card number with all but the last four digits masked.
     *
     * <p>The source wrote whole card numbers into the CICS error log. Logs in the target are
     * aggregated outside the service boundary, so the number is masked wherever it is logged.
     */
    public String maskedCardNumber() {
        if (cardNum == null || cardNum.length() <= 4) {
            return "****";
        }
        return "*".repeat(cardNum.length() - 4) + cardNum.substring(cardNum.length() - 4);
    }
}
