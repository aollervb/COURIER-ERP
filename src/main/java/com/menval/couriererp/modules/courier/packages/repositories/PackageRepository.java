package com.menval.couriererp.modules.courier.packages.repositories;

import com.menval.couriererp.modules.courier.packages.entities.Carrier;
import com.menval.couriererp.modules.courier.packages.entities.PackageEntity;
import com.menval.couriererp.modules.courier.packages.entities.PackageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PackageRepository extends JpaRepository<PackageEntity, Long> {

    Optional<PackageEntity> findByCarrierAndOriginalTrackingNumber(Carrier carrier, String originalTrackingNumber);

    Optional<PackageEntity> findFirstByOriginalTrackingNumberOrderByReceivedAtDesc(String originalTrackingNumber);

    Page<PackageEntity> findByStatus(PackageStatus status, Pageable pageable);

    List<PackageEntity> findByBatch_Id(Long batchId);

    Page<PackageEntity> findByStatusAndBatchIsNull(PackageStatus status, Pageable pageable);

    Page<PackageEntity> findByStatusIn(List<PackageStatus> statuses, Pageable pageable);
}
