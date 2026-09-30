package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.AuthorizationDetailEntity;
import com.carddemo.persistence.entity.AuthorizationDetailId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AuthorizationDetailRepository
        extends JpaRepository<AuthorizationDetailEntity, AuthorizationDetailId> {

    /**
     * Replaces the {@code GNP SEGMENT(PAUTDTL1)} walk of COPAUS0C paragraph GET-AUTHORIZATIONS and
     * of CBPAUP0C paragraph 3000-FIND-NEXT-AUTH-DTL. DL/I returns the children of the root in key
     * order and the key holds the nines complement of date and time, so ascending order is newest
     * first; paging forward is "the next keys greater than the last key on the page".
     */
    @Query("""
            select d from AuthorizationDetailEntity d
             where d.id.acctId = :acctId
               and d.id.authKey > :afterAuthKey
             order by d.id.authKey asc
            """)
    List<AuthorizationDetailEntity> findChildrenAfter(long acctId, String afterAuthKey, Pageable pageable);

    /** The same walk from the start of the child chain, which is what an unpositioned GNP does. */
    @Query("""
            select d from AuthorizationDetailEntity d
             where d.id.acctId = :acctId
             order by d.id.authKey asc
            """)
    List<AuthorizationDetailEntity> findChildren(long acctId, Pageable pageable);

    /** Replaces {@code GU SEGMENT(PAUTDTL1) WHERE (AUTHKEY = WS-AUTH-KEY)} of COPAUS1C. */
    Optional<AuthorizationDetailEntity> findById(AuthorizationDetailId id);

    long countByIdAcctId(long acctId);

    @Modifying
    @Query("delete from AuthorizationDetailEntity d where d.id.acctId = :acctId")
    int deleteByAcctId(long acctId);
}
