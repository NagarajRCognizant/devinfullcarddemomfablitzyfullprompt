package com.carddemo.api.dto;

/**
 * The editable fields of the user update map (app/bms/COUSR02.bms).
 *
 * <p>COUSR02C reads the record, compares each of these fields with the stored value and rewrites
 * only when at least one differs, so all four are sent on every request.
 */
public record UserUpdateForm(String firstName, String lastName, String password, String userType) {
}
