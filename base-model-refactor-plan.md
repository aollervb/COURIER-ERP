# TenantEntity and BaseModel: The Design Problem & Solution

## The Problem You Identified

You're right that `TenantEntity` should benefit from `BaseModel`'s features:
- `createdAt` / `updatedAt` (auditing)
- `version` (optimistic locking)
- Consistent ID generation

However, there's a **circular dependency problem** with the current `BaseModel`:

```java
@MappedSuperclass
public class BaseModel {
    @TenantId  // ⚠️ THIS IS THE PROBLEM
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;
    
    // ... other fields
}
```

**The Issue:**
- `BaseModel` has a `tenantId` field with `@TenantId`
- `TenantEntity` defines tenants themselves
- **A tenant cannot belong to a tenant!** (Logical impossibility)

---

## Solution: Create Two Base Classes

We need to separate concerns:
1. **Entities that belong to a tenant** → Use `TenantScopedBaseModel`
2. **Platform-level entities** (Tenants, System Config) → Use `BaseModel`

---

## Refactored Implementation

### 1. Base Model (No Tenant)

```java
package com.menval.couriererp.modules.common.models;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base class for ALL entities.
 * Provides: ID, timestamps, versioning.
 * Does NOT include tenant_id - use TenantScopedBaseModel for that.
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
    
    // Getters
    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
    
    // For JPA/Hibernate
    protected void setId(Long id) { this.id = id; }
}
```

### 2. Tenant-Scoped Base Model

```java
package com.menval.couriererp.modules.common.models;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

/**
 * Base class for entities that belong to a specific tenant.
 * Extends BaseModel and adds tenant_id with Hibernate's @TenantId.
 * 
 * Use this for: AccountEntity, PackageEntity, etc.
 */
@MappedSuperclass
public abstract class TenantScopedBaseModel extends BaseModel {
    
    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;
    
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
```

### 3. Tenant Entity (Extends BaseModel)

```java
package com.menval.couriererp.tenant.entities;

import com.menval.couriererp.modules.common.models.BaseModel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tenant entity - represents a customer/organization.
 * Extends BaseModel (NOT TenantScopedBaseModel) because tenants don't belong to tenants.
 */
@Entity
@Table(name = "tenants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantEntity extends BaseModel {  // ✅ Extends BaseModel
    
    // tenant_id is the PRIMARY KEY, not a foreign key
    @Column(name = "tenant_id", nullable = false, unique = true, length = 64)
    private String tenantId;
    
    @Column(nullable = false, length = 200)
    private String companyName;
    
    @Column(unique = true, length = 100)
    private String domain;
    
    @Column(nullable = false)
    private boolean active = true;
    
    // Subscription management
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status = TenantStatus.TRIAL;
    
    private Instant subscriptionStartsAt;
    private Instant subscriptionExpiresAt;
    
    @Enumerated(EnumType.STRING)
    private SubscriptionPlan plan;
    
    // Tenant-specific configuration
    @Embedded
    private TenantSettings settings;
    
    // Contact information
    @Column(length = 320)
    private String primaryContactEmail;
    
    @Column(length = 40)
    private String primaryContactPhone;
    
    @Column(length = 200)
    private String primaryContactName;
    
    // Billing
    @Column(length = 100)
    private String billingEmail;
    
    // ✅ Now gets from BaseModel:
    // - id (Long)
    // - createdAt (Instant)
    // - updatedAt (Instant)
    // - version (Long)
    
    public boolean isExpired() {
        return subscriptionExpiresAt != null && 
               Instant.now().isAfter(subscriptionExpiresAt);
    }
    
    public boolean canAccess() {
        return active && !isExpired() && status != TenantStatus.SUSPENDED;
    }
}
```

### 4. Update Your Domain Entities

Now update your existing entities to use `TenantScopedBaseModel`:

```java
package com.menval.couriererp.modules.courier.account.entities;

import com.menval.couriererp.modules.common.models.TenantScopedBaseModel;  // ✅ Changed
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "accounts",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_accounts_tenant_public_id", 
                         columnNames = {"tenant_id", "public_id"}),
        @UniqueConstraint(name = "uq_accounts_tenant_code", 
                         columnNames = {"tenant_id", "code"}),
        @UniqueConstraint(name = "uq_accounts_tenant_external_ref", 
                         columnNames = {"tenant_id", "external_ref"})
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
public class AccountEntity extends TenantScopedBaseModel {  // ✅ Changed from BaseModel
    
    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;
    
    @Column(name = "code", nullable = false, updatable = false, length = 32)
    private String code;
    
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
```

