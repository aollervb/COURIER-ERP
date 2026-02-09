package com.menval.couriererp.modules.courier.packages.repositories;

import com.menval.couriererp.modules.courier.packages.entities.PackageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PackageRepository extends JpaRepository<PackageEntity, Long> {
    Optional<PackageEntity> findByOriginalTrackingNumber(String originalTrackingNumber);
}
