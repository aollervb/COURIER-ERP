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
 * The "default" tenant is used when no tenant is set (e.g. before login). Tenant is set only from the login form.
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
        if (tenantRepository.count() == 0) {
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
                    .subscriptionExpiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
                    .settings(systemSettings)
                    .build();
            tenantRepository.save(systemTenant);
        }
    }

    /** Tenant ID used for the platform super-admin user (not a customer tenant). */
    public static final String SYSTEM_TENANT_ID = "system";
}
