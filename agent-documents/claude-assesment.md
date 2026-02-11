# COURIER-ERP Architecture Assessment

**Date:** February 11, 2026
**Project:** COURIER-ERP (com.menval.couriererp)
**Version:** 0.0.1-SNAPSHOT
**Tech Stack:** Spring Boot 4.0.2, Java 21, PostgreSQL, Hibernate, Thymeleaf

---

## Executive Summary

Since the previous assessment (February 10, 2026), the most critical security issue has been **fully resolved**: tenant isolation is no longer derived from a trusted HTTP header, but from the authenticated user's identity and validated API keys. This is a significant, foundational improvement.

The project remains well-structured with clean domain boundaries. The primary remaining concerns are a handful of moderate security considerations, some architectural gaps, and the natural incomplete state of a project still under active development.

**Overall Grade: B+ (82/100)** *(up from B- / 75 yesterday)*

### What Improved Since Yesterday
✅ **CRITICAL FIX**: Tenant no longer derived from `X-Tenant-ID` header — `TenantAccessFilter` now sets it from the authenticated `BaseUser`
✅ **API key authentication** fully implemented: `ApiKeyAuthenticationFilter`, `ApiKeyService`, SHA-256 hashed key storage
✅ **Tenant lifecycle management**: suspend, activate, expire, plan limits all wired up correctly
✅ `TenantContextFilter` only uses tenant from login form POST — no other header/query exploitation possible

### Remaining Strengths
✅ Clean discriminator-based multi-tenancy (Hibernate `@TenantId`) — correct approach for a SaaS courier ERP
✅ Solid layered architecture: Controller → Service Interface → ServiceImpl → Repository
✅ Good domain encapsulation: `PackageEntity.receive()` and `assignToAccount()` are proper factory/domain methods
✅ Idempotent API design (`ensureAccount`, `receivePackage` handle duplicate races gracefully)
✅ Thorough database indexing and unique constraint design
✅ `@PreAuthorize` at the service layer providing defense-in-depth

### Remaining Issues
⚠️ **SECURITY (Moderate)**: Public `signUp()` endpoint creates a `DIRECTOR`-role user with no tenant assigned
⚠️ **SECURITY (Moderate)**: `TenantBootstrap` sets a 1-year expiry on system/default tenants — this will silently break the platform in 365 days
⚠️ **ARCHITECTURE**: `AccountCounterService` is a stub — `code` field is `updatable=false` meaning this must be right on first insert
⚠️ **ARCHITECTURE**: No domain events — `assignToAccount` has no notification/audit trail side-effect
⚠️ **TESTING**: Only a single smoke test (`contextLoads`) — zero business logic coverage
⚠️ **DESIGN**: `AuthServiceImpl.signUp()` does not validate that a tenant is provided, creating untethered users

---

## 1. Security

### 1.1 Tenant Isolation — RESOLVED ✅

**Grade: A-** *(was D yesterday)*

The previous critical vulnerability is fully fixed. The filter chain now works correctly:

```
POST /auth/login-process  → TenantContextFilter reads "tenant" form field, validates against DB
Authenticated requests    → TenantAccessFilter reads tenant from BaseUser (SecurityContext)
/api/** requests          → ApiKeyAuthenticationFilter validates key hash, sets tenant from key
```

Hibernate's `@TenantId` discriminator ensures every query is automatically scoped. The `TenantIdentifierResolver` bridges `TenantContext` (ThreadLocal) to Hibernate. This is correct.

One small concern: `TenantContext.clear()` is only called in the `finally` block of `TenantContextFilter`, but `TenantAccessFilter` sets the tenant without a corresponding `finally` cleanup. In a servlet container that reuses threads, this is acceptable because `TenantContextFilter` always clears. It's still worth adding a defensive clear in `TenantAccessFilter`'s finally block.

### 1.2 ⚠️ Open User Registration

**Grade: C**

```java
// AuthController.java
@PostMapping("/signup")
public String signup(@Valid @ModelAttribute("form") SignUpRequest form, ...) {
    authService.signUp(form);     // Creates a DIRECTOR-role user, no tenant
    return "redirect:/auth/login?registered=true";
}
```

`/auth/signup` is publicly accessible and creates a `DIRECTOR`-role user. This user will have `tenantId = null` (or `default`, whatever Hibernate injects at save time), and will fail the `TenantAccessFilter` check on every subsequent request. While it can't actively cause harm today because of that filter, it's dead UX and a potential confusion vector.

