# COURIER-ERP Architecture Assessment

**Date:** February 10, 2026  
**Project:** COURIER-ERP (com.menval.couriererp)  
**Version:** 0.0.1-SNAPSHOT  
**Tech Stack:** Spring Boot 4.0.2, Java 21, PostgreSQL  

---

## Executive Summary

COURIER-ERP is a multi-tenant courier package management system built with Spring Boot. The application demonstrates solid fundamentals in multi-tenancy implementation and follows many enterprise patterns. However, there are critical security concerns, architectural gaps, and best practice violations that need immediate attention before production deployment.

**Overall Grade: B- (75/100)**

### Key Strengths
✅ Clean multi-tenant architecture using Hibernate discriminator-based approach  
✅ Well-structured modular design with clear domain boundaries  
✅ Good use of design patterns (Strategy, Repository, Service Layer)  
✅ Idempotent API design for critical operations  
✅ Proper database indexing and constraint design  

### Critical Issues
🚨 **SECURITY**: Tenant ID bypass vulnerability - no authentication/authorization validation  
🚨 **SECURITY**: Missing CSRF protection in API endpoints  
🚨 **ARCHITECTURE**: Incomplete multi-tenant user authentication integration  
⚠️ **DESIGN**: Anemic domain models - business logic scattered across services  
⚠️ **DESIGN**: Missing domain events and event-driven capabilities  

---

## 1. Multi-Tenancy Architecture Analysis

### 1.1 Implementation Approach ✅

**Grade: A-**

You've implemented **discriminator-based (shared database, shared schema)** multi-tenancy using Hibernate's `@TenantId` annotation. This is a solid choice for your use case.

```java
// BaseModel.java - Excellent implementation
@TenantId
@Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
private String tenantId;
```

**Why this works well:**
- Cost-effective for SaaS with many small tenants
- Automatic tenant filtering by Hibernate
- Simple deployment model
- Good database utilization

**Current Implementation:**
```
HTTP Request → TenantContextFilter → ThreadLocal → Hibernate → Database
     ↓
X-Tenant-ID header → TenantContext.setTenantId() → TenantIdentifierResolver
```

### 1.2 🚨 CRITICAL SECURITY VULNERABILITIES

**Grade: D (URGENT FIX REQUIRED)**

#### Vulnerability #1: Tenant ID Bypass
```java
// TenantContextFilter.java - LINE 31
String tenantId = request.getHeader(TENANT_HEADER);
TenantContext.setTenantId(tenantId);
```

**THE PROBLEM:**
Any client can set `X-Tenant-ID` to ANY value and access ANY tenant's data!

**Example Attack:**
```bash
# Attacker can access Tenant A's data by simply changing the header
curl -H "X-Tenant-ID: tenant-a" https://your-api.com/api/accounts
curl -H "X-Tenant-ID: tenant-b" https://your-api.com/api/accounts
```

**IMMEDIATE FIX REQUIRED:**
```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response,
                                    FilterChain filterChain) 
                                    throws ServletException, IOException {
        try {
            // CRITICAL: Extract tenant from authenticated user, NOT from header
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof BaseUser) {
                BaseUser user = (BaseUser) auth.getPrincipal();
                // Option 1: Add tenantId field to BaseUser
                String tenantId = user.getTenantId();
                
                // Option 2: Extract from custom claims in JWT
                // String tenantId = extractTenantFromToken(auth);
                
                TenantContext.setTenantId(tenantId);
            } else {
                // For API integrations, validate API key and extract tenant
                String apiKey = request.getHeader("X-API-Key");
                if (apiKey != null) {
                    String tenantId = validateApiKeyAndGetTenant(apiKey);
                    TenantContext.setTenantId(tenantId);
                } else {
                    TenantContext.setTenantId("default");
                }
            }
            
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
    
    private String validateApiKeyAndGetTenant(String apiKey) {
        // Validate API key against database and return associated tenant
        // NEVER trust client-provided tenant ID
        throw new UnsupportedOperationException("Implement API key validation");
    }
}
```

