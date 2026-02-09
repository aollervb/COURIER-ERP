package com.menval.couriererp.modules.courier.account.repositories;

import com.menval.couriererp.modules.courier.account.entities.AccountEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {
    Optional<AccountEntity> findByExternalRef(String externalRef);
    Optional<AccountEntity> findByPublicId(String publicId);
    Optional<AccountEntity> findByCode(String code);

    Page<AccountEntity> findByActive(boolean active, Pageable pageable);

    @Query("""
    SELECT a
    FROM AccountEntity a
    WHERE (:active IS NULL OR a.active = :active)
      AND (
        (:id IS NOT NULL AND a.id = :id)
        OR (LOWER(a.code) LIKE LOWER(CONCAT('%', :q, '%')))
        OR (LOWER(a.email) LIKE LOWER(CONCAT('%', :q, '%')))
        OR (LOWER(a.externalRef) LIKE LOWER(CONCAT('%', :q, '%')))
      )
  """)
    Page<AccountEntity> search(@Param("q") String q,
                         @Param("id") Long id,
                         @Param("active") Boolean active,
                         Pageable pageable);
}
