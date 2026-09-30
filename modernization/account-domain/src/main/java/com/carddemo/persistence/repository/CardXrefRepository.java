package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.CardXrefEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardXrefRepository extends JpaRepository<CardXrefEntity, String> {

    /**
     * Replaces {@code EXEC CICS READ FILE('CXACAIX') RIDFLD(account-id)}: the alternate index
     * is keyed by account id and returns the first cross reference record for that account.
     */
    Optional<CardXrefEntity> findFirstByXrefAcctIdOrderByXrefCardNumAsc(Long xrefAcctId);
}
