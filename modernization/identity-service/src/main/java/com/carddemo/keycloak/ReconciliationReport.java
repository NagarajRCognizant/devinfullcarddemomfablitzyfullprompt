package com.carddemo.keycloak;

/**
 * What one reconciliation run did.
 *
 * @param records USRSEC records examined
 * @param accountsCreated records that had no realm account and now have one
 * @param accountsLinked records whose existing account was only missing from the record
 * @param failures records the realm could not be reconciled for; the run continues past them
 */
public record ReconciliationReport(int records, int accountsCreated, int accountsLinked,
        int failures) {
}
