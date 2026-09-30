package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.TransactionCategoryBalanceEntity;
import com.carddemo.persistence.entity.TransactionCategoryBalanceId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** TCATBAL file access: point reads by key for posting, key ordered scan for the interest run. */
public interface TransactionCategoryBalanceRepository
        extends JpaRepository<TransactionCategoryBalanceEntity, TransactionCategoryBalanceId> {

    /**
     * The sequential read of CBACT04C, which relies on all rows of an account arriving together and
     * in key order so that the account totals can be accumulated between key breaks.
     */
    List<TransactionCategoryBalanceEntity> findAllByOrderByIdTrancatAcctIdAscIdTrancatTypeCdAscIdTrancatCdAsc();
}
