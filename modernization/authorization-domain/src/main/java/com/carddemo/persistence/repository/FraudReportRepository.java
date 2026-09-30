package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.FraudReportEntity;
import com.carddemo.persistence.entity.FraudReportId;
import org.springframework.data.jpa.repository.JpaRepository;

/** CARDDEMO.AUTHFRDS, the DB2 table COPAUS2C inserts into or updates on -803. */
public interface FraudReportRepository extends JpaRepository<FraudReportEntity, FraudReportId> {
}
