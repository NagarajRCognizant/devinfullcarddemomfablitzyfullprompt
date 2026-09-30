package com.carddemo.transaction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The messages the screens displayed, including the spacing the STRING statements produced.
 *
 * <p>The texts are observable behaviour, so they are asserted verbatim rather than derived.
 */
class TransactionMessagesTest {

    @Test
    void theAddSuccessMessageCarriesTheDoubledSpaceOfTheStringStatement() {
        assertThat(TransactionMessages.transactionAdded("0000000000000301"))
                .isEqualTo("Transaction added successfully.  Your Tran ID is 0000000000000301.");
    }

    @Test
    void thePaymentSuccessMessageCarriesTheSameDoubledSpace() {
        assertThat(TransactionMessages.paymentSuccessful("0000000000000302"))
                .isEqualTo("Payment successful.  Your Transaction ID is 0000000000000302.");
    }

    @Test
    void theReportMessagesAreBuiltAroundTheReportName() {
        assertThat(TransactionMessages.confirmPrint("Monthly"))
                .isEqualTo("Please confirm to print the Monthly report...");
        assertThat(TransactionMessages.reportSubmitted("Yearly"))
                .isEqualTo("Yearly report submitted for printing ...");
        assertThat(TransactionMessages.invalidPrintConfirmation("X"))
                .isEqualTo("\"X\" is not a valid value to confirm...");
    }
}
