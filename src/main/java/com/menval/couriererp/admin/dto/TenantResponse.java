package com.menval.couriererp.admin.dto;

import com.menval.couriererp.tenant.entities.SubscriptionPlan;
import com.menval.couriererp.tenant.entities.TenantSettings;
import com.menval.couriererp.tenant.entities.TenantStatus;

import java.time.Instant;

public record TenantResponse(
        String tenantId,
        String companyName,
        String domain,
        boolean active,
        TenantStatus status,
        SubscriptionPlan plan,
        Instant subscriptionExpiresAt,
        String primaryContactEmail,
        TenantSettings settings
) {}