#### Vulnerability #2: User-Tenant Relationship Missing

```java
// BaseUser.java - MISSING tenant association
@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(name = "uq_users_tenant_email", columnNames = {"tenant_id", "email"})
})
public class BaseUser extends BaseModel implements UserDetails {
    // ❌ No explicit tenant field or getter
    // Users inherit tenantId from BaseModel but don't expose it
}
```

**REQUIRED CHANGES:**
```java
@Entity
@Table(name = "users")
public class BaseUser extends BaseModel implements UserDetails {
    
    // Add explicit tenant access
    public String getUserTenantId() {
        return this.getTenantId();
    }
    
    // Or better: Add a tenant entity relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_entity_id")
    private TenantEntity tenant;
}
```

#### Vulnerability #3: Missing CSRF Protection for State-Changing Operations

```java
// SpringSecurity.java - MISSING CSRF configuration
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // ❌ CSRF is enabled by default but not properly configured for API
        .authorizeHttpRequests(...)
        // MISSING: .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
```

**FIX:**
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf
            // Disable CSRF for stateless API endpoints
            .ignoringRequestMatchers("/api/integration/**", "/api/public/**")
            // Or use CookieCsrfTokenRepository for SPA
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        )
        // ... rest of config
}
```

### 1.3 Multi-Tenant Best Practices Assessment

| Practice | Status | Grade | Notes |
|----------|--------|-------|-------|
| **Tenant Isolation** | ⚠️ Partial | C | Implemented but bypassable via header injection |
| **Tenant from Auth** | ❌ Missing | F | Critical: Must derive tenant from authentication |
| **Database Constraints** | ✅ Good | A | Proper unique constraints with tenant_id |
| **Indexing Strategy** | ✅ Good | A | All indexes include tenant_id as first column |
| **Connection Pooling** | ✅ Implicit | B+ | Using single pool (good for discriminator) |
| **Tenant Config** | ❌ Missing | D | No TenantEntity, no tenant-specific settings |
| **Cross-Tenant Prevention** | ❌ Missing | F | No tests validating isolation |

---

## 2. Code Architecture & Design Patterns

### 2.1 Package Structure ✅

**Grade: A-**

Excellent modular organization following feature-based packaging:

```
com.menval.couriererp/
├── auth/                          # Authentication module
│   ├── models/
│   ├── dto/
│   ├── repository/
│   └── services/
├── modules/
│   ├── common/models/             # Shared abstractions
│   └── courier/                   # Business domain
│       ├── account/               # Bounded context: Accounts
│       │   ├── api/               # API layer (integration endpoints)
│       │   ├── controllers/       # MVC controllers
│       │   ├── entities/          # Domain models
│       │   ├── repositories/      # Data access
│       │   ├── services/          # Business logic
│       │   └── components/        # Utilities (code generators)
│       └── packages/              # Bounded context: Packages
│           ├── entities/
│           ├── repositories/
│           └── services/
├── configuration/                 # Spring configuration
├── security/                      # Security config
└── tenant/                        # Multi-tenancy infrastructure
```

**Strengths:**
- Clear separation of concerns
- Feature-based modules (not layer-based)
- Good domain separation (account vs packages)
- Distinct API and MVC layers

**Suggestions:**
```
# Add these packages:
modules/courier/account/
  ├── exceptions/          # Domain-specific exceptions
  ├── events/             # Domain events (AccountCreated, etc.)
  └── specifications/     # Query specifications for complex filtering

modules/courier/shared/   # Shared courier domain concepts
  ├── valueobjects/       # TrackingNumber, AccountCode, etc.
  └── policies/           # Shared business rules
