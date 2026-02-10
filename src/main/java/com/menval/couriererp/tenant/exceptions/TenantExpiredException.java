package com.menval.couriererp.tenant.exceptions;

public class TenantExpiredException extends RuntimeException {
    public TenantExpiredException(String tenantId) {
        super("Tenant subscription has expired: " + tenantId);
    }
}
