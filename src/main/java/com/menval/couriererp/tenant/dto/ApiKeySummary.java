package com.menval.couriererp.tenant.dto;

import java.time.Instant;

/**
 * Summary of an API key for listing in settings (no raw key or hash).
 */
public record ApiKeySummary(
        Long id,
        String name,
        Instant createdAt,
        boolean suspended,
        Instant suspendedAt,
        String suspensionReason
) {}
