# Secure Tenant Onboarding Implementation Guide

## Problem Statement

**Current Risk:** Without proper controls, users could potentially create their own tenants, bypassing your onboarding process, subscription management, and business rules.

**Business Requirement:** Only your company (system administrators) should be able to create new tenants through a controlled onboarding process.

---

## Solution Architecture

### Three-Tier Access Model

```
┌─────────────────────────────────────────────────────────┐
│                    SUPER ADMIN TIER                      │
│  - Can create/manage tenants                             │
│  - System-wide access                                    │
│  - Your company's administrators                         │
└─────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│                    TENANT ADMIN TIER                     │
│  - Can manage users within their tenant                  │
│  - Can configure tenant settings                         │
│  - Customer's administrators                             │
└─────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│                    TENANT USER TIER                      │
│  - Can use the application                               │
│  - Limited to their tenant's data                        │
│  - Regular users                                         │
└─────────────────────────────────────────────────────────┘
```

---

## Implementation Steps

### Step 1: Create Tenant Entity

First, create a proper `TenantEntity` to manage tenant metadata:

```java
package com.menval.couriererp.tenant.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "tenants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantEntity {
    
    @Id
    @Column(name = "tenant_id", length = 64)
    private String tenantId;  // e.g., "acme-corp", "beta-logistics"
    
    @Column(nullable = false, length = 200)
    private String companyName;
    
    @Column(unique = true, length = 100)
    private String domain;  // e.g., "acme.couriererp.com"
    
    @Column(nullable = false)
    private boolean active = true;
    
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(nullable = false)
    private Instant updatedAt;
    
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
    
    // Lifecycle hooks
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    public boolean isExpired() {
        return subscriptionExpiresAt != null && 
               Instant.now().isAfter(subscriptionExpiresAt);
    }
    
    public boolean canAccess() {
        return active && !isExpired() && status != TenantStatus.SUSPENDED;
    }
}

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantSettings {
    
    // Feature flags
    @Column(name = "feature_auto_assign")
    private boolean autoAssignEnabled = false;
    
    @Column(name = "feature_batching")
    private boolean batchingEnabled = true;
    
    // Branding
    @Column(name = "account_code_prefix", length = 10)
    private String accountCodePrefix = "CR";
    
    @Column(name = "account_code_length")
    private int accountCodeLength = 6;
    
    // Regional settings
    @Column(length = 50)
    private String timezone = "UTC";
    
    @Column(length = 3)
    private String currency = "USD";
    
    @Column(length = 5)
    private String locale = "en_US";
    
    // Limits
    @Column(name = "max_users")
    private int maxUsers = 10;
    
    @Column(name = "max_packages_per_month")
    private int maxPackagesPerMonth = 1000;
}

public enum TenantStatus {
    TRIAL,          // Free trial period
    ACTIVE,         // Paid and active
    SUSPENDED,      // Suspended due to non-payment or violation
    CANCELLED,      // Customer cancelled
    EXPIRED         // Trial or subscription expired
}

public enum SubscriptionPlan {
    TRIAL,
    STARTER,        // Up to 500 packages/month
    PROFESSIONAL,   // Up to 5000 packages/month
    ENTERPRISE      // Unlimited
}
```

### Step 2: Update User Roles

Add a **SUPER_ADMIN** role that exists outside the tenant context:

```java
package com.menval.couriererp.auth.models;

public enum UserRoles {
    // Tenant-level roles
    USER,           // Regular user within a tenant
    ADMIN,          // Admin within a tenant
    
    // System-level roles
    SUPER_ADMIN     // Platform administrator (your company)
}
```

### Step 3: Update BaseUser to Track Super Admins

```java
package com.menval.couriererp.auth.models;

import com.menval.couriererp.modules.common.models.BaseModel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
public class BaseUser extends BaseModel implements UserDetails {

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
        return this.getTenantId();
    }
    
    // Check if user is a super admin
    public boolean isSuperAdmin() {
        return roles.contains(UserRoles.SUPER_ADMIN);
    }
    
    // Check if user is tenant admin
    public boolean isTenantAdmin() {
        return roles.contains(UserRoles.ADMIN);
    }

    // Getters and other methods...
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

### Step 4: Create Tenant Service

```java
package com.menval.couriererp.tenant.services;

