# Security Hardening Plan
*Created: February 18, 2026*

This document covers the actionable security and quality issues identified in the February 2026 assessment, **excluding items 1–3** (hardcoded credentials, ddl-auto, debug logging — acknowledged as dev-only).

Issues are grouped by effort and ordered so each one can be done independently. Each item maps back to the assessment number for traceability.

---

## IMMEDIATE — Fix before merging any new features

---

### SEC-1 · Upgrade API key hashing to HMAC-SHA256 (Assessment #4)

**Risk:** SHA-256 without a salt is fast. A leaked `api_keys` table can be rainbow-tabled or brute-forced cheaply.

**What to do:**

1. Add a `HMAC_SECRET` environment variable (e.g. `courier.security.hmac-secret`). Generate a 256-bit random value for each environment at deploy time (`openssl rand -base64 32`).
2. In `ApiKeyServiceImpl`, replace the `hash()` method:
   ```java
   // Before
   MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8))
   
   // After — inject @Value("${courier.security.hmac-secret}") String hmacSecret
   Mac mac = Mac.getInstance("HmacSHA256");
   mac.init(new SecretKeySpec(hmacSecret.getBytes(UTF_8), "HmacSHA256"));
   return HexFormat.of().formatHex(mac.doFinal(value.getBytes(UTF_8)));
   ```
