package com.carddemo.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * A turn of the Account Update conversation.
 *
 * <p>{@code original} is the snapshot the client received from the fetch call (ACUP-OLD-DETAILS)
 * and {@code updated} is what the user keyed (ACUP-NEW-DETAILS). Sending both reproduces the two
 * comparisons the source makes: keyed against fetched for "no change detected", and fetched
 * against stored for the record-changed check.
 */
public record AccountUpdateRequest(
        @NotNull AccountUpdateForm original,
        @NotNull AccountUpdateForm updated) {
}
