package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.TransactionCategoryEntity;
import com.carddemo.persistence.entity.TransactionCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;

/** TRANCATG file access. */
public interface TransactionCategoryRepository
        extends JpaRepository<TransactionCategoryEntity, TransactionCategoryId> {
}
