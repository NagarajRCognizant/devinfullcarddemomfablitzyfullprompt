package com.carddemo.persistence.repository;

import com.carddemo.persistence.entity.SecurityUserEntity;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The USRSEC accesses of the sign-on and user administration programs.
 *
 * <p>COUSR00C browses the file with STARTBR/READNEXT/READPREV on the user id key, which becomes an
 * ordered keyset query in either direction rather than an offset page: the legacy list moves from
 * the last key shown, so a user added or deleted in between does not shift the window.
 */
public interface SecurityUserRepository extends JpaRepository<SecurityUserEntity, String> {

    /** STARTBR at the requested key followed by READNEXT (the key itself is included). */
    List<SecurityUserEntity> findByUserIdGreaterThanEqualOrderByUserIdAsc(String userId, Limit limit);

    /** READNEXT past the last key shown on the current page. */
    List<SecurityUserEntity> findByUserIdGreaterThanOrderByUserIdAsc(String userId, Limit limit);

    /** READPREV from the first key shown on the current page; the caller re-orders the result. */
    List<SecurityUserEntity> findByUserIdLessThanOrderByUserIdDesc(String userId, Limit limit);
}
