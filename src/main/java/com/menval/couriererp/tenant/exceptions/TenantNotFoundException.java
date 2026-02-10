package com.menval.couriererp.tenant.exceptions;

public class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException(String tenantId) {
        super("Tenant not found: " + tenantId);
    }
}