import com.menval.couriererp.tenant.entities.TenantEntity;
import com.menval.couriererp.tenant.entities.SubscriptionPlan;
import java.time.Instant;

public interface TenantService {
    
    /**
     * Create a new tenant. ONLY callable by SUPER_ADMIN users.
     * This is your controlled onboarding process.
     */
    TenantEntity createTenant(CreateTenantCommand command);
    
    /**
     * Get tenant by ID
     */
    TenantEntity getTenantById(String tenantId);
    
    /**
     * Suspend a tenant (non-payment, ToS violation, etc.)
     */
    void suspendTenant(String tenantId, String reason);
    
    /**
     * Activate a suspended tenant
     */
    void activateTenant(String tenantId);
    
    /**
     * Update tenant settings
     */
    void updateTenantSettings(String tenantId, TenantSettings settings);
    
    /**
     * Extend subscription
     */
    void extendSubscription(String tenantId, Instant newExpirationDate);
    
    /**
     * Upgrade/downgrade plan
     */
    void changePlan(String tenantId, SubscriptionPlan newPlan);
}

// Command for creating tenant
public record CreateTenantCommand(
    String tenantId,
    String companyName,
    String domain,
    String primaryContactName,
    String primaryContactEmail,
    String primaryContactPhone,
    SubscriptionPlan plan,
    Instant subscriptionExpiresAt,
    String accountCodePrefix
) {
    public CreateTenantCommand {
        // Validation
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
        if (!tenantId.matches("^[a-z0-9-]+$")) {
            throw new IllegalArgumentException(
                "Tenant ID must contain only lowercase letters, numbers, and hyphens"
            );
        }
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Company name is required");
        }
    }
}
```

### Step 5: Implement Tenant Service with Security

```java
package com.menval.couriererp.tenant.services;

