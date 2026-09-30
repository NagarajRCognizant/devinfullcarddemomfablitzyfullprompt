package com.carddemo.domain.validation;

import java.time.LocalDate;

/** Outcome of EDIT-DATE-CCYYMMDD: whether the date passed and, when it did, the date itself. */
public record DateEditResult(boolean valid, LocalDate date) {
}
