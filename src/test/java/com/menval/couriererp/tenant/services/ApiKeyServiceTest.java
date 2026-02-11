package com.menval.couriererp.tenant.services;

import com.menval.couriererp.tenant.TenantContext;
import com.menval.couriererp.tenant.entities.*;
import com.menval.couriererp.tenant.repositories.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that API key lookup returns the tenant that owns the key, regardless of current TenantContext.
 */
@SpringBootTest
@Transactional
class ApiKeyServiceTest {

    private static final String TENANT_A = "api-test-tenant-a";
    private static final String TENANT_B = "api-test-tenant-b";

    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private TenantRepository tenantRepository;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void validateAndGetTenantId_returnsKeyOwnerTenant_evenWhenContextIsDifferentTenant() {
        TenantSettings settings = new TenantSettings();
        TenantEntity tenantA = TenantEntity.builder()
                .tenantId(TENANT_A)
                .companyName("Test Tenant A")
                .active(true)
                .status(TenantStatus.ACTIVE)
                .plan(SubscriptionPlan.STARTER)
                .subscriptionStartsAt(Instant.now())
                .subscriptionExpiresAt(null)
                .settings(settings)
                .build();
        TenantEntity tenantB = TenantEntity.builder()
                .tenantId(TENANT_B)
                .companyName("Test Tenant B")
                .active(true)
                .status(TenantStatus.ACTIVE)
                .plan(SubscriptionPlan.STARTER)
                .subscriptionStartsAt(Instant.now())
                .subscriptionExpiresAt(null)
                .settings(settings)
                .build();
        tenantRepository.save(tenantA);
        tenantRepository.save(tenantB);

        String rawKey = apiKeyService.createApiKey(TENANT_B, "test-key");

        // Context is set to tenant A; key belongs to tenant B.
        TenantContext.setTenantId(TENANT_A);

        // Validate: must return tenant B (key owner), not tenant A (context).
        assertThat(apiKeyService.validateAndGetTenantId(rawKey)).contains(TENANT_B);
    }
}
