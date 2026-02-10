package com.menval.couriererp.tenant;

/**
 * Holds the current tenant identifier for the request (or thread).
 * Set by {@link TenantContextFilter} from the login form "tenant" field (or by {@link com.menval.couriererp.security.ApiKeyAuthenticationFilter} for API)
 * and read by {@link TenantIdentifierResolver} for Hibernate's multi-tenancy.
 */
public final class TenantContext {

    private static final String DEFAULT_TENANT = "default";

    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            currentTenant.set(DEFAULT_TENANT);
        } else {
            currentTenant.set(tenantId.trim());
        }
    }

    public static String getTenantId() {
        String tenant = currentTenant.get();
        return tenant != null ? tenant : DEFAULT_TENANT;
    }

    public static void clear() {
        currentTenant.remove();
    }
}
