package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.ReportRequestEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** The confirmed report requests of CORPT00C, newest first. */
public interface ReportRequestRepository extends JpaRepository<ReportRequestEntity, Long> {

    List<ReportRequestEntity> findAllByOrderByRequestIdDesc();
}