**Recommendation:** Either disable the signup page entirely (the super-admin creates tenants and their first user), or make signup flow through tenant onboarding (requiring a valid tenant code to be entered).

### 1.3 ⚠️ API Key Security — Good, One Note

**Grade: A-**

API keys are stored as SHA-256 hashes — correct. Keys are prefixed with `ce_` for easy scanning. `SecureRandom` is used for generation. The `validateAndGetTenantId` pipeline correctly chains: key → hash lookup → tenant lookup → active/expiry/access checks.

One note: there is no mechanism to **rotate** or **revoke** individual API keys from the UI (only "create"). Listing and deleting existing keys should be a near-term addition.

### 1.4 ⚠️ TenantBootstrap Sets Expiry on System Tenant

**Grade: C+**

```java
// TenantBootstrap.java
.subscriptionExpiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
```

Both the `default` and `system` tenants are created with a 1-year expiry. In 365 days, `TenantEntity.isExpired()` will return `true`, and `TenantAccessFilter` will throw `TenantExpiredException` for the super-admin, locking out the platform itself.

**Recommendation:** Set `subscriptionExpiresAt = null` for system tenants and update `isExpired()` to handle null as "never expires":
```java
public boolean isExpired() {
    return subscriptionExpiresAt != null && Instant.now().isAfter(subscriptionExpiresAt);
}
```
The null-safe check is already there — but the bootstrap passes a non-null value, defeating it.

---

## 2. Multi-Tenancy Architecture

**Grade: A-**

### What's Working Well

The discriminator-based approach with Hibernate `@TenantId` is the right call for your scale. `TenantScopedBaseModel` correctly applies `@TenantId` on a `MappedSuperclass`, meaning all inheriting entities get automatic filtering with zero repetition.

The entity hierarchy is clean:
```
BaseModel (id, createdAt, updatedAt)
    └── TenantScopedBaseModel (+ tenant_id @TenantId)
            ├── BaseUser
            ├── AccountEntity
            └── PackageEntity
    TenantEntity (extends BaseModel, not TenantScoped — correct)
```

### Remaining Concern: `TenantEntity` vs `TenantScopedBaseModel`

`TenantEntity` correctly extends `BaseModel` and not `TenantScopedBaseModel`. However, `ApiKeyEntity` should be reviewed to confirm it does NOT extend `TenantScopedBaseModel` — if it does, then API key lookups (which happen before the tenant is set) could fail silently or use the wrong tenant filter.

---

## 3. Domain Model

**Grade: B+**

### Strengths

`PackageEntity` has genuine domain methods:

```java
public static PackageEntity receive(Carrier carrier, String trackingNumber, Instant receivedAt) { ... }
public void assignToAccount(AccountEntity account) { ... } // validates account.isActive(), not-already-assigned
public boolean canBeAssigned() { ... }
```

This is good. Business rules live on the entity, not scattered in services. The `assignToAccount` method correctly validates pre-conditions and throws meaningful exceptions.

### Weakness: No Domain Events

When a package is assigned to an account, nothing else happens — no audit log entry, no notification hook, no event. As the system grows (auto-assign from inbound notice, label generation, manifest creation per the TODO), this will require threading side-effects into more and more service methods. Consider Spring's `ApplicationEventPublisher` or a simple outbox pattern early, before the feature set grows.

### Weakness: `AccountEntity.code` is `updatable=false`

```java
@Column(name = "code", nullable = false, updatable = false, length = 32)
private String code;
```

This is correct intentionally — codes should not change. But `AccountCounterService` is a stub that always returns `false` for `supports()`. This means code generation always falls through to the random code path (`AccountCodeGenerator`), never the sequential counter. The TODO implies sequential codes are planned — until then, the system works, but the gap between design intent and implementation is worth noting.

---

## 4. API Design

**Grade: B+**

### Public Package Status API
```
GET /api/public/packages/received-status?trackingNumber=...
```
Clean, simple, returns 200 always (not 404 for "not found" — correct UX for a polling/check endpoint). Tenant is derived from the API key.

### Integration Account API
```
POST /api/integration/accounts/ensure
```
Idempotent design is excellent. The `externalRef` deduplication with race condition handling (retry on `DataIntegrityViolationException`) is production-grade.

