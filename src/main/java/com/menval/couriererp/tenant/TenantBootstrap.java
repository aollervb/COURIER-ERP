package com.menval.couriererp.tenant;

import com.menval.couriererp.tenant.entities.*;
import com.menval.couriererp.tenant.repositories.TenantRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Creates the "default" tenant if no tenants exist (e.g. first run).
 * The "default" tenant is used when no tenant is set (e.g. before login). Tenant is set only from the login form.
 */
@Component
@Order(100)
public class TenantBootstrap implements ApplicationRunner {

    /** Tenant ID used for the platform super-admin user (not a customer tenant). */
    public static final String SYSTEM_TENANT_ID = "system";

    private final TenantRepository tenantRepository;

    public TenantBootstrap(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void run(ApplicationArguments args) {

        if (tenantRepository.findByTenantId(SYSTEM_TENANT_ID).isEmpty()) {
            TenantSettings systemSettings = new TenantSettings();
            systemSettings.setAccountCodePrefix("SYS");
            systemSettings.setAccountCodeLength(6);
            TenantEntity systemTenant = TenantEntity.builder()
                    .tenantId(SYSTEM_TENANT_ID)
                    .companyName("System")
                    .domain(null)
                    .active(true)
                    .status(TenantStatus.ACTIVE)
                    .plan(SubscriptionPlan.ENTERPRISE)
                    .subscriptionStartsAt(Instant.now())
                    .subscriptionExpiresAt(null) // system tenant never expires
                    .settings(systemSettings)
                    .build();
            tenantRepository.save(systemTenant);
        }
    }
}
