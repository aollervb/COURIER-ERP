package com.menval.couriererp.tenant.entities;

import com.menval.couriererp.modules.common.models.BaseModel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tenant entity — represents a customer/organization.
 * Extends BaseModel (NOT TenantScopedBaseModel) because tenants don't belong to tenants.
 * tenant_id is the business identifier (unique), not a foreign key.
 */
@Entity
@Table(
        name = "tenants",
        uniqueConstraints = @UniqueConstraint(name = "uq_tenants_tenant_id", columnNames = "tenant_id"),
        indexes = {
                @Index(name = "idx_tenants_active", columnList = "active"),
                @Index(name = "idx_tenants_status", columnList = "status"),
                @Index(name = "idx_tenants_domain", columnList = "domain")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantEntity extends BaseModel {

    @Column(name = "tenant_id", nullable = false, unique = true, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 200)
    private String companyName;

    @Column(unique = true, length = 100)
    private String domain;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status = TenantStatus.TRIAL;

    private Instant subscriptionStartsAt;
    private Instant subscriptionExpiresAt;

    @Enumerated(EnumType.STRING)
    private SubscriptionPlan plan;

    @Embedded
    @Builder.Default
    private TenantSettings settings = new TenantSettings();

    @Column(length = 320)
    private String primaryContactEmail;

    @Column(length = 40)
    private String primaryContactPhone;

    @Column(length = 200)
    private String primaryContactName;

    @Column(length = 100)
    private String billingEmail;

    public boolean isExpired() {
        return subscriptionExpiresAt != null && Instant.now().isAfter(subscriptionExpiresAt);
    }

    public boolean canAccess() {
        return active && !isExpired() && status != TenantStatus.SUSPENDED;
    }
}