```

### 2.2 Design Patterns Implemented ✅

**Grade: B+**

#### ✅ Repository Pattern
```java
public interface AccountRepository extends JpaRepository<AccountEntity, Long> {
    Optional<AccountEntity> findByPublicId(String publicId);
    Optional<AccountEntity> findByCode(String code);
    Optional<AccountEntity> findByExternalRef(String externalRef);
    // Custom query methods following Spring Data conventions
}
```
**Assessment:** Well-implemented, follows Spring Data best practices.

#### ✅ Service Layer Pattern
```java
@Service
public class AccountServiceImpl implements AccountService {
    // Good: Interface-based design
    // Good: Clear transaction boundaries
    // Good: Separation of orchestration from data access
}
```

#### ✅ Strategy Pattern
```java
public interface AccountCodeGenerator {
    String generate();
}

@Component
public class SequentialCodeGenerator implements AccountCodeGenerator {
    @Override
    public String generate() { ... }
}
```
**Assessment:** Good abstraction for pluggable code generation strategies.

#### ✅ Command Pattern (Partial)
```java
public record EnsureAccountCommand(
    String externalRef,
    String displayName,
    String email,
    String phone,
    String requestedCode
) {}
```
**Assessment:** Using Java records as immutable commands. Good choice!

**Recommendation:** Extend to full CQRS pattern:
```java
// Command side
public sealed interface AccountCommand permits 
    CreateAccountCommand, 
    DeactivateAccountCommand,
    UpdateAccountCommand {}

public record CreateAccountCommand(...) implements AccountCommand {}

// Query side
public sealed interface AccountQuery permits
    FindAccountByCode,
    SearchAccounts {}
    
public record FindAccountByCode(String code) implements AccountQuery {}
```

### 2.3 ⚠️ Domain Model Design Issues

**Grade: C**

#### Problem: Anemic Domain Models

Your entities are **data containers** with almost no behavior:

```java
@Entity
@Data  // ❌ Lombok @Data exposes everything
public class PackageEntity extends BaseModel {
    private Carrier carrier;
    private String originalTrackingNumber;
    private AccountEntity owner;
    private PackageStatus status;
    
    // ❌ Only one business method
    public void markReceivedNow(Instant now) {
        if (this.receivedAt == null) this.receivedAt = now;
        this.lastSeenAt = now;
    }
    
    // ❌ Boolean logic method instead of polymorphism
    public boolean isAssigned() {
        return owner != null;
    }
}
```

**The Problem:**
All business logic lives in service classes, not in the domain model where it belongs.

**Example - Current (Anti-pattern):**
```java
// PackageServiceImpl.java - Business logic in service layer
@Transactional
public PackageEntity receivePackage(Carrier carrier, String originalTrackingNumber) {
    // Service doing domain logic
    Optional<PackageEntity> existing = packageRepository.findByCarrierAndOriginalTrackingNumber(...);
    if (existing.isPresent()) {
        return existing.get();
    }
    Instant now = Instant.now();
    PackageEntity pkg = new PackageEntity();
    pkg.setCarrier(carrier);
    pkg.setOriginalTrackingNumber(tracking);
    pkg.setStatus(PackageStatus.RECEIVED_US_UNASSIGNED);
    // ... lots of setters
}
```

**BETTER - Rich Domain Model:**
```java
@Entity
public class PackageEntity extends BaseModel {
    
    // Private fields, no setters from outside
    private Carrier carrier;
    private String originalTrackingNumber;
    private AccountEntity owner;
    private PackageStatus status;
    private Instant receivedAt;
    
    // Factory method for creation
    public static PackageEntity receive(Carrier carrier, String trackingNumber, Instant receivedAt) {
        PackageEntity pkg = new PackageEntity();
        pkg.carrier = Objects.requireNonNull(carrier, "Carrier cannot be null");
        pkg.originalTrackingNumber = validateTrackingNumber(trackingNumber);
        pkg.status = PackageStatus.RECEIVED_US_UNASSIGNED;
        pkg.receivedAt = receivedAt;
        pkg.lastSeenAt = receivedAt;
        return pkg;
    }
    
