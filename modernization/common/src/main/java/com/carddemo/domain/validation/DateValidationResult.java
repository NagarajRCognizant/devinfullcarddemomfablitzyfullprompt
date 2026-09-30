package com.carddemo.domain.validation;

/**
 * Result structure returned by CSUTLDTC (LS-RESULT): the CEEDAYS feedback severity, message
 * number and the decoded result text.
 */
public record DateValidationResult(int severity, int messageNumber, String result) {

    public static DateValidationResult valid() {
        return new DateValidationResult(0, 0, "Date is valid");
    }

    public boolean isValid() {
        return severity == 0;
    }

    /** CSUTLDTC formats the severity and message number as 4 digit zero filled numbers. */
    public String severityCode() {
        return String.format("%04d", severity);
    }

    public String messageCode() {
        return String.format("%04d", messageNumber);
    }
}
