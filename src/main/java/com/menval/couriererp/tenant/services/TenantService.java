package com.menval.couriererp.tenant.services;

import com.menval.couriererp.tenant.entities.TenantEntity;
import com.menval.couriererp.tenant.entities.TenantSettings;

import java.time.Instant;

public interface TenantService {

    /**
     * Create a new tenant. Only callable by SUPER_ADMIN users.
     */
    TenantEntity createTenant(CreateTenantCommand command);

    TenantEntity getTenantById(String tenantId);

    void suspendTenant(String tenantId, String reason);

    void activateTenant(String tenantId);

    void updateTenantSettings(String tenantId, TenantSettings settings);

    void extendSubscription(String tenantId, Instant newExpirationDate);

    void changePlan(String tenantId, com.menval.couriererp.tenant.entities.SubscriptionPlan newPlan);
}
