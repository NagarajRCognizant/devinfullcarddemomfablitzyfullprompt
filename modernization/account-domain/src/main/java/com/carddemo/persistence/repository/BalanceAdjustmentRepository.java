package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.BalanceAdjustmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** The applied balance changes, keyed by the caller's idempotency key. */
public interface BalanceAdjustmentRepository extends JpaRepository<BalanceAdjustmentEntity, String> {
}
