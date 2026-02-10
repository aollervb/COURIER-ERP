package com.menval.couriererp.modules.courier.account.entities;

import com.menval.couriererp.modules.common.models.TenantScopedBaseModel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_accounts_tenant_public_id", columnNames = {"tenant_id", "public_id"}),
                @UniqueConstraint(name = "uq_accounts_tenant_code", columnNames = {"tenant_id", "code"}),
                @UniqueConstraint(name = "uq_accounts_tenant_external_ref", columnNames = {"tenant_id", "external_ref"})
        },
        indexes = {
                @Index(name = "idx_accounts_tenant", columnList = "tenant_id"),
                @Index(name = "idx_accounts_code", columnList = "tenant_id,code"),
                @Index(name = "idx_accounts_external_ref", columnList = "tenant_id,external_ref")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountEntity extends TenantScopedBaseModel {

    // External safe identifier
    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    // Account code (JP-5466)
    @Column(name = "code", nullable = false, updatable = false, length = 32)
    private String code;

    /**
     * External stable identifier from the portal (future).
     * Example: "PORTAL:ORG:12345" or "PORTAL:USER:abc"
     */
    @Column(name = "external_ref", nullable = false, updatable = false, length = 64)
    private String externalRef;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
