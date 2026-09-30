package com.carddemo.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** CSUTLDTC feedback codes, reproduced locally instead of calling CEEDAYS. */
class DateValidationServiceTest {

    private final DateValidationService service = new DateValidationService();

    @Test
    void aValidDatePassesWithSeverityZero() {
        DateValidationResult result = service.validateYyyyMmDd("20240229");

        assertThat(result.isValid()).isTrue();
        assertThat(result.severity()).isZero();
        assertThat(result.severityCode()).isEqualTo("0000");
    }

    @Test
    void aShortValueIsInsufficientData() {
        DateValidationResult result = service.validateYyyyMmDd("202402");

        assertThat(result.isValid()).isFalse();
        assertThat(result.severity()).isEqualTo(3);
        assertThat(result.messageNumber()).isEqualTo(2507);
        assertThat(result.messageCode()).isEqualTo("2507");
    }

    @Test
    void nonNumericDataIsReportedSeparately() {
        assertThat(service.validateYyyyMmDd("2024022X").messageNumber()).isEqualTo(2520);
    }

    @Test
    void anImpossibleDateIsADateValueError() {
        assertThat(service.validateYyyyMmDd("20230229").messageNumber()).isEqualTo(2508);
        assertThat(service.validateYyyyMmDd("20241301").messageNumber()).isEqualTo(2508);
    }
}
