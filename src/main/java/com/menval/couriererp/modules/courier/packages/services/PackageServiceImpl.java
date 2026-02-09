package com.menval.couriererp.modules.courier.packages.services;

import com.menval.couriererp.modules.courier.packages.entities.Carrier;
import com.menval.couriererp.modules.courier.packages.entities.PackageEntity;
import com.menval.couriererp.modules.courier.packages.entities.PackageStatus;
import com.menval.couriererp.modules.courier.packages.repositories.PackageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PackageServiceImpl implements PackageService {

    private final PackageRepository packageRepository;

    public PackageServiceImpl(PackageRepository packageRepository) {
        this.packageRepository = packageRepository;
    }

    @Override
    @Transactional
    public PackageEntity receivePackage(Carrier carrier, String originalTrackingNumber) {
        String tracking = normalizeTracking(originalTrackingNumber);
        if (tracking == null || tracking.isBlank()) {
            throw new IllegalArgumentException("Tracking number is required");
        }
        Optional<PackageEntity> existing = packageRepository.findByCarrierAndOriginalTrackingNumber(carrier, tracking);
        if (existing.isPresent()) {
            return existing.get();
        }
        return saveNewPackage(carrier != null ? carrier : Carrier.UNKNOWN, tracking);
    }

    private PackageEntity saveNewPackage(Carrier carrier, String tracking) {
        Instant now = Instant.now();
        PackageEntity pkg = new PackageEntity();
        pkg.setCarrier(carrier);
        pkg.setOriginalTrackingNumber(tracking);
        pkg.setOwner(null);
        pkg.setStatus(PackageStatus.RECEIVED_US_UNASSIGNED);
        pkg.setReceivedAt(now);
        pkg.setLastSeenAt(now);
        try {
            return packageRepository.save(pkg);
        } catch (DataIntegrityViolationException e) {
            return packageRepository.findByCarrierAndOriginalTrackingNumber(carrier, tracking)
                    .orElseThrow(() -> e);
        }
    }

    @Override
    @Transactional
    public BatchReceiveResult receivePackages(Carrier carrier, List<String> trackingNumbers) {
        if (trackingNumbers == null || trackingNumbers.isEmpty()) {
            return new BatchReceiveResult(0, 0, List.of());
        }
        Carrier effectiveCarrier = carrier != null ? carrier : Carrier.UNKNOWN;
        int receivedCount = 0;
        int duplicateCount = 0;
        List<String> invalidLines = new ArrayList<>();
        for (String line : trackingNumbers) {
            String tracking = normalizeTracking(line);
            if (tracking == null || tracking.isBlank()) {
                if (line != null && !line.isBlank()) {
                    invalidLines.add(line.trim());
                }
                continue;
            }
            Optional<PackageEntity> existing = packageRepository.findByCarrierAndOriginalTrackingNumber(effectiveCarrier, tracking);
            if (existing.isPresent()) {
                duplicateCount++;
                continue;
            }
            try {
                saveNewPackage(effectiveCarrier, tracking);
                receivedCount++;
            } catch (Exception e) {
                invalidLines.add(tracking);
            }
        }
        return new BatchReceiveResult(receivedCount, duplicateCount, List.copyOf(invalidLines));
    }

    @Override
    @Transactional(readOnly = true)
    public ReceivedStatus getReceivedStatusByTrackingNumber(String trackingNumber) {
        String tracking = normalizeTracking(trackingNumber);
        if (tracking == null || tracking.isBlank()) {
            return ReceivedStatus.notReceived(trackingNumber != null ? trackingNumber : "");
        }
        return packageRepository.findFirstByOriginalTrackingNumberOrderByReceivedAtDesc(tracking)
                .map(p -> ReceivedStatus.of(
                        p.getOriginalTrackingNumber(),
                        p.getCarrier(),
                        p.getStatus(),
                        p.getReceivedAt()
                ))
                .orElse(ReceivedStatus.notReceived(trackingNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PackageEntity> findByStatus(PackageStatus status, Pageable pageable) {
        return packageRepository.findByStatus(status, pageable);
    }

    private static String normalizeTracking(String raw) {
        if (raw == null) return null;
        return raw.trim();
    }
}
