package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.AuthorizationSummaryEntity;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface AuthorizationSummaryRepository extends JpaRepository<AuthorizationSummaryEntity, Long> {

    /**
     * Replaces {@code EXEC DLI GU SEGMENT(PAUTSUM0) WHERE (ACCNTID = PA-ACCT-ID)} of paragraph
     * 5500-READ-AUTH-SUMMRY with the pessimistic row lock that DL/I holds on the root segment for
     * the duration of the unit of work, so two authorizations for one account serialise on the
     * counters they both update instead of interleaving.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthorizationSummaryEntity s where s.acctId = :acctId")
    Optional<AuthorizationSummaryEntity> findByIdForUpdate(long acctId);

    /**
     * Replaces the {@code GN SEGMENT(PAUTSUM0)} root walk of CBPAUP0C paragraph
     * 2000-FIND-NEXT-AUTH-SUMMARY: the HIDAM root sequence is the ascending root key.
     */
    @Query("select s from AuthorizationSummaryEntity s where s.acctId > :afterAcctId order by s.acctId asc")
    java.util.List<AuthorizationSummaryEntity> findNextRoots(long afterAcctId, Pageable pageable);
}
