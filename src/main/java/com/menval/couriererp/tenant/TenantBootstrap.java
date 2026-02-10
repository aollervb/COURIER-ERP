package com.menval.couriererp.tenant;

import com.menval.couriererp.tenant.entities.*;
import com.menval.couriererp.tenant.repositories.TenantRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Creates the "default" tenant if no tenants exist (e.g. first run).
 * Ensures login with X-Tenant-ID: default works after tenant-based auth is enabled.
 */
@Component
@Order(100)
public class TenantBootstrap implements ApplicationRunner {

    private static final String DEFAULT_TENANT_ID = "default";

    private final TenantRepository tenantRepository;

    public TenantBootstrap(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tenantRepository.count() > 0) {
            return;
        }

        TenantSettings settings = new TenantSettings();
        settings.setAccountCodePrefix("CR");
        settings.setAccountCodeLength(6);
        settings.setMaxUsers(10);
        settings.setMaxPackagesPerMonth(1000);

        TenantEntity defaultTenant = TenantEntity.builder()
                .tenantId(DEFAULT_TENANT_ID)
                .companyName("Default Tenant")
                .domain(null)
                .active(true)
                .status(TenantStatus.ACTIVE)
                .plan(SubscriptionPlan.STARTER)
                .subscriptionStartsAt(Instant.now())
                .subscriptionExpiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
                .settings(settings)
                .build();

        tenantRepository.save(defaultTenant);
    }
}
