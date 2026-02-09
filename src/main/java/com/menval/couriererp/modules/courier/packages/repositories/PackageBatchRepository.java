package com.menval.couriererp.modules.courier.packages.repositories;

import com.menval.couriererp.modules.courier.packages.entities.batchPackages.BatchStatus;
import com.menval.couriererp.modules.courier.packages.entities.batchPackages.PackageBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PackageBatchRepository extends JpaRepository<PackageBatchEntity, Long> {
    Optional<PackageBatchEntity> findByReferenceCode(String referenceCode);
    List<PackageBatchEntity> findTop50ByOrderByCreatedAtDesc();
    List<PackageBatchEntity> findByStatusOrderByCreatedAtDesc(BatchStatus status);
}

