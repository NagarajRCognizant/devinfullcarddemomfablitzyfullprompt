package com.carddemo.batch.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** The edited fields of the STATEMENT-LINES structure of CBSTM03A. */
class StatementLinesTest {

    @Test
    void everyTextLineIsWrittenAtTheStatementRecordLength() {
        assertThat(StatementLines.START_OF_STATEMENT).hasSize(StatementLines.TEXT_LINE_LENGTH);
        assertThat(StatementLines.END_OF_STATEMENT).hasSize(StatementLines.TEXT_LINE_LENGTH);
        assertThat(StatementLines.RULE).hasSize(StatementLines.TEXT_LINE_LENGTH);
        assertThat(StatementLines.BASIC_DETAILS).hasSize(StatementLines.TEXT_LINE_LENGTH);
        assertThat(StatementLines.TRANSACTION_SUMMARY).hasSize(StatementLines.TEXT_LINE_LENGTH);
        assertThat(StatementLines.accountIdLine(1L)).hasSize(StatementLines.TEXT_LINE_LENGTH);
    }

    /** The STRING of ST-LINE1 stops each name part at its first space. */
    @Test
    void theNameLineTakesEachNamePartUpToItsFirstSpace() {
        assertThat(StatementLines.nameLine("John ", "Q", "Doe Jr"))
                .startsWith("John Q Doe ");
    }

    @Test
    void anAbsentNamePartLeavesItsPositionEmpty() {
        assertThat(StatementLines.nameLine(null, null, null).trim()).isEmpty();
        assertThat(StatementLines.upToSpace(null)).isEmpty();
        assertThat(StatementLines.upToDoubleSpace(null)).isEmpty();
        assertThat(StatementLines.upToDoubleSpace("Seattle  WA")).isEqualTo("Seattle");
        assertThat(StatementLines.upToDoubleSpace("Seattle")).isEqualTo("Seattle");
    }

    /** A value longer than its field is truncated by the MOVE, not wrapped. */
    @Test
    void anAddressLongerThanItsFieldIsTruncated() {
        String line = StatementLines.addressLine("A".repeat(60));
        assertThat(line).startsWith("A".repeat(50));
        assertThat(line.substring(50).trim()).isEmpty();
    }

    @Test
    void theCityLineTakesEachPartUpToItsFirstSpace() {
        assertThat(StatementLines.cityStateZipLine("New York NY", "NY", "USA", "10001 "))
                .startsWith("New NY USA 10001");
    }

    /** ST-ACCT-ID and ST-FICO-SCORE are blank when the record carried no value. */
    @Test
    void theAccountAndFicoFieldsAreBlankWithoutAValue() {
        assertThat(StatementLines.accountIdField(null).trim()).isEmpty();
        assertThat(StatementLines.ficoField(null).trim()).isEmpty();
        assertThat(StatementLines.accountIdField(1L)).startsWith("00000000001");
        assertThat(StatementLines.ficoField(7)).startsWith("007");
    }

    /** {@code PIC 9(9).99-}: unsuppressed digits and a trailing minus only when negative. */
    @Test
    void theUnsuppressedAmountKeepsItsLeadingZerosAndItsTrailingSign() {
        assertThat(StatementLines.unsuppressedAmount(new BigDecimal("1000.00")))
                .isEqualTo("000001000.00 ");
        assertThat(StatementLines.unsuppressedAmount(new BigDecimal("-12.34")))
                .isEqualTo("000000012.34-");
        assertThat(StatementLines.unsuppressedAmount(null)).isEqualTo("000000000.00 ");
    }

    /** A value beyond nine digits loses its high order digits as the MOVE does. */
    @Test
    void anAmountBeyondTheFieldLosesItsHighOrderDigits() {
        assertThat(StatementLines.unsuppressedAmount(new BigDecimal("12345678901.23")))
                .isEqualTo("345678901.23 ");
    }

    /** {@code PIC Z(9).99-}: the leading zeros print as spaces, the units digit always prints. */
    @Test
    void theSuppressedAmountBlanksItsLeadingZeros() {
        assertThat(StatementLines.suppressedAmount(new BigDecimal("12.34")))
                .isEqualTo("       12.34 ");
        assertThat(StatementLines.suppressedAmount(BigDecimal.ZERO))
                .isEqualTo("        0.00 ");
        assertThat(StatementLines.suppressedAmount(new BigDecimal("-5.00")))
                .isEqualTo("        5.00-");
    }

    @Test
    void theTransactionAndTotalLinesAreWrittenAtTheRecordLength() {
        assertThat(StatementLines.transactionLine("T1", "COFFEE", new BigDecimal("3.50")))
                .hasSize(StatementLines.TEXT_LINE_LENGTH)
                .contains("T1")
                .contains("COFFEE")
                .contains("3.50");
        assertThat(StatementLines.totalLine(new BigDecimal("-1.00")))
                .hasSize(StatementLines.TEXT_LINE_LENGTH)
                .startsWith("Total EXP:")
                .contains("1.00-");
    }

    @Test
    void anHtmlLineIsWrittenAtTheHtmlRecordLength() {
        assertThat(StatementLines.html("<html>")).hasSize(StatementLines.HTML_LINE_LENGTH);
        assertThat(StatementLines.html("<p>" + "x".repeat(200)))
                .hasSize(StatementLines.HTML_LINE_LENGTH);
    }
}
