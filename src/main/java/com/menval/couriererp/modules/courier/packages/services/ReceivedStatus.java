package com.menval.couriererp.modules.courier.packages.services;

import com.menval.couriererp.modules.courier.packages.entities.Carrier;
import com.menval.couriererp.modules.courier.packages.entities.PackageStatus;

import java.time.Instant;

/**
 * DTO for the public API: whether a package has been received and minimal info.
 */
public record ReceivedStatus(
        boolean received,
        String originalTrackingNumber,
        Carrier carrier,
        PackageStatus status,
        Instant receivedAt
) {
    public static ReceivedStatus of(String originalTrackingNumber, Carrier carrier, PackageStatus status, Instant receivedAt) {
        return new ReceivedStatus(true, originalTrackingNumber, carrier, status, receivedAt);
    }

    public static ReceivedStatus notReceived(String originalTrackingNumber) {
        return new ReceivedStatus(false, originalTrackingNumber, null, null, null);
    }
}
