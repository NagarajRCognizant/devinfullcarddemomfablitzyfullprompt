package com.carddemo.authorization.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * The wire contracts of copybooks CCPAURQY and CCPAURLY: the comma delimited request COPAUA0C
 * UNSTRINGs and the fixed width reply 6000-BUILD-RESPONSE strings together.
 */
class AuthorizationMessageLayoutTest {

    private static final String REQUEST = String.join(",",
            "240301", "134530", "4111111111111111", "01", "1225", "0100", "POS", "000000",
            "250.75", "5411", "840", "05", "MERCH000000001", "ACME STORE", "DALLAS", "TX",
            "75001", "TRAN0000000001");

    @Test
    void theRequestIsParsedFieldByField() {
        AuthorizationRequestMessage request = AuthorizationRequestMessage.parse(REQUEST);

        assertThat(request.authDate()).isEqualTo("240301");
        assertThat(request.authTime()).isEqualTo("134530");
        assertThat(request.cardNum()).isEqualTo("4111111111111111");
        assertThat(request.transactionAmt()).isEqualByComparingTo("250.75");
        assertThat(request.merchantCategoryCode()).isEqualTo("5411");
        assertThat(request.transactionId()).isEqualTo("TRAN0000000001");
    }

    /** The idempotency key that replaces the once only delivery of the MQ read. */
    @Test
    void theRequestIdIsTheCardTheStampedTimestampAndTheTransactionId() {
        assertThat(AuthorizationRequestMessage.parse(REQUEST).requestId())
                .isEqualTo("4111111111111111|240301134530|TRAN0000000001");
    }

    @Test
    void theCardNumberIsMaskedForLogging() {
        assertThat(AuthorizationRequestMessage.parse(REQUEST).maskedCardNumber())
                .isEqualTo("************1111");
    }

    @Test
    void anEmptyOrOverlongOrShortMessageIsMalformed() {
        assertThatThrownBy(() -> AuthorizationRequestMessage.parse("   "))
                .isInstanceOf(MalformedAuthorizationRequestException.class);
        assertThatThrownBy(() -> AuthorizationRequestMessage.parse("240301,134530,4111"))
                .isInstanceOf(MalformedAuthorizationRequestException.class);
        assertThatThrownBy(() -> AuthorizationRequestMessage.parse("x".repeat(501)))
                .isInstanceOf(MalformedAuthorizationRequestException.class);
    }

    @Test
    void anAmountThatNumvalCouldNotReadIsMalformed() {
        assertThatThrownBy(() -> AuthorizationRequestMessage.parse(REQUEST.replace("250.75", "ABC")))
                .isInstanceOf(MalformedAuthorizationRequestException.class);
        assertThatThrownBy(() -> AuthorizationRequestMessage.parse(REQUEST.replace("250.75", "")))
                .isInstanceOf(MalformedAuthorizationRequestException.class);
    }

    /** The six fields of CCPAURLY, each padded to its PIC width and each followed by a comma. */
    @Test
    void theReplyBufferKeepsTheCopybookWidths() {
        AuthorizationReplyMessage reply = new AuthorizationReplyMessage("4111111111111111",
                "TRAN0000000001", "134530", "00", "0000", new BigDecimal("250.75"));

        String buffer = reply.toBuffer();

        assertThat(buffer).isEqualTo("4111111111111111,TRAN0000000001 ,134530,00,0000,"
                + "        250.75,");
        assertThat(reply.editedAmount()).hasSize(14);
    }

    @Test
    void aNegativeApprovedAmountCarriesTheHyphenOfTheEditedPicture() {
        AuthorizationReplyMessage reply = new AuthorizationReplyMessage("4111111111111111",
                "TRAN1", "134530", "05", "4100", new BigDecimal("-1.50"));

        assertThat(reply.editedAmount()).isEqualTo("-         1.50");
    }

    /** A stored reply is replayed verbatim when Kafka delivers the same request twice. */
    @Test
    void aStoredReplyIsReadBackFromItsBuffer() {
        AuthorizationReplyMessage original = new AuthorizationReplyMessage("4111111111111111",
                "TRAN0000000001", "134530", "00", "0000", new BigDecimal("250.75"));

        AuthorizationReplyMessage parsed = AuthorizationReplyMessage.parse(original.toBuffer());

        assertThat(parsed.cardNum()).isEqualTo(original.cardNum());
        assertThat(parsed.transactionId()).isEqualTo(original.transactionId());
        assertThat(parsed.authRespCode()).isEqualTo("00");
        assertThat(parsed.approvedAmt()).isEqualByComparingTo("250.75");
    }

    @Test
    void aBufferThatIsNotAReplyIsRejected() {
        assertThatThrownBy(() -> AuthorizationReplyMessage.parse("1,2,3"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
