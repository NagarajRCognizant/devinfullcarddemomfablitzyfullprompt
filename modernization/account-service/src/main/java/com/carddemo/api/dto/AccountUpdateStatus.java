package com.carddemo.api.dto;

/**
 * The states COACTUPC keeps in ACUP-CHANGE-ACTION, exposed as an explicit response field.
 *
 * <p>The legacy program drives the screen from this one character in the COMMAREA; the target
 * returns it to the client, which is what lets the update flow stay stateless on the server.
 */
public enum AccountUpdateStatus {
    /** ACUP-SHOW-DETAILS - the fetched record is on the screen, unchanged. */
    DETAILS_FETCHED,
    /** 1205-COMPARE-OLD-NEW found the keyed values identical to the fetched ones. */
    NO_CHANGES,
    /** ACUP-CHANGES-NOT-OK - at least one edit failed. */
    VALIDATION_ERROR,
    /** ACUP-CHANGES-OK-NOT-CONFIRMED - the edits passed and confirmation is awaited. */
    CHANGES_VALIDATED,
    /** ACUP-CHANGES-OKAYED-AND-DONE - both records were rewritten. */
    CHANGES_COMMITTED,
    /** 9700-CHECK-CHANGE-IN-REC detected a concurrent update. */
    RECORD_CHANGED,
    /** ACUP-CHANGES-OKAYED-LOCK-ERROR - a record could not be locked for update. */
    LOCK_ERROR,
    /** ACUP-CHANGES-OKAYED-BUT-FAILED - a rewrite failed and the unit of work was rolled back. */
    UPDATE_FAILED
}
