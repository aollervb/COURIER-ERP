package com.menval.couriererp.modules.courier.packages.dto;

import java.time.Instant;

/**
 * Audit event for a package (for JSON / UI).
 */
public record PackageEventDto(
        String eventType,
        Instant eventTime,
        String actorEmail,
        String actorName,
        String notes
) {}
