package com.menval.couriererp.modules.courier.packages.services;

import com.menval.couriererp.modules.courier.packages.entities.Carrier;
import com.menval.couriererp.modules.courier.packages.entities.PackageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PackageService {

    /**
     * Receive a package by carrier + tracking number. Creates a package with no owner,
     * status RECEIVED_US_UNASSIGNED. Idempotent: if same carrier+tracking already exists in tenant, returns existing.
     */
    PackageEntity receivePackage(Carrier carrier, String originalTrackingNumber);

    /**
     * Receive a batch of packages (same carrier). Each tracking number is processed idempotently.
     * Blank lines are skipped and reported in invalidLines. Returns counts of newly received, duplicates, and invalid.
     */
    BatchReceiveResult receivePackages(Carrier carrier, List<String> trackingNumbers);

    /**
     * Look up received status by tracking number only (for public/customer API).
     * Tenant from context. Returns first match if same tracking exists for multiple carriers.
     * Always returns a ReceivedStatus (received=true with data, or received=false when not found).
     */
    ReceivedStatus getReceivedStatusByTrackingNumber(String trackingNumber);

    /**
     * List packages by status (e.g. RECEIVED_US_UNASSIGNED for warehouse view).
     */
    Page<PackageEntity> findByStatus(com.menval.couriererp.modules.courier.packages.entities.PackageStatus status, Pageable pageable);

    /**
     * Assign a package to an account (customer). Delegates to {@link PackageEntity#assignToAccount}.
     *
     * @param packageId  package to assign
     * @param accountCode account code (e.g. CR-7K2P9D)
     * @return the updated package
     */
    PackageEntity assignPackageToAccount(Long packageId, String accountCode);
}
