package com.menval.couriererp.modules.common.models;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base class for all entities. Provides: ID, timestamps, versioning.
 * Does NOT include tenant_id — use {@link TenantScopedBaseModel} for tenant-scoped entities.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "global_seq")
    @SequenceGenerator(
            name = "global_seq",
            sequenceName = "global_seq",
            allocationSize = 50
    )
    private Long id;

    @CreatedDate
    @Column(nullable = false, updatable = false,
            columnDefinition = "timestamp with time zone default now()")
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false,
            columnDefinition = "timestamp with time zone default now()")
    private Instant updatedAt;

    @Version
    private Long version;

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    protected void setId(Long id) { this.id = id; }
}
