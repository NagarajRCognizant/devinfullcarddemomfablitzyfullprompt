package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.TransactionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * TRANSACT file access.
 *
 * <p>The browse methods reproduce the CICS {@code STARTBR}/{@code READNEXT}/{@code READPREV} of
 * COTRN00C: a key ordered scan from a position, forwards or backwards, limited to the page size.
 * Keyset paging is used rather than offset paging because the source browse is positional and a
 * transaction added between two pages must not shift the rows a user has already seen.
 */
public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {

    /** {@code STARTBR} at the supplied key then {@code READNEXT}: keys greater than or equal to it. */
    @Query("select t from TransactionEntity t where t.tranId >= :fromId order by t.tranId asc")
    List<TransactionEntity> browseForwardFrom(@Param("fromId") String fromId, Limit limit);

    /** {@code READNEXT} after the last key of the current page: strictly greater keys. */
    @Query("select t from TransactionEntity t where t.tranId > :afterId order by t.tranId asc")
    List<TransactionEntity> browseForwardAfter(@Param("afterId") String afterId, Limit limit);

    /** {@code READPREV} before the first key of the current page: strictly smaller keys, descending. */
    @Query("select t from TransactionEntity t where t.tranId < :beforeId order by t.tranId desc")
    List<TransactionEntity> browseBackwardBefore(@Param("beforeId") String beforeId, Limit limit);

    /**
     * {@code STARTBR} at HIGH-VALUES then {@code READPREV}: the highest key on file, which is the
     * identifier COTRN02C and COBIL00C add one to.
     */
    @Query("select max(t.tranId) from TransactionEntity t")
    Optional<String> findHighestTransactionId();

    /** Date range selection of CBTRN03C, which filters on the first ten characters of the process timestamp. */
    @Query("select t from TransactionEntity t where substring(t.tranProcTs, 1, 10) between :startDate and :endDate"
            + " order by t.tranId asc")
    List<TransactionEntity> findByProcessingDateRange(@Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /** Card level selection used by the statement job (CBSTM03A) and the card transaction views. */
    List<TransactionEntity> findByTranCardNumOrderByTranIdAsc(String tranCardNum);
}
