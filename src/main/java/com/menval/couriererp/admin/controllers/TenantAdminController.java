package com.menval.couriererp.admin.controllers;

import com.menval.couriererp.admin.dto.CreateApiKeyRequest;
import com.menval.couriererp.admin.dto.CreateApiKeyResponse;
import com.menval.couriererp.admin.dto.CreateTenantRequest;
import com.menval.couriererp.admin.dto.SuspendRequest;
import com.menval.couriererp.admin.dto.TenantResponse;
import com.menval.couriererp.tenant.entities.TenantEntity;
import com.menval.couriererp.tenant.services.ApiKeyService;
import com.menval.couriererp.tenant.services.CreateTenantCommand;
import com.menval.couriererp.tenant.services.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * SUPER_ADMIN only: create and manage tenants.
 */
@RestController
@RequestMapping("/api/admin/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantAdminController {

    private final TenantService tenantService;
    private final ApiKeyService apiKeyService;

    public TenantAdminController(TenantService tenantService, ApiKeyService apiKeyService) {
        this.tenantService = tenantService;
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        Instant expiresAt = request.subscriptionMonths() != null
                ? Instant.now().plus(request.subscriptionMonths() * 30L, ChronoUnit.DAYS)
                : Instant.now().plus(365, ChronoUnit.DAYS);

        CreateTenantCommand command = new CreateTenantCommand(
                request.tenantId(),
                request.companyName(),
                request.domain(),
                request.primaryContactName(),
                request.primaryContactEmail(),
                request.primaryContactPhone(),
                request.plan(),
                expiresAt,
                request.accountCodePrefix()
        );

        TenantEntity tenant = tenantService.createTenant(command);
        return toResponse(tenant);
    }

    @GetMapping("/{tenantId}")
    public TenantResponse getTenant(@PathVariable String tenantId) {
        TenantEntity tenant = tenantService.getTenantById(tenantId);
        return toResponse(tenant);
    }

    @PostMapping("/{tenantId}/suspend")
    public void suspendTenant(@PathVariable String tenantId, @RequestBody SuspendRequest request) {
        tenantService.suspendTenant(tenantId, request.reason());
    }

    @PostMapping("/{tenantId}/activate")
    public void activateTenant(@PathVariable String tenantId) {
        tenantService.activateTenant(tenantId);
    }

    /**
     * Create an API key for the tenant. The raw key is returned only once.
     * Use X-API-Key or Authorization: Bearer &lt;key&gt; for /api/public/** and /api/integration/**.
     */
    @PostMapping("/{tenantId}/api-keys")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateApiKeyResponse createApiKey(@PathVariable String tenantId,
                                             @RequestBody(required = false) CreateApiKeyRequest request) {
        String name = request != null && request.name() != null ? request.name() : "API key";
        String rawKey = apiKeyService.createApiKey(tenantId, name);
        return new CreateApiKeyResponse(rawKey);
    }

    private static TenantResponse toResponse(TenantEntity tenant) {
        return new TenantResponse(
                tenant.getTenantId(),
                tenant.getCompanyName(),
                tenant.getDomain(),
                tenant.isActive(),
                tenant.getStatus(),
                tenant.getPlan(),
                tenant.getSubscriptionExpiresAt(),
                tenant.getPrimaryContactEmail(),
                tenant.getSettings()
        );
    }
}
