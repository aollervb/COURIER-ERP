package com.menval.couriererp.modules.courier.packages.services;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.modules.courier.account.entities.AccountEntity;
import com.menval.couriererp.modules.courier.account.services.AccountService;
import com.menval.couriererp.modules.courier.packages.dto.PackageEventDto;
import com.menval.couriererp.modules.courier.packages.entities.Carrier;
import com.menval.couriererp.modules.courier.packages.entities.PackageEntity;
import com.menval.couriererp.modules.courier.packages.entities.PackageEventEntity;
import com.menval.couriererp.modules.courier.packages.entities.PackageStatus;
import com.menval.couriererp.tenant.TenantContext;
import com.menval.couriererp.modules.courier.packages.repositories.PackageEventRepository;
import com.menval.couriererp.modules.courier.packages.repositories.PackageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

import org.hibernate.ObjectNotFoundException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PackageServiceImpl implements PackageService {
    private static final Logger log = LoggerFactory.getLogger(PackageServiceImpl.class);

    private final PackageRepository packageRepository;
    private final PackageEventRepository packageEventRepository;
    private final AccountService accountService;

    public PackageServiceImpl(PackageRepository packageRepository,
                              PackageEventRepository packageEventRepository,
                              AccountService accountService) {
        this.packageRepository = packageRepository;
        this.packageEventRepository = packageEventRepository;
        this.accountService = accountService;
    }

    @Override
    @Transactional
    public PackageEntity receivePackage(Carrier carrier, String originalTrackingNumber, BaseUser actor) {
        String tracking = normalizeTracking(originalTrackingNumber);
        if (tracking == null || tracking.isBlank()) {
            throw new IllegalArgumentException("Tracking number is required");
        }
        Optional<PackageEntity> existing = packageRepository.findByCarrierAndOriginalTrackingNumber(carrier, tracking);
        if (existing.isPresent()) {
            return existing.get();
        }
        return saveNewPackage(carrier != null ? carrier : Carrier.UNKNOWN, tracking, actor);
    }

    private PackageEntity saveNewPackage(Carrier carrier, String tracking, BaseUser actor) {
        Instant now = Instant.now();
        PackageEntity pkg = PackageEntity.receive(carrier, tracking, now);
        try {
            pkg = packageRepository.save(pkg);
            PackageEventEntity event = pkg.createReceivedEvent(pkg.getReceivedAt(), null, actor, null);
            // Persist event in the actor's tenant so the actor can be loaded when viewing audit
            if (actor != null && actor.getUserTenantId() != null && !actor.getUserTenantId().isBlank()) {
                TenantContext.setTenantId(actor.getUserTenantId());
            }
            packageEventRepository.save(event);
            return pkg;
        } catch (DataIntegrityViolationException e) {
            return packageRepository.findByCarrierAndOriginalTrackingNumber(carrier, tracking)
                    .orElseThrow(() -> e);
        }
    }

    @Override
    @Transactional
    public BatchReceiveResult receivePackages(Carrier carrier, List<String> trackingNumbers, BaseUser actor) {
        if (trackingNumbers == null || trackingNumbers.isEmpty()) {
            return new BatchReceiveResult(0, 0, List.of());
        }
        Carrier effectiveCarrier = carrier != null ? carrier : Carrier.UNKNOWN;
        log.debug("Package modified by {}", actor);
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
                saveNewPackage(effectiveCarrier, tracking, actor);
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
    public Page<PackageEntity> listAll(Pageable pageable, PackageStatus statusFilter) {
        if (statusFilter == null) {
            return packageRepository.findAll(pageable);
        }
        return packageRepository.findByStatus(statusFilter, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PackageEntity> findByStatus(PackageStatus status, Pageable pageable) {
        return packageRepository.findByStatus(status, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackageEventDto> getEventsForPackage(Long packageId) {
        if (packageId == null) {
            return List.of();
        }
        if (packageRepository.findById(packageId).isEmpty()) {
            return List.of();
        }
        List<PackageEventEntity> events = packageEventRepository.findByPkg_IdOrderByEventTimeAsc(packageId);
        log.debug("Found {} events for package {}", events.size(), packageId);
        return events.stream()
                .map(this::toEventDto)
                .toList();
    }

    private PackageEventDto toEventDto(PackageEventEntity e) {
        String actorEmail = null;
        String actorName = null;

        BaseUser actor = e.getActor();
        if (actor != null) {
            // TODO: investigate why the actor is null here
            log.debug("Actor isn't null, {}", actor);
            actorEmail = actor.getEmail();
            actorName = (actor.getFirstName() + " " + actor.getLastName()).trim();
        }

        return new PackageEventDto(
                e.getType().name(),
                e.getEventTime(),
                actorEmail,
                actorName,
                e.getNotes()
        );
    }

    @Override
    @Transactional
    public PackageEntity assignPackageToAccount(Long packageId, String accountCode, BaseUser actor) {
        PackageEntity pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));
        AccountEntity account = accountService.getByCode(accountCode);
        pkg.assignToAccount(account);
        pkg = packageRepository.save(pkg);
        PackageEventEntity event = pkg.createOwnerAssignedEvent(null, null, actor, null);

        if (actor != null && actor.getUserTenantId() != null && !actor.getUserTenantId().isBlank()) {
            TenantContext.setTenantId(actor.getUserTenantId());
        }
        packageEventRepository.save(event);
        return pkg;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PackageEntity> findAssignableForBatch(Pageable pageable) {
        return packageRepository.findByStatusAndBatchIsNull(PackageStatus.RECEIVED_US_ASSIGNED, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PackageEntity> findReadyForDispatch(Pageable pageable) {
        return packageRepository.findByStatusIn(
                List.of(PackageStatus.RECEIVED_FINAL, PackageStatus.OUT_FOR_DELIVERY),
                pageable);
    }

    @Override
    @Transactional
    public PackageEntity markOutForDelivery(Long packageId, BaseUser actor) {
        PackageEntity pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));
        if (pkg.getStatus() != PackageStatus.RECEIVED_FINAL && pkg.getStatus() != PackageStatus.ARRIVED_DR) {
            throw new IllegalStateException("Package must be RECEIVED_FINAL or ARRIVED_DR; current: " + pkg.getStatus());
        }
        pkg.setStatus(PackageStatus.OUT_FOR_DELIVERY);
        pkg.setLastSeenAt(Instant.now());
        pkg = packageRepository.save(pkg);
        PackageEventEntity event = pkg.createOutForDeliveryEvent(null, null, actor, null);
        if (actor != null && actor.getUserTenantId() != null && !actor.getUserTenantId().isBlank()) {
            TenantContext.setTenantId(actor.getUserTenantId());
        }
        packageEventRepository.save(event);
        return pkg;
    }

    @Override
    @Transactional
    public PackageEntity markDelivered(Long packageId, BaseUser actor) {
        PackageEntity pkg = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));
        if (pkg.getStatus() != PackageStatus.OUT_FOR_DELIVERY && pkg.getStatus() != PackageStatus.RECEIVED_FINAL) {
            throw new IllegalStateException("Package must be OUT_FOR_DELIVERY or RECEIVED_FINAL; current: " + pkg.getStatus());
        }
        pkg.setStatus(PackageStatus.DELIVERED);
        pkg.setLastSeenAt(Instant.now());
        pkg = packageRepository.save(pkg);
        PackageEventEntity event = pkg.createDeliveredEvent(null, null, actor, null);
        if (actor != null && actor.getUserTenantId() != null && !actor.getUserTenantId().isBlank()) {
            TenantContext.setTenantId(actor.getUserTenantId());
        }
        packageEventRepository.save(event);
        return pkg;
    }

    private static String normalizeTracking(String raw) {
        if (raw == null) return null;
        return raw.trim();
    }
}
