package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.AuthorizationRequestLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorizationRequestLogRepository
        extends JpaRepository<AuthorizationRequestLogEntity, String> {
}
