package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.DailyTransactionRejectEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** DALYREJS file access. */
public interface DailyTransactionRejectRepository extends JpaRepository<DailyTransactionRejectEntity, Long> {

    List<DailyTransactionRejectEntity> findAllByOrderByRejectSeqAsc();
}
