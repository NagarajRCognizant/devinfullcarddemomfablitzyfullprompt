package com.carddemo.batch.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** The print lines of the daily transaction report (CBTRN03C, copybook CVTRA07Y). */
class ReportLinesTest {

    @Test
    void everyPrintLineIsWrittenAtTheReportRecordLength() {
        assertThat(ReportLines.nameHeader("2022-01-01", "2022-01-31"))
                .hasSize(ReportLines.LINE_LENGTH);
        assertThat(ReportLines.columnHeader()).hasSize(ReportLines.LINE_LENGTH);
        assertThat(ReportLines.blank().trim()).isEmpty();
        assertThat(ReportLines.blank()).hasSize(ReportLines.LINE_LENGTH);
        assertThat(ReportLines.RULE).hasSize(ReportLines.LINE_LENGTH);
    }

    @Test
    void theDetailLineEditsTheKeysAndTheAmount() {
        String detail = ReportLines.detail("T0000000000000001", 11L, "01", "PURCHASE", 5,
                "GROCERY", "POS", new BigDecimal("1234.56"));

        assertThat(detail).hasSize(ReportLines.LINE_LENGTH);
        assertThat(detail).startsWith("T000000000000000");
        assertThat(detail).contains("00000000011");
        assertThat(detail).contains("01-PURCHASE");
        assertThat(detail).contains("0005-GROCERY");
        assertThat(detail).contains("1,234.56");
    }

    /** An unset account, category or description prints as the blank or zero field of the MOVE. */
    @Test
    void theDetailLinePrintsTheEmptyFieldsOfAnIncompleteRecord() {
        String detail = ReportLines.detail(null, null, null, null, null, null, null, null);

        assertThat(detail).hasSize(ReportLines.LINE_LENGTH);
        assertThat(detail).contains("0000-");
        assertThat(detail.replace(" ", "")).isEqualTo("-0000-");
    }

    /**
     * {@code PIC -ZZZ,ZZZ,ZZZ.ZZ} and {@code PIC +ZZZ,ZZZ,ZZZ.ZZ}: a blank field for zero, the
     * sign in the leading position and a printed plus only on the total lines.
     */
    @Test
    void theEditedAmountsFollowTheirPictureClauses() {
        assertThat(ReportLines.signedAmount(BigDecimal.ZERO)).isBlank();
        assertThat(ReportLines.signedAmount(null)).isBlank();
        assertThat(ReportLines.signedAmount(new BigDecimal("-2.50")).replace(" ", ""))
                .isEqualTo("-2.50");
        assertThat(ReportLines.signedAmount(new BigDecimal("2.50")).trim()).isEqualTo("2.50");
        assertThat(ReportLines.positiveSignedAmount(new BigDecimal("2.50")).replace(" ", ""))
                .isEqualTo("+2.50");
        assertThat(ReportLines.positiveSignedAmount(new BigDecimal("-2.50")).replace(" ", ""))
                .isEqualTo("-2.50");
        assertThat(ReportLines.signedAmount(new BigDecimal("999999.99")))
                .isEqualTo("    999,999.99");
    }

    @Test
    void theTotalLinesCarryTheirLeaderAndTheirEditedTotal() {
        assertThat(ReportLines.pageTotal(new BigDecimal("10.00")))
                .hasSize(ReportLines.LINE_LENGTH)
                .startsWith("Page Total")
                .contains("+")
                .contains("10.00");
        assertThat(ReportLines.accountTotal(new BigDecimal("10.00")))
                .startsWith("Account Total")
                .contains("10.00");
        assertThat(ReportLines.grandTotal(new BigDecimal("10.00")))
                .startsWith("Grand Total")
                .contains("10.00");
    }
}
