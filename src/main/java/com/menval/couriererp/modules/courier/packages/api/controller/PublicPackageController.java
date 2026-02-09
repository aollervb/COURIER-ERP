package com.menval.couriererp.modules.courier.packages.api.controller;

import com.menval.couriererp.modules.courier.packages.services.PackageService;
import com.menval.couriererp.modules.courier.packages.services.ReceivedStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Public API for package received status. Intended for the customer-facing application
 * (e.g. portal) to check whether a package has been received. Tenant is taken from
 * the X-Tenant-ID request header.
 */
@RestController
@RequestMapping("/api/public/packages")
public class PublicPackageController {

    private final PackageService packageService;

    public PublicPackageController(PackageService packageService) {
        this.packageService = packageService;
    }

    /**
     * Check if a package has been received by original tracking number.
     * Caller must send X-Tenant-ID so the lookup is tenant-scoped.
     *
     * @param trackingNumber original tracking number (e.g. from carrier label)
     * @return received status: received=true with receivedAt, status, carrier when found;
     *         received=false when not found (always 200)
     */
    @GetMapping("/received-status")
    public ReceivedStatus getReceivedStatus(@RequestParam("trackingNumber") String trackingNumber) {
        return packageService.getReceivedStatusByTrackingNumber(trackingNumber);
    }
}
