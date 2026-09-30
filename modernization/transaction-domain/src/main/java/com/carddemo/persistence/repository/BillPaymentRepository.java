package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.BillPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** The accepted bill payments, keyed by the submitting screen's idempotency key. */
public interface BillPaymentRepository extends JpaRepository<BillPaymentEntity, String> {
}