3. Write a one-time migration script that re-hashes all existing keys using the new algorithm (you'll need to either force rotation of all keys, or store the algorithm version in `ApiKeyEntity` as a migration path).
4. Add `courier.security.hmac-secret` to `application-docker.yml` as `${COURIER_HMAC_SECRET:}` with no default — startup should fail fast if the secret is missing.

**Files:** `ApiKeyServiceImpl.java`, `application-docker.yml`, `ApiKeyEntity.java` (add optional `hashAlgorithm` column for migration).

---

### SEC-2 · Fix TenantContext mutation inside PackageServiceImpl (Assessment #5 & #6)

**Risk:** `saveNewPackage()` calls `TenantContext.setTenantId(actor.getUserTenantId())` mid-request without saving and restoring the previous value. Any DB operation after this in the same thread runs against the actor's tenant, not the package's tenant. This is a silent data corruption / cross-tenant leak risk.

**What to do:**

1. In `PackageServiceImpl.saveNewPackage()`, save and restore the context:
   ```java
   String previousTenant = TenantContext.getTenantId();
   try {
       if (actor != null && actor.getUserTenantId() != null) {
           TenantContext.setTenantId(actor.getUserTenantId());
       }
       packageEventRepository.save(event);
   } finally {
       TenantContext.setTenantId(previousTenant);
   }
   ```
2. Similarly fix `AuthServiceImpl.createUserForTenant()` — it sets `TenantContext.setTenantId(tenantId)` with no `finally` restore. Wrap in try/finally identically.
3. Add a Javadoc comment to `TenantContext.setTenantId()` explicitly warning: *"Callers that change the tenant mid-request must save and restore the previous value in a finally block."*
4. Review all remaining callsites of `TenantContext.setTenantId()` in non-filter code (grep the src tree) and apply the same save/restore pattern wherever found outside of a filter's `doFilterInternal`.

**Files:** `PackageServiceImpl.java`, `AuthServiceImpl.java`, `TenantContext.java`.

---

### SEC-3 · Validate tenantId format to prevent open redirect (Assessment #7)

**Risk:** `AdminTenantMvcController` builds redirect URLs as `"redirect:/admin/tenants/" + tenantId` using a path variable. A crafted `tenantId` (e.g. `/admin/tenants//evil.com`) could redirect users off-site.

**What to do:**

1. Add a `TenantIdValidator` utility (or add to `TenantServiceImpl.createTenant`) that enforces the format:
   ```java
   private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9\\-]{1,30}[a-z0-9]$");
   
   public static void validateTenantIdFormat(String tenantId) {
       if (tenantId == null || !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
           throw new IllegalArgumentException(
               "Tenant ID must be 3–32 lowercase alphanumerics or hyphens, cannot start/end with hyphen");
       }
   }
   ```
2. Call this validator in `TenantServiceImpl.createTenant()` before any other logic — this ensures no invalid IDs can ever be persisted.
3. In `AdminTenantMvcController`, add `@PathVariable @Pattern(regexp="^[a-z0-9\\-]{3,32}$") String tenantId` with `@Validated` on the class, or validate manually and return 400 on mismatch.
4. Add the same format check to `OnboardTenantForm` as a `@Pattern` annotation on the `tenantId` field.

**Files:** `TenantServiceImpl.java`, `AdminTenantMvcController.java`, `OnboardTenantForm.java`, new `TenantIdValidator.java`.

---

## SHORT-TERM — This week

---

### SEC-4 · Gate DatabaseSeedRunner behind a dev/test profile (Assessment #14)

**Risk:** On a fresh production deployment, `DatabaseSeedRunner` auto-creates a demo tenant with a weak password. Even though the credentials are "just for testing," the demo tenant runs as `ENTERPRISE` plan with `subscriptionExpiresAt = null` (never expires). Someone who knows the defaults can log in forever.

**What to do:**

1. Add `@Profile({"dev", "test"})` to `DatabaseSeedRunner`. This is a one-line fix.
2. Alternatively, add a property flag:
   ```yaml
   # application-docker.yml
   courier.seed.demo-data: ${COURIER_SEED_DEMO_DATA:false}
   ```
   And in the runner:
   ```java
   @ConditionalOnProperty(name = "courier.seed.demo-data", havingValue = "true")
   ```
3. Remove the plaintext password from the log output:
   ```java
   // Before
   log.info("Demo seed complete. Log in as {} / {} (tenant {})", email, DEMO_ADMIN_PASSWORD, tenantId);
   // After
   log.info("Demo seed complete. Log in as {} (tenant {})", email, tenantId);
   ```
4. Update `compose.yaml` to not set `COURIER_SEED_DEMO_DATA` (defaults to false), and add a note in `HOW-TO-TEST.md` explaining how to enable it for local testing.

**Files:** `DatabaseSeedRunner.java`, `compose.yaml`, `HOW-TO-TEST.md`.

---

### SEC-5 · Cap unbounded page size parameters (Assessment #11)

**Risk:** `AccountSearchController` accepts any `size` parameter. `size=100000` causes a full table scan and can be used for a simple denial-of-service or data exfiltration.

**What to do:**

1. In `AccountSearchController.search()`:
   ```java
   int effectiveSize = Math.min(Math.max(size, 1), 100); // clamp 1..100
   Page<AccountEntity> accounts = accountService.search(q, true, PageRequest.of(0, effectiveSize));
   ```
2. Review all other controllers that accept a `size` or `page` parameter: `PackageListController`, `PackageBatchController`, `AccountMVCController`. Apply the same cap (suggest max 100 for API endpoints, max 50 for UI pages).
3. Add `@Max(100) @Min(1)` bean validation annotations to any request DTO that carries a page size if you prefer declarative validation over imperative clamping.

**Files:** `AccountSearchController.java`, `PackageListController.java`, and any other paginated controller.

---

### SEC-6 · Fix CSRF on logout (Assessment #13)

**Risk:** `csrf.ignoringRequestMatchers("/auth/logout")` means any page on the internet can log out your users with a `<img src="https://yourapp.com/auth/logout">`. This is a minor but real CSRF vulnerability.

**What to do:**

1. Remove `/auth/logout` from the CSRF ignore list in `SpringSecurity.java`.
2. Update the logout link in all Thymeleaf templates from a plain `<a href="/auth/logout">` to a form with a CSRF token:
   ```html
   <form th:action="@{/auth/logout}" method="post">
       <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
       <button type="submit">Logout</button>
   </form>
   ```
   Or use Thymeleaf's Spring Security extras to auto-inject the CSRF token:
   ```html
   <form th:action="@{/auth/logout}" method="post" sec:csrf="">
       <button type="submit">Sign out</button>
   </form>
   ```
3. Verify that the login processing URL `/auth/login-process` still needs CSRF disabled (it does — the session isn't established yet) and leave that exclusion in place.

**Files:** `SpringSecurity.java`, all Thymeleaf templates that contain a logout link.

---

### SEC-7 · Add rate limiting on login and API endpoints (Assessment #8)

**Risk:** No protection against brute-force login attempts or API key scanning.

**What to do:**

**Option A (lightweight — add Bucket4j):**
1. Add to `build.gradle`:
   ```groovy
   implementation 'com.bucket4j:bucket4j-core:8.10.1'
   ```
2. Create `RateLimitFilter` (a `OncePerRequestFilter`) that maintains an in-memory `ConcurrentHashMap<String, Bucket>` keyed on client IP:
   ```java
   // 10 login attempts per minute per IP
   Bandwidth loginLimit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
   ```
3. Apply the filter only to `/auth/login-process` and `/api/**` paths.
4. Return HTTP 429 with `Retry-After` header on limit exceeded.

**Option B (recommended for production — handle at reverse proxy):**
1. Document in `architecture.md` that rate limiting should be handled at the nginx/ALB layer.
2. Add example nginx config snippet to `agent-documents/` showing `limit_req_zone` configuration.
3. Still add a lightweight server-side backstop via Bucket4j for defense-in-depth.

Either way, start with Option A now and add Option B when deploying to AWS (which aligns with the existing `SEC-22` infrastructure item).

**Files:** new `RateLimitFilter.java`, `SpringSecurity.java` (register filter), `build.gradle`.

---

## MEDIUM-TERM — Next sprint

---

### SEC-8 · Add API key expiry (Assessment #9)

**Risk:** Keys live forever unless manually suspended. A leaked key that isn't noticed remains valid indefinitely.

**Note:** The TODO already marks #7 (API key rotation) as done and mentions `expiresAt` — verify whether this field actually exists on `ApiKeyEntity`. If the field is there but not enforced in `validateAndGetTenantId`, the fix is just wiring it up. If the field is missing, it needs to be added.

**What to do:**

1. Confirm `ApiKeyEntity` has `expiresAt` (Instant, nullable). If not, add the JPA field and a Liquibase/Flyway migration (or let `ddl-auto` handle it in dev).
2. In `ApiKeyServiceImpl.validateAndGetTenantId()`, add the expiry check to the filter chain:
   ```java
   return apiKeyRepository.findByKeyHash(keyHash)
       .filter(key -> !key.isSuspended())
       .filter(key -> key.getExpiresAt() == null || Instant.now().isBefore(key.getExpiresAt()))
       // ... rest of chain
   ```
3. In `ApiKeyServiceImpl.createApiKey()`, accept an optional `expiresAt` parameter. Default to `null` (no expiry) but allow callers to pass a duration.
4. In the admin UI (`api-key-new.html`), add an optional expiry date picker field.
5. In `ApiKeySummary` DTO, include `expiresAt` so the settings page can display it.
6. Add a scheduled task (Spring `@Scheduled`) that logs or alerts on keys expiring within 7 days — optional, but useful.

**Files:** `ApiKeyEntity.java`, `ApiKeyServiceImpl.java`, `ApiKeyService.java`, `ApiKeySummary.java`, `api-key-new.html`, `api-keys.html`.

---

### SEC-9 · Extract shared public-path list (Assessment #15)

**Risk:** `TenantContextFilter` and `TenantAccessFilter` each maintain their own hardcoded list of "public" paths. When you add a new public endpoint, you must update both or one filter will incorrectly reject it. This has already bitten projects like this in practice.

**What to do:**

1. Create `PublicPaths.java` in the `security` package:
   ```java
   public final class PublicPaths {
       public static final List<String> PREFIXES = List.of(
           "/auth/",
           "/api/public/",
           "/api/integration/",
           "/css/",
           "/js/",
           "/images/"
       );
       public static final List<String> EXACT = List.of("/error");
   
       public static boolean isPublic(String requestUri) {
           return EXACT.contains(requestUri)
               || PREFIXES.stream().anyMatch(requestUri::startsWith);
       }
   
       private PublicPaths() {}
   }
   ```
2. Replace the `isPublicEndpoint()` private method in `TenantAccessFilter` with `PublicPaths.isPublic(path)`.
3. Replace the equivalent inline checks in `TenantContextFilter` with the same call.
4. Update `SpringSecurity.java`'s `requestMatchers(...)` permit list to also reference `PublicPaths.PREFIXES` rather than duplicating strings — keeping all three in sync.

**Files:** new `PublicPaths.java`, `TenantAccessFilter.java`, `TenantContextFilter.java`, `SpringSecurity.java`.

---

### SEC-10 · Consistent `@PreAuthorize` on TenantService methods (Assessment #17)

**Risk:** `getTenantById()` has no access control annotation. It's currently called from other `@PreAuthorize("SUPER_ADMIN")` methods, so it's safe today — but any future controller that calls it directly would bypass the authorization check silently.

**What to do:**

1. Decide the policy: `getTenantById` should be callable by `SUPER_ADMIN` and also by the tenant's own `ADMIN` user (to load their own tenant settings). Model this:
   ```java
   @PreAuthorize("hasRole('SUPER_ADMIN') or #tenantId == authentication.principal.userTenantId")
   public TenantEntity getTenantById(String tenantId) { ... }
   ```
2. Review all other `TenantServiceImpl` methods that currently lack `@PreAuthorize` and decide their access policy:
   - `updateTenantSettings`: already `SUPER_ADMIN` — consider also allowing the tenant's own ADMIN to update *their own* settings.
   - `extendSubscription`, `changePlan`, `suspendTenant`: billing operations — `SUPER_ADMIN` only, which is already set.
3. Add a comment to `getTenantById` explaining why the rule is what it is, so the next developer doesn't accidentally remove or weaken it.

**Files:** `TenantServiceImpl.java`, `TenantService.java`.

---

## LONG-TERM — Next sprint or later

---

### SEC-11 · Tenant isolation integration tests (Assessment #18)

**Risk:** The central security guarantee of this application — data isolation between tenants — has no automated verification. A refactor, a JPA query change, or a missing `WHERE tenant_id = ?` could silently break isolation.

**What to do:**

1. Add a `TenantIsolationTest` (`@SpringBootTest` + `@Transactional`):
   - Create two tenants: `tenant-a` and `tenant-b`
   - Create a package, account, and batch under `tenant-a`
   - Switch `TenantContext` to `tenant-b`
   - Assert that all repository queries return empty results (no `tenant-a` data bleeds through)
   - This tests the Hibernate multi-tenancy discriminator in practice

2. Add `ApiKeyAuthenticationFilterTest`:
   - `POST /api/public/packages/received-status` with no key → assert 401
   - With an invalid key → assert 401
   - With a suspended key → assert 401
   - With a valid key from `tenant-a` → assert 200 and that only `tenant-a` data is accessible

3. Add `TenantAccessFilterTest`:
   - Suspended tenant → assert 403 on any `/packages/**` request
   - Expired tenant → assert 403
   - Active tenant → assert 200

4. Add `PackageStateTest` unit tests:
   - `PackageEntity.assignToAccount()` with inactive account → IllegalArgumentException
   - `PackageEntity` status transitions: valid and invalid transitions

5. Tie into the existing `TODO.md` item #21 (Test coverage) — this plan supersedes and expands it.

**Files:** new test classes in `src/test/java/...`.

---

### SEC-12 · Fix `eraseCredentials()` override (Assessment #19)

**Risk:** `BaseUser.eraseCredentials()` is not annotated with `@Override`. If the method signature ever drifts from the `CredentialsContainer` interface, it silently stops being called by Spring Security's `ProviderManager`, leaving password hashes in memory after authentication.

**What to do:**

1. Add `@Override` to `BaseUser.eraseCredentials()`:
   ```java
   @Override
   public void eraseCredentials() {
       this.password = null;
   }
   ```
2. Confirm that `BaseUser` either implements `CredentialsContainer` directly or that `UserDetails` extends it in the Spring version you're using. If not, add `implements CredentialsContainer` explicitly.
3. Add a unit test: authenticate a user, then call `eraseCredentials()`, assert `getPassword()` returns null.

**Files:** `BaseUser.java`.

---

## Summary Table

| ID | Issue | Priority | Effort | Files Touched |
|----|-------|----------|--------|---------------|
| SEC-1 | HMAC-SHA256 for API keys | 🔴 Immediate | Medium | ApiKeyServiceImpl, app config |
| SEC-2 | TenantContext mid-request mutation | 🔴 Immediate | Small | PackageServiceImpl, AuthServiceImpl |
| SEC-3 | Validate tenantId format / open redirect | 🔴 Immediate | Small | TenantServiceImpl, controller, form |
| SEC-4 | Gate seeder behind dev profile | 🟡 This week | Tiny | DatabaseSeedRunner, compose.yaml |
| SEC-5 | Cap page size params | 🟡 This week | Tiny | AccountSearchController + others |
| SEC-6 | CSRF on logout | 🟡 This week | Small | SpringSecurity, templates |
| SEC-7 | Rate limiting (login + API) | 🟡 This week | Medium | New filter, build.gradle |
| SEC-8 | API key expiry enforcement | 🟡 Next sprint | Small | ApiKeyServiceImpl, entity, UI |
| SEC-9 | Extract shared PublicPaths | 🟢 Next sprint | Small | New utility, 3 filters |
| SEC-10 | Consistent @PreAuthorize on TenantService | 🟢 Next sprint | Small | TenantServiceImpl |
| SEC-11 | Tenant isolation integration tests | 🟢 Long-term | Large | New test classes |
| SEC-12 | Fix eraseCredentials @Override | 🟢 Quick | Tiny | BaseUser |
