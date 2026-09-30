package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.AccountEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    /**
     * Equivalent of {@code EXEC CICS READ FILE('ACCTDAT') UPDATE} in COACTUPC paragraph
     * 9600-WRITE-PROCESSING: the row is locked for the duration of the transaction so the
     * subsequent compare-and-rewrite cannot interleave with another updater.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.acctId = :acctId")
    Optional<AccountEntity> lockByAcctId(Long acctId);

    /** Sequential ascending read of the ACCTDAT KSDS, as performed by CBACT01C. */
    @Query("select a from AccountEntity a order by a.acctId asc")
    java.util.List<AccountEntity> readForward(Pageable pageable);
}
