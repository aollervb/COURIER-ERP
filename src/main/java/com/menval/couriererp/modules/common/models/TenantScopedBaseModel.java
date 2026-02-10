package com.menval.couriererp.modules.common.models;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

/**
 * Base class for entities that belong to a specific tenant.
 * Extends BaseModel and adds tenant_id with Hibernate's @TenantId.
 * Use for: AccountEntity, PackageEntity, BaseUser, etc.
 */
@MappedSuperclass
public abstract class TenantScopedBaseModel extends BaseModel {

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
