package com.menval.couriererp.tenant.entities;

import com.menval.couriererp.modules.common.models.BaseModel;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * API key for authenticating programmatic calls to /api/public/** and /api/integration/**.
 * Stored by key hash only; the raw key is returned once on creation.
 * Not tenant-scoped in the JPA sense so we can look up by hash without tenant context.
 */
@Entity
@Table(name = "api_keys", indexes = {
        @Index(name = "idx_api_keys_key_hash", columnList = "key_hash", unique = true),
        @Index(name = "idx_api_keys_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyEntity extends BaseModel {

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "key_hash", nullable = false, length = 64, unique = true)
    private String keyHash;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "suspended", nullable = false)
    @Builder.Default
    private boolean suspended = false;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    public boolean isSuspended() {
        return suspended;
    }
}