    // Business behavior
    public void assignToAccount(AccountEntity account) {
        if (!account.isActive()) {
            throw new IllegalStateException("Cannot assign to inactive account");
        }
        if (this.isAssigned()) {
            throw new IllegalStateException("Package already assigned to " + owner.getCode());
        }
        this.owner = account;
        this.status = PackageStatus.ASSIGNED;
        // Raise domain event
        registerEvent(new PackageAssignedEvent(this.getId(), account.getId()));
    }
    
    public void updateDimensions(int length, int width, int height, int weight) {
        if (length <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
        this.lengthCm = length;
        this.widthCm = width;
        this.heightCm = height;
        this.weightGrams = weight;
    }
    
    // Query methods
    public boolean canBeAssigned() {
        return !isAssigned() && status == PackageStatus.RECEIVED_US_UNASSIGNED;
    }
    
    public boolean isInTransit() {
        return status == PackageStatus.IN_TRANSIT_TO_CUSTOMER;
    }
    
    // Encapsulation - controlled state access
    public PackageStatus getStatus() { return status; }
    public Carrier getCarrier() { return carrier; }
    public String getTrackingNumber() { return originalTrackingNumber; }
    
    private static String validateTrackingNumber(String tracking) {
        if (tracking == null || tracking.isBlank()) {
            throw new IllegalArgumentException("Tracking number required");
        }
        return tracking.trim().toUpperCase();
    }
}
```

**Service Layer Becomes Orchestration:**
```java
@Service
public class PackageServiceImpl implements PackageService {
    
    @Transactional
    public PackageEntity receivePackage(Carrier carrier, String trackingNumber) {
        // Check if already exists
        return packageRepository
            .findByCarrierAndOriginalTrackingNumber(carrier, trackingNumber)
            .orElseGet(() -> {
                // Domain object creates itself
                PackageEntity pkg = PackageEntity.receive(carrier, trackingNumber, Instant.now());
                return packageRepository.save(pkg);
            });
    }
    
    @Transactional
    public void assignPackageToAccount(Long packageId, String accountCode) {
        PackageEntity pkg = getById(packageId);
        AccountEntity account = accountService.getByCode(accountCode);
        
        // Domain object validates and performs business logic
        pkg.assignToAccount(account);
        
        packageRepository.save(pkg);
        // Publish events raised by domain object
        eventPublisher.publishEvents(pkg);
    }
}
```

### 2.4 Value Objects - Missing ⚠️

**Grade: D**

You're using primitive types for domain concepts that have business rules:

```java
// ❌ Current - primitives everywhere
@Column(name = "code", nullable = false)
private String code;

@Column(nullable = false, length = 64)
private String originalTrackingNumber;
```

**✅ SHOULD BE:**
```java
// Value Objects with validation and behavior
@Embeddable
public class AccountCode {
    @Column(name = "code", nullable = false, length = 32)
    private final String value;
    
    protected AccountCode() { this.value = null; } // JPA
    
    public AccountCode(String code) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("Invalid account code: " + code);
        }
        this.value = normalize(code);
    }
    
    public static AccountCode of(String code) {
        return new AccountCode(code);
    }
    
    private static boolean isValid(String code) {
        return code != null && code.matches("^[A-Z]{2}-[0-9A-Z]{4,10}$");
    }
    
    private static String normalize(String code) {
        return code.toUpperCase().trim();
    }
    
    public String getValue() { return value; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountCode)) return false;
        return value.equals(((AccountCode) o).value);
    }
    
    @Override
    public int hashCode() { return value.hashCode(); }
}

// Usage in entity
@Entity
public class AccountEntity extends BaseModel {
    @Embedded
    private AccountCode code;  // Type-safe, self-validating
    
