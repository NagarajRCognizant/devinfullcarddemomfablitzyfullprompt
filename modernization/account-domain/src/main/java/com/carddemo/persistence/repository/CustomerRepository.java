package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.CustomerEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

    /** Equivalent of {@code EXEC CICS READ FILE('CUSTDAT') UPDATE} in COACTUPC. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CustomerEntity c where c.custId = :custId")
    Optional<CustomerEntity> lockByCustId(Long custId);
}