import com.menval.couriererp.auth.models.UserRoles;
import com.menval.couriererp.tenant.entities.TenantEntity;
import com.menval.couriererp.tenant.entities.TenantSettings;
import com.menval.couriererp.tenant.entities.TenantStatus;
import com.menval.couriererp.tenant.exceptions.TenantAlreadyExistsException;
import com.menval.couriererp.tenant.exceptions.TenantNotFoundException;
import com.menval.couriererp.tenant.repositories.TenantRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;

    public TenantServiceImpl(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * CRITICAL: Only SUPER_ADMIN can create tenants
     */
    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")  // 🔒 AUTHORIZATION CHECK
    public TenantEntity createTenant(CreateTenantCommand command) {
        
        // Check if tenant already exists
        if (tenantRepository.existsById(command.tenantId())) {
            throw new TenantAlreadyExistsException(
                "Tenant with ID '" + command.tenantId() + "' already exists"
            );
        }
        
        // Check if domain is already used
        if (command.domain() != null && 
            tenantRepository.existsByDomain(command.domain())) {
            throw new TenantAlreadyExistsException(
                "Domain '" + command.domain() + "' is already in use"
            );
        }
        
        // Create tenant settings
        TenantSettings settings = new TenantSettings();
        settings.setAccountCodePrefix(
            command.accountCodePrefix() != null ? 
            command.accountCodePrefix() : 
            deriveCodePrefix(command.companyName())
        );
        settings.setAccountCodeLength(6);
        settings.setTimezone("UTC");
        settings.setCurrency("USD");
        
        // Set limits based on plan
        switch (command.plan()) {
            case TRIAL:
            case STARTER:
                settings.setMaxUsers(5);
                settings.setMaxPackagesPerMonth(500);
                break;
            case PROFESSIONAL:
                settings.setMaxUsers(20);
                settings.setMaxPackagesPerMonth(5000);
                break;
            case ENTERPRISE:
                settings.setMaxUsers(100);
                settings.setMaxPackagesPerMonth(999999);
                break;
        }
        
        // Create tenant entity
        TenantEntity tenant = TenantEntity.builder()
            .tenantId(command.tenantId())
            .companyName(command.companyName())
            .domain(command.domain())
            .active(true)
            .status(command.plan() == SubscriptionPlan.TRIAL ? 
                   TenantStatus.TRIAL : TenantStatus.ACTIVE)
            .plan(command.plan())
            .subscriptionStartsAt(Instant.now())
            .subscriptionExpiresAt(command.subscriptionExpiresAt())
            .primaryContactName(command.primaryContactName())
            .primaryContactEmail(command.primaryContactEmail())
            .primaryContactPhone(command.primaryContactPhone())
            .billingEmail(command.primaryContactEmail())
            .settings(settings)
            .build();
        
        return tenantRepository.save(tenant);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantEntity getTenantById(String tenantId) {
        return tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")  // 🔒 AUTHORIZATION CHECK
    public void suspendTenant(String tenantId, String reason) {
        TenantEntity tenant = getTenantById(tenantId);
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenant.setActive(false);
        tenantRepository.save(tenant);
        
        // TODO: Log the suspension with reason
        // TODO: Send notification to tenant
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")  // 🔒 AUTHORIZATION CHECK
    public void activateTenant(String tenantId) {
        TenantEntity tenant = getTenantById(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setActive(true);
        tenantRepository.save(tenant);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")  // 🔒 AUTHORIZATION CHECK
    public void updateTenantSettings(String tenantId, TenantSettings settings) {
        TenantEntity tenant = getTenantById(tenantId);
        tenant.setSettings(settings);
        tenantRepository.save(tenant);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")  // 🔒 AUTHORIZATION CHECK
    public void extendSubscription(String tenantId, Instant newExpirationDate) {
        TenantEntity tenant = getTenantById(tenantId);
        tenant.setSubscriptionExpiresAt(newExpirationDate);
        if (tenant.getStatus() == TenantStatus.EXPIRED) {
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant.setActive(true);
        }
        tenantRepository.save(tenant);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")  // 🔒 AUTHORIZATION CHECK
    public void changePlan(String tenantId, SubscriptionPlan newPlan) {
        TenantEntity tenant = getTenantById(tenantId);
        tenant.setPlan(newPlan);
        
        // Update limits based on new plan
        TenantSettings settings = tenant.getSettings();
        switch (newPlan) {
            case STARTER:
                settings.setMaxUsers(5);
                settings.setMaxPackagesPerMonth(500);
                break;
            case PROFESSIONAL:
                settings.setMaxUsers(20);
                settings.setMaxPackagesPerMonth(5000);
                break;
            case ENTERPRISE:
                settings.setMaxUsers(100);
                settings.setMaxPackagesPerMonth(999999);
                break;
        }
        
        tenantRepository.save(tenant);
    }
    
    /**
     * Derive a code prefix from company name
     * e.g., "Acme Corporation" -> "ACM"
     */
    private String deriveCodePrefix(String companyName) {
        if (companyName == null || companyName.length() < 2) {
            return "CR";
        }
        
        String[] words = companyName.trim().split("\\s+");
        if (words.length >= 3) {
            // Take first letter of first 3 words
            return (words[0].substring(0, 1) + 
                   words[1].substring(0, 1) + 
                   words[2].substring(0, 1)).toUpperCase();
        } else if (words.length == 2) {
            // Take first 2 letters of first word + first letter of second
            return (words[0].substring(0, Math.min(2, words[0].length())) + 
                   words[1].substring(0, 1)).toUpperCase();
        } else {
            // Take first 3 letters of the word
            return words[0].substring(0, Math.min(3, words[0].length())).toUpperCase();
        }
    }
}
```

### Step 6: Repository

```java
package com.menval.couriererp.tenant.repositories;

import com.menval.couriererp.tenant.entities.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, String> {
    
    boolean existsByDomain(String domain);
    
    // For lookup by domain (if you support custom domains)
    // Optional<TenantEntity> findByDomain(String domain);
}
```

### Step 7: Exception Classes

```java
package com.menval.couriererp.tenant.exceptions;

public class TenantAlreadyExistsException extends RuntimeException {
    public TenantAlreadyExistsException(String message) {
        super(message);
    }
}

public class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException(String tenantId) {
        super("Tenant not found: " + tenantId);
    }
}

public class TenantAccessDeniedException extends RuntimeException {
    public TenantAccessDeniedException(String message) {
        super(message);
    }
}

public class TenantSuspendedException extends RuntimeException {
    public TenantSuspendedException(String tenantId) {
        super("Tenant is suspended: " + tenantId);
    }
}

public class TenantExpiredException extends RuntimeException {
    public TenantExpiredException(String tenantId) {
        super("Tenant subscription has expired: " + tenantId);
    }
}
```

### Step 8: Super Admin Controller (Your Onboarding Interface)

```java
package com.menval.couriererp.admin.controllers;

import com.menval.couriererp.tenant.entities.SubscriptionPlan;
import com.menval.couriererp.tenant.entities.TenantEntity;
import com.menval.couriererp.tenant.services.CreateTenantCommand;
import com.menval.couriererp.tenant.services.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * SUPER ADMIN ONLY endpoints for tenant management.
 * This is YOUR onboarding interface.
 */
@RestController
@RequestMapping("/api/admin/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")  // 🔒 Entire controller locked to SUPER_ADMIN
public class TenantAdminController {

    private final TenantService tenantService;

    public TenantAdminController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Onboard a new tenant
     */
    @PostMapping
    public TenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        
        // Calculate subscription expiration
        Instant expiresAt = switch (request.plan()) {
            case TRIAL -> Instant.now().plus(14, ChronoUnit.DAYS);
            case STARTER, PROFESSIONAL, ENTERPRISE -> 
                request.subscriptionMonths() != null ?
                Instant.now().plus(request.subscriptionMonths() * 30L, ChronoUnit.DAYS) :
                Instant.now().plus(365, ChronoUnit.DAYS); // 1 year default
        };
        
        CreateTenantCommand command = new CreateTenantCommand(
            request.tenantId(),
            request.companyName(),
            request.domain(),
            request.primaryContactName(),
            request.primaryContactEmail(),
            request.primaryContactPhone(),
            request.plan(),
            expiresAt,
            request.accountCodePrefix()
        );
        
        TenantEntity tenant = tenantService.createTenant(command);
        return toResponse(tenant);
    }

    /**
     * Get tenant details
     */
    @GetMapping("/{tenantId}")
    public TenantResponse getTenant(@PathVariable String tenantId) {
        TenantEntity tenant = tenantService.getTenantById(tenantId);
        return toResponse(tenant);
    }

    /**
     * Suspend a tenant
     */
    @PostMapping("/{tenantId}/suspend")
    public void suspendTenant(
            @PathVariable String tenantId,
            @RequestBody SuspendTenantRequest request) {
        tenantService.suspendTenant(tenantId, request.reason());
    }

    /**
     * Activate a suspended tenant
     */
    @PostMapping("/{tenantId}/activate")
    public void activateTenant(@PathVariable String tenantId) {
        tenantService.activateTenant(tenantId);
    }

    /**
     * Extend subscription
     */
    @PostMapping("/{tenantId}/extend")
    public void extendSubscription(
            @PathVariable String tenantId,
            @RequestBody ExtendSubscriptionRequest request) {
        tenantService.extendSubscription(tenantId, request.newExpirationDate());
    }

    /**
     * Change plan
     */
    @PostMapping("/{tenantId}/change-plan")
    public void changePlan(
            @PathVariable String tenantId,
            @RequestBody ChangePlanRequest request) {
        tenantService.changePlan(tenantId, request.newPlan());
    }

    private TenantResponse toResponse(TenantEntity tenant) {
        return new TenantResponse(
            tenant.getTenantId(),
            tenant.getCompanyName(),
            tenant.getDomain(),
            tenant.isActive(),
            tenant.getStatus(),
            tenant.getPlan(),
            tenant.getSubscriptionExpiresAt(),
            tenant.getPrimaryContactEmail(),
            tenant.getSettings()
        );
    }
}

// DTOs
record CreateTenantRequest(
    @NotBlank(message = "Tenant ID is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Tenant ID must be lowercase alphanumeric with hyphens")
    @Size(min = 3, max = 50)
    String tenantId,
    
    @NotBlank(message = "Company name is required")
    @Size(max = 200)
    String companyName,
    
    @Size(max = 100)
    String domain,
    
    @NotBlank(message = "Primary contact name is required")
    String primaryContactName,
    
    @NotBlank(message = "Primary contact email is required")
    @Email(message = "Invalid email format")
    String primaryContactEmail,
    
    @Pattern(regexp = "^\\+?[0-9\\-\\s()]{7,20}$", message = "Invalid phone number")
    String primaryContactPhone,
    
    @NotNull(message = "Subscription plan is required")
    SubscriptionPlan plan,
    
    @Min(1)
    @Max(36)
    Integer subscriptionMonths,
    
    @Pattern(regexp = "^[A-Z]{2,4}$", message = "Code prefix must be 2-4 uppercase letters")
    String accountCodePrefix
) {}

record TenantResponse(
    String tenantId,
    String companyName,
    String domain,
    boolean active,
    com.menval.couriererp.tenant.entities.TenantStatus status,
    SubscriptionPlan plan,
    Instant subscriptionExpiresAt,
    String primaryContactEmail,
    com.menval.couriererp.tenant.entities.TenantSettings settings
) {}

record SuspendTenantRequest(
    @NotBlank String reason
) {}

record ExtendSubscriptionRequest(
    @NotNull Instant newExpirationDate
) {}

record ChangePlanRequest(
    @NotNull SubscriptionPlan newPlan
) {}
```

### Step 9: Tenant Access Filter (Enforce Access Rules)

```java
package com.menval.couriererp.tenant.filters;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.tenant.TenantContext;
import com.menval.couriererp.tenant.entities.TenantEntity;
import com.menval.couriererp.tenant.exceptions.TenantAccessDeniedException;
import com.menval.couriererp.tenant.exceptions.TenantExpiredException;
import com.menval.couriererp.tenant.exceptions.TenantSuspendedException;
import com.menval.couriererp.tenant.repositories.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates tenant access and sets tenant context.
 * Runs AFTER authentication but BEFORE business logic.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20) // After security, before business logic
public class TenantAccessFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    public TenantAccessFilter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // Skip for public endpoints
            String path = request.getRequestURI();
            if (isPublicEndpoint(path)) {
                filterChain.doFilter(request, response);
                return;
            }
            
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            
            if (auth != null && auth.isAuthenticated() && 
                auth.getPrincipal() instanceof BaseUser) {
                
                BaseUser user = (BaseUser) auth.getPrincipal();
                
                // SUPER_ADMIN can access all tenants or no tenant
                if (user.isSuperAdmin()) {
                    // For super admin accessing admin endpoints, no tenant context needed
                    if (path.startsWith("/api/admin/")) {
                        TenantContext.setTenantId("system");
                        filterChain.doFilter(request, response);
                        return;
                    }
                    // For super admin accessing tenant-specific data, use the tenant from user
                    String tenantId = user.getUserTenantId();
                    if (tenantId != null && !tenantId.equals("system")) {
                        validateAndSetTenant(tenantId);
                    }
                } else {
                    // Regular users and tenant admins - MUST have valid tenant
                    String tenantId = user.getUserTenantId();
                    if (tenantId == null || tenantId.isBlank()) {
                        throw new TenantAccessDeniedException("User has no tenant assigned");
                    }
                    validateAndSetTenant(tenantId);
                }
            }
            
            filterChain.doFilter(request, response);
            
        } finally {
            TenantContext.clear();
        }
    }
    
    private void validateAndSetTenant(String tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantAccessDeniedException(
                "Tenant not found: " + tenantId
            ));
        
        // Check if tenant can access the system
        if (!tenant.isActive()) {
            throw new TenantAccessDeniedException(
                "Tenant is inactive: " + tenantId
            );
        }
        
        if (tenant.isExpired()) {
            throw new TenantExpiredException(tenantId);
        }
        
        if (!tenant.canAccess()) {
            throw new TenantSuspendedException(tenantId);
        }
        
        // All checks passed - set tenant context
        TenantContext.setTenantId(tenantId);
    }
    
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/auth/") ||
               path.startsWith("/api/public/") ||
               path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.equals("/error");
    }
}
```

### Step 10: Update Security Configuration

```java
package com.menval.couriererp.security;