```java
package com.menval.couriererp.modules.courier.packages.entities;

import com.menval.couriererp.modules.common.models.TenantScopedBaseModel;  // ✅ Changed
import com.menval.couriererp.modules.courier.account.entities.AccountEntity;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(
    name = "packages",
    indexes = {
        @Index(name = "idx_packages_tracking", columnList = "carrier,originalTrackingNumber"),
        @Index(name = "idx_packages_owner_account", columnList = "owner_account_id"),
        @Index(name = "idx_packages_status", columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_packages_tenant_carrier_tracking", 
                         columnNames = {"tenant_id", "carrier", "originalTrackingNumber"})
    }
)
@Data
public class PackageEntity extends TenantScopedBaseModel {  // ✅ Changed from BaseModel
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Carrier carrier = Carrier.UNKNOWN;
    
    @Column(nullable = false, length = 64)
    private String originalTrackingNumber;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_account_id")
    private AccountEntity owner;
    
    @Column(nullable = true, length = 32)
    private String internalCode;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PackageStatus status = PackageStatus.RECEIVED_US_UNASSIGNED;
    
    @Column(nullable = false, updatable = false)
    private Instant receivedAt;
    
    @Column(nullable = false)
    private Instant lastSeenAt;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_notice_id")
    private InboundNotice inboundNotice;
    
    private int weightGrams;
    private int lengthCm;
    private int widthCm;
    private int heightCm;
    
    public void markReceivedNow(Instant now) {
        if (this.receivedAt == null) this.receivedAt = now;
        this.lastSeenAt = now;
    }
    
    public boolean isAssigned() {
        return owner != null;
    }
}
```

### 5. Update BaseUser

```java
package com.menval.couriererp.auth.models;

import com.menval.couriererp.modules.common.models.TenantScopedBaseModel;  // ✅ Changed
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * User entity.
 * Extends TenantScopedBaseModel because users belong to tenants.
 * Exception: SUPER_ADMIN users have tenant_id = 'system'
 */
@Entity
@Table(name = "users", 
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_users_tenant_email", 
                         columnNames = {"tenant_id", "email"})
    },
    indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_tenant", columnList = "tenant_id")
    }
)
@Builder
@AllArgsConstructor
public class BaseUser extends TenantScopedBaseModel implements UserDetails {  // ✅ Changed
    
    @Column(nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String firstName;
    
    @Column(nullable = false)
    private String lastName;
    
    @Column(nullable = false)
    private String password;
    
    private boolean enabled;
    private boolean accountNonExpired;
    private boolean accountNonLocked;
    private boolean credentialsNonExpired;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        uniqueConstraints = @UniqueConstraint(
            name = "uq_user_roles", 
            columnNames = {"user_id", "role"}
        )
    )
    @Column(name = "role", nullable = false)
    private Set<UserRoles> roles = new HashSet<>();

    public BaseUser() {}

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();
    }

    // Expose tenant ID for security context
    public String getUserTenantId() {
        return this.getTenantId();  // ✅ Inherited from TenantScopedBaseModel
    }
    
    public boolean isSuperAdmin() {
        return roles.contains(UserRoles.SUPER_ADMIN);
    }
    
    public boolean isTenantAdmin() {
        return roles.contains(UserRoles.ADMIN);
    }

    // Standard getters/setters
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    @Override
    public String getPassword() { return password; }
    
    @Override
    public String getUsername() { return email; }
    
    @Override
    public boolean isAccountNonExpired() { return accountNonExpired; }
    
    @Override
    public boolean isAccountNonLocked() { return accountNonLocked; }
    
    @Override
    public boolean isCredentialsNonExpired() { return credentialsNonExpired; }
    
    @Override
    public boolean isEnabled() { return enabled; }
    
    public void eraseCredentials() { this.password = null; }
    
    public Set<UserRoles> getRoles() { return roles; }
    public void setRoles(Set<UserRoles> roles) { this.roles = roles; }
    public String getEmail() { return email; }
}
```

---

## Database Schema Comparison

### Before (Wrong)

```sql
-- Tenants table
CREATE TABLE tenants (
    tenant_id VARCHAR(64) PRIMARY KEY,  -- ❌ No id, no timestamps, no version
    company_name VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,  -- ❌ Manually defined
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL   -- ❌ Manually defined
);

-- Users table
CREATE TABLE users (
    id BIGINT PRIMARY KEY,              -- ✅ Has id
    tenant_id VARCHAR(64) NOT NULL,     -- ✅ Has tenant_id
    created_at TIMESTAMP,               -- ✅ Has timestamps
    updated_at TIMESTAMP,
    version BIGINT                      -- ✅ Has version
);
```

### After (Correct)