    public AccountCode getCode() { return code; }
}
```

**Benefits:**
- Type safety (can't accidentally pass email where code is expected)
- Validation in one place
- Normalization logic encapsulated
- Can't create invalid value objects

---

## 3. Best Practices Compliance

### 3.1 Transaction Management ✅

**Grade: A-**

```java
@Transactional(readOnly = true)  // ✅ Explicit read-only
public Page<AccountEntity> search(String q, Boolean active, Pageable pageable) { ... }

@Transactional  // ✅ Write transactions marked
public AccountEntity ensureAccount(EnsureAccountCommand cmd) { ... }
```

**Good practices observed:**
- Proper use of `@Transactional`
- Read-only transactions for queries
- Appropriate transaction boundaries

### 3.2 Exception Handling ⚠️

**Grade: C+**

**Current:**
```java
// ❌ Generic exceptions
throw new IllegalArgumentException("Account not found");
throw new IllegalStateException("Could not allocate unique code");
```

**BETTER:**
```java
// Custom domain exceptions
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String code) {
        super("Account not found with code: " + code);
    }
}

public class CodeAllocationException extends RuntimeException {
    public CodeAllocationException(String prefix) {
        super("Could not allocate unique code with prefix: " + prefix);
    }
}

// Global exception handler
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(AccountNotFoundException ex) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("ACCOUNT_NOT_FOUND", ex.getMessage()));
    }
}
```

### 3.3 API Design ✅

**Grade: A**

**Excellent idempotent design:**
```java
@PostMapping("/ensure")
public EnsureAccountResponse ensure(@Valid @RequestBody EnsureAccountRequest req) {
    // ✅ Idempotent: same externalRef always returns same account
    AccountEntity a = accountService.ensureAccount(...);
    return toResponse(a);
}
```

**Good separation:**
- `/api/integration/*` - External integrations
- `/api/public/*` - Public endpoints
- MVC controllers for UI

### 3.4 Database Design ✅

**Grade: A**

**Excellent constraint design:**
```java
@Table(
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
```

**Why this is excellent:**
- All unique constraints include `tenant_id` (prevents cross-tenant collisions)
- Indexes optimized for multi-tenant queries
- Named constraints for easy debugging

### 3.5 Auditing ✅

**Grade: A-**

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseModel {
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
    
    @Version
    private Long version;  // ✅ Optimistic locking
}
```

**Good:**
- Automatic timestamp management
- Optimistic locking with `@Version`
- Using `Instant` (UTC) for timestamps

**Missing:**
- `createdBy` / `modifiedBy` fields
- Soft delete support

---

## 4. Missing Architectural Components

### 4.1 ❌ Domain Events (Critical for ERP)

**Current State:** No event system

**Why You Need This:**
```java
// When a package is assigned, you need to:
// 1. Update package status
// 2. Send notification to customer
// 3. Update inventory counts
// 4. Log audit trail
// 5. Trigger billing

// With events:
@Service
public class PackageService {
    @Transactional
    public void assignPackage(Long pkgId, Long accountId) {
        PackageEntity pkg = repository.findById(pkgId).orElseThrow();
        AccountEntity account = accountRepo.findById(accountId).orElseThrow();
        
        pkg.assignToAccount(account);
        
        // Publish event
        eventPublisher.publish(new PackageAssignedEvent(
            pkg.getId(),
            account.getId(),
            pkg.getTrackingNumber(),
            Instant.now()
        ));
        
        repository.save(pkg);
    }
}

// Listeners can react independently
@Component
public class PackageEventHandlers {
    
    @EventListener
    @Async
    public void onPackageAssigned(PackageAssignedEvent event) {
        // Send email notification
        emailService.sendAssignmentNotification(event.accountId(), event.trackingNumber());
    }
    
    @EventListener
    @Async
    public void onPackageAssigned(PackageAssignedEvent event) {
        // Update metrics
        metricsService.recordAssignment(event.accountId());
    }
}
```

### 4.2 ❌ Specification Pattern for Complex Queries

**Current Problem:**
```java
// Repository with many find methods
Optional<AccountEntity> findByCode(String code);
Optional<AccountEntity> findByPublicId(String publicId);
Optional<AccountEntity> findByExternalRef(String externalRef);
Page<AccountEntity> findByActive(boolean active, Pageable pageable);
// What about: findByCodeAndActive? findByCodeOrPublicIdAndActive?
```

**Better Approach:**
```java
// Specification
public class AccountSpecifications {
    
    public static Specification<AccountEntity> hasCode(String code) {
        return (root, query, cb) -> 
            cb.equal(root.get("code"), code);
    }
    
    public static Specification<AccountEntity> isActive() {
        return (root, query, cb) -> 
            cb.isTrue(root.get("active"));
    }
    
    public static Specification<AccountEntity> searchByText(String text) {
        return (root, query, cb) -> cb.or(
            cb.like(root.get("displayName"), "%" + text + "%"),
            cb.like(root.get("code"), "%" + text + "%"),
            cb.like(root.get("email"), "%" + text + "%")
        );
    }
}

// Repository
public interface AccountRepository extends JpaRepository<AccountEntity, Long>, 
                                           JpaSpecificationExecutor<AccountEntity> {
}

// Usage - compose specifications
Specification<AccountEntity> spec = Specification
    .where(AccountSpecifications.isActive())
    .and(AccountSpecifications.searchByText(query));
    
Page<AccountEntity> results = repository.findAll(spec, pageable);
```

### 4.3 ❌ Tenant Configuration Entity

You need a `TenantEntity` to store tenant-specific configuration:

```java
@Entity
@Table(name = "tenants")
public class TenantEntity {
    @Id
    private String tenantId;  // "tenant-a", "tenant-b"
    
    private String companyName;
    private String domain;  // "acme.couriererp.com"
    private boolean active;
    
    // Tenant-specific settings
    @Embedded
    private TenantSettings settings;
    
    private Instant createdAt;
    private Instant subscriptionExpiresAt;
}

@Embeddable
public class TenantSettings {
    private String accountCodePrefix;  // "ACME" vs "BETA"
    private int accountCodeLength = 6;
    private String timezone = "UTC";
    private String currency = "USD";
    
    // Feature flags per tenant
    private boolean autoAssignEnabled;
    private boolean batchingEnabled;
}
```

---

## 5. Security Recommendations

### 5.1 🚨 Immediate Fixes Required

1. **Remove X-Tenant-ID header trust**
```java
// BEFORE (VULNERABLE)
String tenantId = request.getHeader(TENANT_HEADER);
TenantContext.setTenantId(tenantId);

// AFTER (SECURE)
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String tenantId = extractTenantFromAuthentication(auth);
TenantContext.setTenantId(tenantId);
```

2. **Add API Key Authentication for Integration Endpoints**
```java
@Configuration
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null) {
            ApiKeyAuthentication auth = apiKeyService.authenticate(apiKey);
            SecurityContextHolder.getContext().setAuthentication(auth);
            TenantContext.setTenantId(auth.getTenantId());
        }
        filterChain.doFilter(request, response);
    }
}
```

3. **Add Rate Limiting Per Tenant**
```java
@Component
public class TenantRateLimitFilter extends OncePerRequestFilter {
    
    private final RateLimiter rateLimiter;
    
    @Override
    protected void doFilterInternal(...) {
        String tenantId = TenantContext.getTenantId();
        
        if (!rateLimiter.tryAcquire(tenantId)) {
            response.setStatus(429); // Too Many Requests
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}
```

### 5.2 Input Validation ⚠️

**Current:**
```java
@PostMapping("/ensure")
public EnsureAccountResponse ensure(@Valid @RequestBody EnsureAccountRequest req) {
    // ✅ Using @Valid
}
```

**Add to DTOs:**
```java
public record EnsureAccountRequest(
    @NotBlank(message = "externalRef is required")
    @Size(max = 64)
    String externalRef,
    
    @NotBlank(message = "displayName is required")
    @Size(min = 2, max = 120)
    String displayName,
    
    @Email(message = "Invalid email format")
    @Size(max = 320)
    String email,
    
    @Pattern(regexp = "^\\+?[0-9\\-\\s()]{7,20}$", message = "Invalid phone number")
    String phone,
    
    @Pattern(regexp = "^[A-Z]{2}-[0-9A-Z]{4,10}$", message = "Invalid code format")
    String requestedCode
) {}
```

---

## 6. Testing Recommendations

### 6.1 Missing Tests

**Current:** Only `CourierErpApplicationTests.java` (context load test)

**Required:**
```java
// 1. Multi-tenant isolation tests
@SpringBootTest
class MultiTenantIsolationTests {
    
    @Test
    void accountsFromDifferentTenantsAreIsolated() {
        // Create account for tenant-a
        TenantContext.setTenantId("tenant-a");
        AccountEntity accountA = accountService.createAccount(...);
        
        // Switch to tenant-b
        TenantContext.setTenantId("tenant-b");
        
        // Should not be able to find tenant-a's account
        assertThrows(AccountNotFoundException.class, () -> 
            accountService.getById(accountA.getId())
        );
    }
}

// 2. Concurrency tests
@Test
void ensureAccountIsIdempotentUnderConcurrency() throws Exception {
    String externalRef = "PORTAL:ORG:123";
    int threads = 10;
    
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    List<Future<AccountEntity>> futures = new ArrayList<>();
    
    for (int i = 0; i < threads; i++) {
        futures.add(executor.submit(() -> 
            accountService.ensureAccount(new EnsureAccountCommand(externalRef, ...))
        ));
    }
    
    Set<Long> accountIds = futures.stream()
        .map(f -> f.get().getId())
        .collect(Collectors.toSet());
        
    // All threads should return the same account
    assertEquals(1, accountIds.size());
}

// 3. Domain logic tests
@Test
void packageCannotBeAssignedToInactiveAccount() {
    PackageEntity pkg = PackageEntity.receive(Carrier.USPS, "123456", Instant.now());
    AccountEntity inactive = createAccount();
    inactive.setActive(false);
    
    assertThrows(IllegalStateException.class, () -> 
        pkg.assignToAccount(inactive)
    );
}
```

---

## 7. Recommendations Priority Matrix

### 🔴 CRITICAL (Fix within 1 week)

| Issue | Impact | Effort |
|-------|--------|--------|
| Tenant ID security vulnerability | HIGH | MEDIUM |
| Missing user-tenant association | HIGH | HIGH |
| CSRF protection for APIs | HIGH | LOW |

### 🟡 HIGH (Fix within 1 month)

| Issue | Impact | Effort |
|-------|--------|--------|
| Anemic domain models | MEDIUM | HIGH |
| Missing domain events | MEDIUM | MEDIUM |
| Value objects for domain concepts | MEDIUM | MEDIUM |
| TenantEntity and configuration | MEDIUM | MEDIUM |

### 🟢 MEDIUM (Nice to have)

| Issue | Impact | Effort |
|-------|--------|--------|
| Specification pattern | LOW | LOW |
| Comprehensive test suite | MEDIUM | HIGH |
| Soft delete support | LOW | LOW |

---

## 8. Detailed Refactoring Plan

### Phase 1: Security (Week 1)

```java
// 1. Add tenantId to BaseUser
@Entity
public class BaseUser extends BaseModel implements UserDetails {
    // Expose inherited tenantId
    public String getUserTenantId() {
        return super.getTenantId();
    }
}

// 2. Update TenantContextFilter
@Component
public class TenantContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth != null && auth.getPrincipal() instanceof BaseUser) {
            String tenantId = ((BaseUser) auth.getPrincipal()).getUserTenantId();
            TenantContext.setTenantId(tenantId);
        }
        
        filterChain.doFilter(request, response);
        TenantContext.clear();
    }
}

// 3. Add API key authentication for /api/integration/*
```

### Phase 2: Domain Model Enrichment (Weeks 2-3)

```java
// 1. Convert to rich domain models
public class PackageEntity extends BaseModel {
    // Remove @Data, add specific methods
    public static PackageEntity receive(...) { }
    public void assignToAccount(AccountEntity account) { }
    public void updateDimensions(...) { }
    // etc.
}

// 2. Add value objects
@Embeddable
public class TrackingNumber {
    private String value;
    
    public TrackingNumber(Carrier carrier, String rawValue) {
        this.value = carrier.normalize(rawValue);
    }
}

// 3. Add domain events
public record PackageAssignedEvent(Long packageId, Long accountId, Instant when) {}
```

### Phase 3: Testing (Week 4)

```java
// Add comprehensive test suite
// - Multi-tenant isolation tests
// - Concurrency tests
// - Domain logic tests
// - Integration tests
```

---

## 9. Final Recommendations

### Immediate Actions

1. **Stop accepting tenant ID from headers** - This is a critical security vulnerability
2. **Add TenantEntity** - You'll need this for tenant-specific configuration
3. **Enrich domain models** - Move business logic from services to entities
4. **Add domain events** - Essential for ERP systems with complex workflows

### Architecture Evolution Path

```
Current State (Phase 0)
  └─> Discriminator-based multi-tenancy
  └─> Anemic domain model
  └─> Service layer orchestration

Phase 1: Security & Foundation
  └─> Tenant from authentication
  └─> TenantEntity with configuration
  └─> API key authentication

Phase 2: Domain-Driven Design
  └─> Rich domain models
  └─> Value objects
  └─> Domain events
  └─> Aggregate roots

Phase 3: Scalability
  └─> CQRS (if needed)
  └─> Event sourcing for audit trail
  └─> Read models for reporting

Phase 4: Advanced Features
  └─> Tenant-specific customizations
  └─> White-labeling support
  └─> Advanced analytics
```

### Long-term Considerations

As you scale:

1. **Consider Database-per-Tenant** if tenants grow large
2. **Add Caching** with tenant-aware cache keys
3. **Implement Saga Pattern** for complex multi-step workflows
4. **Add Monitoring** per tenant (performance, usage, errors)

---

## 10. Score Summary

| Category | Grade | Weight | Notes |
|----------|-------|--------|-------|
| **Multi-Tenancy Implementation** | B | 25% | Good foundation, critical security flaw |
| **Architecture & Structure** | A- | 20% | Excellent modular design |
| **Design Patterns** | B+ | 15% | Good patterns, missing domain richness |
| **Security** | D | 20% | Critical vulnerabilities must be fixed |
| **Best Practices** | B | 10% | Good transaction management, needs improvement |
| **Code Quality** | B | 10% | Clean code, proper naming, good organization |

**Overall: B- (75/100)**

With security fixes: **A- (90/100)**
With all recommendations: **A+ (95/100)**

---

## Conclusion

Your COURIER-ERP project demonstrates solid software engineering fundamentals with a well-structured, modular architecture. The multi-tenancy implementation using Hibernate's discriminator approach is appropriate for your use case.

**However, the tenant ID security vulnerability is a critical issue that must be addressed before any production deployment.**

Once the security issues are resolved and you enrich your domain models, you'll have a robust, scalable, and maintainable multi-tenant ERP system.

The architecture is well-positioned for growth - you have clean boundaries, good separation of concerns, and a solid foundation. Focus on:
1. Security fixes (immediate)
2. Rich domain models (high priority)
3. Domain events (high priority)
4. Comprehensive testing (ongoing)

**Good luck with your project! You're on the right track.** 🚀
