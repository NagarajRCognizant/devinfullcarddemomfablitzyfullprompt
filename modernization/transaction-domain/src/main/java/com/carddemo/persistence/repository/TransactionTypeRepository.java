package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.TransactionTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** TRANTYPE file access. */
public interface TransactionTypeRepository extends JpaRepository<TransactionTypeEntity, String> {
}
