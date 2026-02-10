package com.menval.couriererp.tenant.services;

import com.menval.couriererp.tenant.entities.SubscriptionPlan;

import java.time.Instant;

public record CreateTenantCommand(
        String tenantId,
        String companyName,
        String domain,
        String primaryContactName,
        String primaryContactEmail,
        String primaryContactPhone,
        SubscriptionPlan plan,
        Instant subscriptionExpiresAt,
        String accountCodePrefix
) {
    public CreateTenantCommand {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
        if (!tenantId.matches("^[a-z0-9-]+$")) {
            throw new IllegalArgumentException("Tenant ID must contain only lowercase letters, numbers, and hyphens");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Company name is required");
        }
    }
}
