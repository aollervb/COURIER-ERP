package com.menval.couriererp.modules.courier.packages.repositories;

import com.menval.couriererp.modules.courier.packages.entities.PackageEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackageEventRepository extends JpaRepository<PackageEventEntity, Long> {
    List<PackageEventEntity> findByPkg_IdOrderByEventTimeAsc(Long packageId);
}
