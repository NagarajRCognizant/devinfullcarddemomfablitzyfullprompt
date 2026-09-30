package com.carddemo.api;

import com.carddemo.keycloak.ReconciliationReport;
import com.carddemo.keycloak.UserReconciliation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provisions the realm accounts of the USRSEC records that have none.
 *
 * <p>Not a screen of the source: the source had one credential store and needed no reconciliation.
 * It sits under {@code /api/users}, so the administrator authority of the admin menu already guards
 * it, and it is idempotent - a record that already names its account is skipped.
 */
@RestController
@RequestMapping("/api/users/reconciliation")
public class ReconciliationController {

    private final UserReconciliation reconciliation;

    public ReconciliationController(UserReconciliation reconciliation) {
        this.reconciliation = reconciliation;
    }

    @PostMapping
    public ResponseEntity<ReconciliationReport> reconcile() {
        return ResponseEntity.ok(reconciliation.reconcile());
    }
}
