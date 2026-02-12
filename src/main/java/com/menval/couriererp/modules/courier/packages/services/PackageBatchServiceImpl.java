package com.menval.couriererp.modules.courier.packages.services;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.modules.courier.packages.entities.PackageEntity;
import com.menval.couriererp.modules.courier.packages.entities.PackageEventEntity;
import com.menval.couriererp.modules.courier.packages.entities.PackageStatus;
import com.menval.couriererp.modules.courier.packages.entities.batchPackages.BatchStatus;
import com.menval.couriererp.modules.courier.packages.entities.batchPackages.PackageBatchEntity;
import com.menval.couriererp.modules.courier.packages.entities.batchPackages.TransportMode;
import com.menval.couriererp.modules.courier.packages.repositories.PackageBatchRepository;
import com.menval.couriererp.modules.courier.packages.repositories.PackageEventRepository;
import com.menval.couriererp.modules.courier.packages.repositories.PackageRepository;
import com.menval.couriererp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PackageBatchServiceImpl implements PackageBatchService {

    private final PackageBatchRepository batchRepository;
    private final PackageRepository packageRepository;
    private final PackageEventRepository packageEventRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PackageBatchEntity> listRecent() {
        return batchRepository.findTop50ByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional
    public PackageBatchEntity createBatch(String referenceCode, TransportMode transportMode,
                                          String originFacilityCode, String destinationFacilityCode,
                                          String destinationCountry, Instant plannedDepartureAt,
                                          String containerType, BaseUser createdBy) {
        if (referenceCode == null || referenceCode.isBlank()) {
            throw new IllegalArgumentException("Reference code is required");
        }
        if (batchRepository.findByReferenceCode(referenceCode.trim()).isPresent()) {
            throw new IllegalArgumentException("Batch with reference code " + referenceCode + " already exists");
        }
        PackageBatchEntity batch = PackageBatchEntity.builder()
                .referenceCode(referenceCode.trim())
                .transportMode(transportMode != null ? transportMode : TransportMode.AIR)
                .originFacilityCode(originFacilityCode != null ? originFacilityCode.trim() : "")
                .destinationFacilityCode(destinationFacilityCode != null ? destinationFacilityCode.trim() : "")
                .destinationCountry(destinationCountry != null ? destinationCountry.trim().toUpperCase() : "DO")
                .plannedDepartureAt(plannedDepartureAt)
                .containerType(containerType != null ? containerType.trim() : null)
                .status(BatchStatus.DRAFT)
                .packageCount(0)
                .totalWeightGrams(0)
                .totalVolumeCm3(0)
                .createdBy(createdBy)
                .build();
        return batchRepository.save(batch);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PackageBatchEntity> findById(Long id) {
        if (id == null) return Optional.empty();
        return batchRepository.findById(id);
    }

    @Override
    @Transactional
    public void addPackageToBatch(Long batchId, Long packageId) {
        PackageBatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        if (!batch.isOpenForChanges()) {
            throw new IllegalStateException("Batch is closed; cannot add packages");
        }
        PackageEntity pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));
        if (!pkg.canBeAddedToBatch()) {
            throw new IllegalStateException("Package cannot be added to batch: status=" + pkg.getStatus()
                    + (pkg.getBatch() != null ? ", already in batch" : ""));
        }
        pkg.setBatch(batch);
        pkg.setStatus(PackageStatus.READY_TO_EXPORT);
        packageRepository.save(pkg);
        updateBatchTotals(batch);
    }

    @Override
    @Transactional
    public void removePackageFromBatch(Long batchId, Long packageId) {
        PackageBatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        if (!batch.isOpenForChanges()) {
            throw new IllegalStateException("Batch is closed; cannot remove packages");
        }
        PackageEntity pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));
        if (pkg.getBatch() == null || !pkg.getBatch().getId().equals(batchId)) {
            throw new IllegalArgumentException("Package is not in this batch");
        }
        pkg.setBatch(null);
        pkg.setStatus(PackageStatus.RECEIVED_US_ASSIGNED);
        packageRepository.save(pkg);
        updateBatchTotals(batch);
    }

    @Override
    @Transactional
    public void sealBatch(Long batchId) {
        PackageBatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        if (!batch.isOpenForChanges()) {
            throw new IllegalStateException("Batch is already closed");
        }
        batch.setStatus(BatchStatus.CLOSED);
        batchRepository.save(batch);
    }

    @Override
    @Transactional
    public void markBatchInTransit(Long batchId) {
        PackageBatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        if (batch.getStatus() != BatchStatus.CLOSED) {
            throw new IllegalStateException("Batch must be closed before marking in transit");
        }
        Instant now = Instant.now();
        batch.setStatus(BatchStatus.IN_TRANSIT);
        batch.setActualDepartureAt(now);
        batchRepository.save(batch);
        List<PackageEntity> packages = packageRepository.findByBatch_Id(batchId);
        for (PackageEntity pkg : packages) {
            pkg.setStatus(PackageStatus.IN_TRANSIT_INTERNATIONAL);
            pkg.setLastSeenAt(now);
            packageRepository.save(pkg);
        }
    }

    @Override
    @Transactional
    public void markBatchArrived(Long batchId, String facilityCode, BaseUser actor) {
        PackageBatchEntity batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        if (batch.getStatus() != BatchStatus.IN_TRANSIT) {
            throw new IllegalStateException("Batch must be in transit before marking arrived");
        }
        Instant now = Instant.now();
        batch.setStatus(BatchStatus.ARRIVED);
        batch.setActualArrivalAt(now);
        batchRepository.save(batch);
        List<PackageEntity> packages = packageRepository.findByBatch_Id(batchId);
        String facility = facilityCode != null ? facilityCode : batch.getDestinationFacilityCode();
        for (PackageEntity pkg : packages) {
            pkg.setStatus(PackageStatus.RECEIVED_FINAL);
            pkg.setLastSeenAt(now);
            packageRepository.save(pkg);
            PackageEventEntity event = pkg.createArrivedDestinationEvent(now, facility, actor, null);
            if (actor != null && actor.getUserTenantId() != null && !actor.getUserTenantId().isBlank()) {
                TenantContext.setTenantId(actor.getUserTenantId());
            }
            packageEventRepository.save(event);
        }
    }

    private void updateBatchTotals(PackageBatchEntity batch) {
        List<PackageEntity> packages = packageRepository.findByBatch_Id(batch.getId());
        int count = packages.size();
        long weight = 0;
        long volume = 0;
        for (PackageEntity pkg : packages) {
            weight += pkg.getWeightGrams();
            volume += (long) pkg.getLengthCm() * pkg.getWidthCm() * pkg.getHeightCm();
        }
        batch.setPackageCount(count);
        batch.setTotalWeightGrams(weight);
        batch.setTotalVolumeCm3(volume);
        batchRepository.save(batch);
    }
}
