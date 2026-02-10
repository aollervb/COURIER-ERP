package com.menval.couriererp.tenant.repositories;

import com.menval.couriererp.tenant.entities.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<TenantEntity, Long> {

    Optional<TenantEntity> findByTenantId(String tenantId);

    Optional<TenantEntity> findByDomain(String domain);

    boolean existsByTenantId(String tenantId);

    boolean existsByDomain(String domain);
}
