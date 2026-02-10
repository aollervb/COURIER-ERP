package com.menval.couriererp.tenant.services;

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
     * Validate the raw API key and return the tenant ID if valid and tenant is active.
     */
    Optional<String> validateAndGetTenantId(String rawKey);
}