```sql
-- Tenants table
CREATE TABLE tenants (
    id BIGINT PRIMARY KEY,                    -- ✅ From BaseModel
    tenant_id VARCHAR(64) NOT NULL UNIQUE,    -- ✅ Business identifier
    company_name VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,      -- ✅ From BaseModel
    updated_at TIMESTAMP WITH TIME ZONE,      -- ✅ From BaseModel
    version BIGINT                            -- ✅ From BaseModel (optimistic locking)
);

-- Users table  
CREATE TABLE users (
    id BIGINT PRIMARY KEY,                    -- ✅ From BaseModel
    tenant_id VARCHAR(64) NOT NULL,           -- ✅ From TenantScopedBaseModel
    email VARCHAR(320) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,      -- ✅ From BaseModel
    updated_at TIMESTAMP WITH TIME ZONE,      -- ✅ From BaseModel
    version BIGINT,                           -- ✅ From BaseModel
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email)
);
```

---

## Benefits of This Approach

### ✅ Consistency
All entities now have:
- Numeric ID (good for joins, indexes)
- Created/updated timestamps
- Optimistic locking

### ✅ Flexibility
- Platform entities: Extend `BaseModel`
- Tenant-scoped entities: Extend `TenantScopedBaseModel`

### ✅ Clear Semantics
```java
// ❌ Before - Confusing
public class TenantEntity extends BaseModel {
    // Wait, does this tenant have a tenant_id? 🤔
}

// ✅ After - Clear
public class TenantEntity extends BaseModel {
    // No tenant_id - this IS a tenant
}

public class AccountEntity extends TenantScopedBaseModel {
    // Has tenant_id - belongs to a tenant
}
```

### ✅ Better Queries
```java
// Can use numeric ID for efficient joins
@ManyToOne
@JoinColumn(name = "tenant_entity_id")
private TenantEntity tenant;

// But still have business identifier for lookup
TenantEntity tenant = tenantRepository.findByTenantId("acme-corp");
```

### ✅ Audit Trail
```sql
-- When was this tenant created?
SELECT created_at FROM tenants WHERE tenant_id = 'acme-corp';

-- When was it last updated?
SELECT updated_at FROM tenants WHERE tenant_id = 'acme-corp';

-- Detect concurrent updates
UPDATE tenants 
SET company_name = 'New Name', version = version + 1
WHERE id = 123 AND version = 5;  -- ✅ Optimistic locking
```

---

## Migration Strategy

### Step 1: Create New Base Classes

```java
// Create BaseModel (without @TenantId)
// Create TenantScopedBaseModel (with @TenantId)
```

### Step 2: Update TenantEntity

```java
// Change to extend BaseModel
@Entity
public class TenantEntity extends BaseModel {
    @Column(name = "tenant_id", unique = true)
    private String tenantId;  // Business key, not FK
    // ...
}
```

### Step 3: Update All Domain Entities

```java
// Change all from BaseModel to TenantScopedBaseModel
public class AccountEntity extends TenantScopedBaseModel { }
public class PackageEntity extends TenantScopedBaseModel { }
public class BaseUser extends TenantScopedBaseModel { }
```

### Step 4: Database Migration

```sql
-- Add id, timestamps, version to existing tenants table
ALTER TABLE tenants ADD COLUMN id BIGINT;
ALTER TABLE tenants ADD COLUMN created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
ALTER TABLE tenants ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
ALTER TABLE tenants ADD COLUMN version BIGINT DEFAULT 0;

-- Create sequence for tenant IDs
CREATE SEQUENCE tenant_id_seq START WITH 1;

-- Backfill IDs
UPDATE tenants SET id = nextval('tenant_id_seq') WHERE id IS NULL;

-- Make id primary key (requires recreating PK)
ALTER TABLE tenants DROP CONSTRAINT tenants_pkey;
ALTER TABLE tenants ADD PRIMARY KEY (id);
ALTER TABLE tenants ADD CONSTRAINT uq_tenants_tenant_id UNIQUE (tenant_id);

-- Update defaults
ALTER TABLE tenants ALTER COLUMN id SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN updated_at SET NOT NULL;
```

---

## Summary

**Why This Matters:**

1. **Consistency** - All entities follow same patterns
2. **Auditing** - Know when tenants were created/modified
3. **Concurrency** - Optimistic locking prevents data loss
4. **Performance** - Numeric IDs are faster for joins
5. **Clarity** - Clear distinction between platform and tenant entities

**The Rule:**
- Tenant-scoped data? → `TenantScopedBaseModel`
- Platform-level data? → `BaseModel`

This is a cleaner, more maintainable architecture that follows domain-driven design principles.