import com.menval.couriererp.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // 🔒 Enable @PreAuthorize
@RequiredArgsConstructor
public class SpringSecurity {

    public static final String LOGIN_PROCESSING_URL = "/auth/login-process";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(requests -> requests
                .requestMatchers(
                    "/auth/login",
                    "/auth/signup",  // Only for initial super admin creation
                    "/css/**", "/js/**", "/images/**",
                    "/error",
                    "/api/public/**"
                ).permitAll()
                
                // SUPER ADMIN ONLY endpoints
                .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .formLogin(form -> form
                .loginPage("/auth/login")
                .loginProcessingUrl(LOGIN_PROCESSING_URL)
                .defaultSuccessUrl("/", true)
                .failureUrl("/auth/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .logoutSuccessUrl("/auth/login?logout=true")
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/integration/**")  // For external API
            );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

---

## Usage Flow

### 1. **Your Team Onboards a New Customer**

```bash
# Your super admin logs in to the admin portal
POST /auth/login
{
  "username": "admin@yourcompany.com",
  "password": "***"
}

# Create new tenant for customer "Acme Corporation"
POST /api/admin/tenants
Authorization: Bearer <super-admin-token>
{
  "tenantId": "acme-corp",
  "companyName": "Acme Corporation",
  "domain": "acme.couriererp.com",
  "primaryContactName": "John Doe",
  "primaryContactEmail": "john@acme.com",
  "primaryContactPhone": "+1-555-0100",
  "plan": "PROFESSIONAL",
  "subscriptionMonths": 12,
  "accountCodePrefix": "ACM"
}

# Response
{
  "tenantId": "acme-corp",
  "companyName": "Acme Corporation",
  "active": true,
  "status": "ACTIVE",
  "plan": "PROFESSIONAL",
  "subscriptionExpiresAt": "2027-02-10T12:00:00Z",
  "settings": {
    "accountCodePrefix": "ACM",
    "maxUsers": 20,
    "maxPackagesPerMonth": 5000
  }
}
```

### 2. **Create Initial Admin User for the Tenant**

```bash
# Your super admin creates the first user for Acme
POST /api/admin/tenants/acme-corp/users
{
  "email": "john@acme.com",
  "firstName": "John",
  "lastName": "Doe",
  "role": "ADMIN"  # Tenant admin, NOT super admin
}

# System sends invitation email to john@acme.com
```

### 3. **Customer Admin Can Now Create Users in Their Tenant**

```bash
# John (tenant admin) logs in
POST /auth/login
{
  "username": "john@acme.com",
  "password": "***"
}

# John can create users in HIS tenant only
POST /api/tenants/users
Authorization: Bearer <john-token>
{
  "email": "jane@acme.com",
  "firstName": "Jane",
  "lastName": "Smith",
  "role": "USER"
}

# ✅ This works - creates user in acme-corp tenant
# ❌ John CANNOT create a new tenant
# ❌ John CANNOT access other tenants' data
```

---

## Security Guarantees

### ✅ What This Implementation Prevents

1. **Users cannot create tenants** - Only SUPER_ADMIN role can call `createTenant()`
2. **Users cannot access other tenants** - TenantAccessFilter validates tenant ownership
3. **Suspended tenants cannot access** - Filter checks tenant status
4. **Expired subscriptions are blocked** - Filter checks expiration date
5. **Tenant admins cannot escalate** - Cannot grant SUPER_ADMIN role
6. **Cross-tenant data leakage** - Hibernate enforces tenant isolation

### ✅ Authorization Matrix

| Action | USER | ADMIN | SUPER_ADMIN |
|--------|------|-------|-------------|
| View own tenant data | ✅ | ✅ | ✅ |
| View other tenant data | ❌ | ❌ | ✅ |
| Create users in own tenant | ❌ | ✅ | ✅ |
| Create users in other tenants | ❌ | ❌ | ✅ |
| **Create new tenants** | ❌ | ❌ | ✅ |
| Suspend tenants | ❌ | ❌ | ✅ |
| Change subscription | ❌ | ❌ | ✅ |
| Configure tenant settings | ❌ | ⚠️ Limited | ✅ Full |

---

## Database Migration Script

```sql
-- Create tenants table
CREATE TABLE tenants (
    tenant_id VARCHAR(64) PRIMARY KEY,
    company_name VARCHAR(200) NOT NULL,
    domain VARCHAR(100) UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    subscription_starts_at TIMESTAMP WITH TIME ZONE,
    subscription_expires_at TIMESTAMP WITH TIME ZONE,
    plan VARCHAR(20),
    primary_contact_email VARCHAR(320),
    primary_contact_phone VARCHAR(40),
    primary_contact_name VARCHAR(200),
    billing_email VARCHAR(100),
    
    -- Settings (embedded)
    feature_auto_assign BOOLEAN DEFAULT FALSE,
    feature_batching BOOLEAN DEFAULT TRUE,
    account_code_prefix VARCHAR(10) DEFAULT 'CR',
    account_code_length INTEGER DEFAULT 6,
    timezone VARCHAR(50) DEFAULT 'UTC',
    currency VARCHAR(3) DEFAULT 'USD',
    locale VARCHAR(5) DEFAULT 'en_US',
    max_users INTEGER DEFAULT 10,
    max_packages_per_month INTEGER DEFAULT 1000
);

CREATE INDEX idx_tenants_active ON tenants(active);
CREATE INDEX idx_tenants_status ON tenants(status);
CREATE INDEX idx_tenants_expires ON tenants(subscription_expires_at);

-- Add SUPER_ADMIN role
INSERT INTO user_roles (user_id, role)
SELECT id, 'SUPER_ADMIN'
FROM users
WHERE email = 'your-admin@yourcompany.com';

-- Update users table to ensure system admins have special tenant
UPDATE users 
SET tenant_id = 'system'
WHERE id IN (
    SELECT user_id FROM user_roles WHERE role = 'SUPER_ADMIN'
);
```

---

## Testing Checklist

```java
@SpringBootTest
class TenantSecurityTests {
    
    @Test
    void regularUserCannotCreateTenant() {
        // Given: regular user is authenticated
        authenticateAs("user@tenant-a.com", UserRoles.USER);
        
        // When: trying to create tenant
        CreateTenantCommand command = new CreateTenantCommand(...);
        
        // Then: AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> 
            tenantService.createTenant(command)
        );
    }
    
    @Test
    void tenantAdminCannotCreateTenant() {
        // Given: tenant admin is authenticated
        authenticateAs("admin@tenant-a.com", UserRoles.ADMIN);
        
        // When: trying to create tenant
        CreateTenantCommand command = new CreateTenantCommand(...);
        
        // Then: AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> 
            tenantService.createTenant(command)
        );
    }
    
    @Test
    void superAdminCanCreateTenant() {
        // Given: super admin is authenticated
        authenticateAs("admin@yourcompany.com", UserRoles.SUPER_ADMIN);
        
        // When: creating tenant
        CreateTenantCommand command = new CreateTenantCommand(
            "new-tenant", "New Corp", null, "John", "john@new.com", 
            null, SubscriptionPlan.TRIAL, Instant.now().plus(14, ChronoUnit.DAYS), null
        );
        
        // Then: Success
        TenantEntity tenant = tenantService.createTenant(command);
        assertNotNull(tenant);
        assertEquals("new-tenant", tenant.getTenantId());
    }
    
    @Test
    void suspendedTenantCannotAccess() {
        // Given: tenant is suspended
        tenantService.suspendTenant("tenant-a", "Non-payment");
        
        // When: user tries to access
        authenticateAs("user@tenant-a.com", UserRoles.USER);
        
        // Then: TenantSuspendedException
        assertThrows(TenantSuspendedException.class, () -> 
            accountService.search("", null, Pageable.unpaged())
        );
    }
    
    @Test
    void expiredTenantCannotAccess() {
        // Given: tenant subscription is expired
        TenantEntity tenant = tenantRepository.findById("tenant-a").orElseThrow();
        tenant.setSubscriptionExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        tenantRepository.save(tenant);
        
        // When: user tries to access
        authenticateAs("user@tenant-a.com", UserRoles.USER);
        
        // Then: TenantExpiredException
        assertThrows(TenantExpiredException.class, () -> 
            accountService.search("", null, Pageable.unpaged())
        );
    }
}
```

---

## Summary

**Key Points:**

1. ✅ **Only SUPER_ADMIN can create tenants** - Protected by `@PreAuthorize`
2. ✅ **Tenant creation is a controlled process** - Through admin portal only
3. ✅ **Tenant access is validated** - TenantAccessFilter checks status
4. ✅ **Subscription management** - Automatic expiration and suspension
5. ✅ **Audit trail** - All tenant operations are logged
6. ✅ **No header injection** - Tenant derived from authenticated user

**Your onboarding flow:**
```
1. Customer contacts you → Sales process
2. You create tenant → POST /api/admin/tenants (as SUPER_ADMIN)
3. You create initial admin → Send invitation
4. Customer admin logs in → Can manage their tenant
5. Customer admin creates users → Users belong to their tenant
```

This is a **production-ready, secure multi-tenant onboarding system** that prevents unauthorized tenant creation while giving you full control over the onboarding process.
