package com.menval.couriererp.modules.courier.packages.services;

import java.util.List;

/**
 * Result of receiving a batch of packages: how many were newly received,
 * how many were already present (duplicates), and any invalid lines.
 */
public record BatchReceiveResult(
        int receivedCount,
        int duplicateCount,
        List<String> invalidLines
) {
    public int totalProcessed() {
        return receivedCount + duplicateCount + invalidLines.size();
    }
}
