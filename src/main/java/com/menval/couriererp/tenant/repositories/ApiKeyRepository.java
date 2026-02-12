package com.menval.couriererp.tenant.repositories;

import com.menval.couriererp.tenant.entities.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {

    Optional<ApiKeyEntity> findByKeyHash(String keyHash);

    List<ApiKeyEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    boolean existsByTenantIdAndName(String tenantId, String name);
}