### What's Missing
The TODO list accurately captures what's needed next. From an API perspective:
- No endpoint to **list packages** via API (only UI)
- No endpoint to **assign** a package (only UI)
- No **webhook** or event notification surface

---

## 5. Service Layer

**Grade: B+**

`PackageServiceImpl.receivePackages()` correctly handles:
- Null/blank carrier → defaults to `UNKNOWN`
- Duplicate detection before insert + race condition fallback
- Invalid lines collected and returned in `BatchReceiveResult`

`AccountServiceImpl.ensureAccount()` handles the concurrent creation edge case well — the 10-retry loop for code collision is appropriate.

`TenantServiceImpl` uses `@PreAuthorize("hasRole('SUPER_ADMIN')")` at the service layer — this is good defense-in-depth even though the MVC controllers are also `@PreAuthorize`-annotated.

### Minor Issue: `suspendTenant` ignores the `reason` parameter
```java
public void suspendTenant(String tenantId, String reason) {
    TenantEntity tenant = getTenantById(tenantId);
    tenant.setStatus(TenantStatus.SUSPENDED);
    // 'reason' is never stored
}
```
There's no `suspensionReason` field on `TenantEntity`. Either add one or remove the parameter from the interface.

---

## 6. Configuration & Infrastructure

**Grade: B**

`application.properties` is nearly empty — just the app name and a debug log level for Spring Security. Everything else presumably lives in `application-docker.yml` or environment variables.

The `Dockerfile` and `compose.yaml` suggest this is deployment-ready via Docker. That's good.

**Recommendation:** Ensure the following are externalized for production:
- Database credentials (never in committed config)
- `courier.superadmin.initial-password` — should be required on first run, not defaulting to `changeme`
- `spring.jpa.hibernate.ddl-auto` — should be `validate` or `none` in production, not `update` or `create`

---

## 7. Testing

**Grade: D**

```java
// CourierErpApplicationTests.java
@Test
void contextLoads() { }
```

This is the only test. For a system handling multi-tenant data isolation, this is a meaningful gap. Missing tests:

- **Tenant isolation**: verify that a query under tenant A cannot return tenant B's packages
- **Package assignment**: verify that `assignToAccount` rejects inactive accounts and double-assignments
- **API key flow**: validate that an invalid key returns 401, a valid key sets the correct tenant
- **`ensureAccount` idempotency**: concurrent calls with the same `externalRef` return the same account

Spring Boot provides excellent test infrastructure (`@SpringBootTest`, `@DataJpaTest`, `MockMvc`) — these should be used.

---

## 8. Summary Scorecard

| Area | Yesterday | Today | Change |
|---|---|---|---|
| Tenant Isolation | D (critical vuln) | A- | 🟢 +4 grades |
| API Security | C | A- | 🟢 +2 grades |
| Domain Model | B | B+ | 🟢 Slight improvement |
| Service Layer | B+ | B+ | — |
| API Design | B | B+ | 🟢 Slight improvement |
| Testing | D | D | — |
| Infrastructure | B | B | — |
| **Overall** | **B- (75)** | **B+ (82)** | **🟢 +7 points** |

---

## 9. Priority Action Items

### Immediate (before next feature)
1. **Fix `TenantBootstrap`** — set `subscriptionExpiresAt = null` for `system` and `default` tenants, or set it far in the future. This will break the platform in 365 days otherwise.
2. **Disable or secure `/auth/signup`** — either restrict it or remove it. The public signup creates useless DIRECTOR users with no tenant.

### Short-term (this week)
3. **Add API key revocation** — `DELETE /settings/api-keys/{id}` so tenants can rotate keys.
4. **Store suspension reason** — add `suspensionReason` field to `TenantEntity`, or remove the `reason` param from `suspendTenant`.
5. **Write core tests** — at minimum: tenant isolation test, package assignment domain test, API key auth test.

### Medium-term (next sprint)
6. **Domain events** — introduce `ApplicationEventPublisher` for package lifecycle events (assigned, dispatched, received at final warehouse). This will cleanly support labels, manifests, and notifications without service coupling.
7. **Sequential account codes** — implement `AccountCounterService` properly with a `account_code_counters` table (per-tenant counter, atomic increment).
8. **API completeness** — expose package listing and assignment via REST API, not just UI, to support the external portal.
