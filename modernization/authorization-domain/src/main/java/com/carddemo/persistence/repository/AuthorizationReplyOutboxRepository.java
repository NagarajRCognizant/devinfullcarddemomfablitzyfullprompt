package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.AuthorizationReplyOutboxEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuthorizationReplyOutboxRepository
        extends JpaRepository<AuthorizationReplyOutboxEntity, Long> {

    @Query("""
            select o from AuthorizationReplyOutboxEntity o
             where o.publishedAt is null
             order by o.id asc
            """)
    List<AuthorizationReplyOutboxEntity> findUnpublished(Pageable pageable);
}
