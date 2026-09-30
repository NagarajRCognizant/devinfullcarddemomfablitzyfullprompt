package com.carddemo.domain.validation;

/**
 * Port of the one byte edit flags COACTUPC keeps per screen field
 * (LOW-VALUES = valid, '0' = not ok, 'B' = blank).
 *
 * <p>CSSETATY turns those flags into the BMS attribute bytes: a not-ok or blank field is
 * highlighted in red and a blank field additionally shows '*'. The React UI reproduces that
 * behaviour from this flag.
 */
public enum FieldFlag {
    VALID,
    NOT_OK,
    BLANK
}
