package com.menval.couriererp.modules.courier.packages.services;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.modules.courier.packages.entities.batchPackages.PackageBatchEntity;
import com.menval.couriererp.modules.courier.packages.entities.batchPackages.TransportMode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PackageBatchService {

    List<PackageBatchEntity> listRecent();

    PackageBatchEntity createBatch(String referenceCode, TransportMode transportMode,
                                   String originFacilityCode, String destinationFacilityCode,
                                   String destinationCountry, Instant plannedDepartureAt,
                                   String containerType, BaseUser createdBy);

    Optional<PackageBatchEntity> findById(Long id);

    void addPackageToBatch(Long batchId, Long packageId);

    void removePackageFromBatch(Long batchId, Long packageId);

    void sealBatch(Long batchId);

    void markBatchInTransit(Long batchId);

    void markBatchArrived(Long batchId, String facilityCode, BaseUser actor);
}
