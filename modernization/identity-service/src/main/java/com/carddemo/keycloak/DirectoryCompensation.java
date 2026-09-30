package com.carddemo.keycloak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Undoes a realm write when the USRSEC write it belongs to does not commit.
 *
 * <p>The two stores cannot be written in one transaction. The order chosen is USRSEC first and the
 * realm second, so a failed realm call rolls the record back and the screen reports the failure of
 * the source program; the remaining window is a realm call that succeeded and a commit that then
 * failed, and this closes it by running the inverse call after the rollback.
 *
 * <p>A failing compensation is logged and not rethrown: the transaction has already ended, and the
 * reconciliation of {@link UserReconciliation} is what resolves a leftover account.
 */
@Component
public class DirectoryCompensation {

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryCompensation.class);

    /** Registers {@code undo} to run only if the current transaction rolls back. */
    public void onRollback(String what, Runnable undo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    undo.run();
                } catch (RuntimeException failure) {
                    LOG.error("Could not compensate the realm write to {}: {}", what,
                            failure.getMessage(), failure);
                }
            }
        });
    }
}
