package com.menval.couriererp.tenant.exceptions;

public class TenantSuspendedException extends RuntimeException {
    public TenantSuspendedException(String tenantId) {
        super("Tenant is suspended: " + tenantId);
    }
}
