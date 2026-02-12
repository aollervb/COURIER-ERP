package com.menval.couriererp.tenant.services;

import com.menval.couriererp.tenant.dto.ApiKeySummary;

import java.util.List;
import java.util.Optional;

/**
 * Create and validate API keys for /api/public/** and /api/integration/**.
 * The raw key is returned only once on creation; only the hash is stored.
 */
public interface ApiKeyService {

    /**
     * Create a new API key for the tenant. Callable by tenant admin or SUPER_ADMIN.
     * The returned string is the raw key and must be shown once to the caller.
     */
    String createApiKey(String tenantId, String name);

    /**
     * Validate the raw API key and return the tenant ID if valid, tenant is active, and key is not suspended.
     */
    Optional<String> validateAndGetTenantId(String rawKey);

    /**
     * List API keys for the tenant (for settings UI). Keys are ordered by created date descending.
     */
    List<ApiKeySummary> listKeysForTenant(String tenantId);

    /**
     * Suspend an API key. Key must belong to the given tenant. Optional reason (e.g. leaked).
     */
    void suspendKey(String tenantId, Long keyId, String reason);

    /**
     * Unsuspend an API key. Key must belong to the given tenant.
     */
    void unsuspendKey(String tenantId, Long keyId);
}
