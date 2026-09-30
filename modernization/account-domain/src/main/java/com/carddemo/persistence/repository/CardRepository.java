package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.CardEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * CARDDAT file access.
 *
 * <p>The browse methods reproduce the CICS {@code STARTBR}/{@code READNEXT}/{@code READPREV} of
 * COCRDLIC, which pages the card file in key order and optionally filters on the account id
 * through the CARDAIX alternate index.
 */
public interface CardRepository extends JpaRepository<CardEntity, String> {

    List<CardEntity> findByCardAcctIdOrderByCardNumAsc(Long cardAcctId);

    Optional<CardEntity> findByCardNumAndCardAcctId(String cardNum, Long cardAcctId);

    @Query("select c from CardEntity c where c.cardNum >= :fromCard"
            + " and (:acctId is null or c.cardAcctId = :acctId) order by c.cardNum asc")
    List<CardEntity> browseForwardFrom(@Param("fromCard") String fromCard, @Param("acctId") Long acctId,
            Limit limit);

    @Query("select c from CardEntity c where c.cardNum > :afterCard"
            + " and (:acctId is null or c.cardAcctId = :acctId) order by c.cardNum asc")
    List<CardEntity> browseForwardAfter(@Param("afterCard") String afterCard, @Param("acctId") Long acctId,
            Limit limit);

    @Query("select c from CardEntity c where c.cardNum < :beforeCard"
            + " and (:acctId is null or c.cardAcctId = :acctId) order by c.cardNum desc")
    List<CardEntity> browseBackwardBefore(@Param("beforeCard") String beforeCard, @Param("acctId") Long acctId,
            Limit limit);

    List<CardEntity> findAllByOrderByCardNumAsc();
}
