# Courier ERP – Development TODO
*Last updated: February 12, 2026*

**Related documents:** [architecture.md](architecture.md) | [test-run.md](test-run.md)

---

## ✅ Done

- ~~Tenant isolation via `X-Tenant-ID` header~~ → tenant now set from authenticated `BaseUser` or validated API key
- ~~API key authentication~~ → `ApiKeyAuthenticationFilter`, SHA-256 hashed storage, `ApiKeyService` complete
- ~~Tenant lifecycle~~ → suspend, activate, expire, plan limits wired up
- ~~`PackageEntity.assignToAccount()`~~ → domain method with pre-condition validation
- ~~Package list + assign~~ → `/packages` with status filter; assign via modal (search account, assign); `PackageListController`, `PackageService.listAll` with owner fetch
- ~~Package batches~~ → create batch, add/remove packages, seal, in transit, receive at destination; `PackageBatchController`, `PackageBatchServiceImpl`, batch list/detail/add-packages
- ~~Receive at final warehouse~~ → batch status ARRIVED; packages `RECEIVED_FINAL`; receiving flow in `PackageReceivingController` / batch receive
- ~~Dispatch to customers~~ → `/packages/dispatch`; mark out for delivery / delivered; `PackageDispatchController`, `PackageService.findReadyForDispatch` with owner fetch
- ~~LazyInitializationException on package list~~ → repository methods with `@EntityGraph(attributePaths = {"owner"})` for list, dispatch, batch detail, add-packages
- ~~Database seed~~ → `DatabaseSeedRunner`: demo tenant, admin user, sample accounts, packages, batches

---

## 🚨 IMMEDIATE — Fix before next feature

### 1. Remove public signup page
**Why:** `/auth/signup` is publicly accessible and creates a `DIRECTOR`-role user with no tenant — dead UX at best, confusion vector at worst.
**Decision:** Tenant onboarding (super-admin) is the only entry point for new users. Remove or `@Deprecated`-gate the signup controller and Thymeleaf template.
- [X] Delete or disable `AuthController.signup()` and `POST /auth/signup`
- [X] Remove `signup.html` template (or keep as reference, commented out)
- [X] Remove `AuthService.signUp()` or make it package-private/internal-only

### 2. Fix `TenantBootstrap` expiry on system/default tenants
**Why:** Both `system` and `default` tenants are created with `subscriptionExpiresAt = Instant.now().plus(365 days)`. In 365 days `isExpired()` returns `true`, locking the super-admin out of the platform.
**Decision:** System and default tenants never expire.
- [X] Set `subscriptionExpiresAt = null` in `TenantBootstrap` for both `system` and `default` tenants
- [X] Add a comment to `TenantEntity.isExpired()` noting that `null` means "never expires" (it already handles null safely — just document it)

### 3. Fix `ApiKeyAuthenticationFilter` — ensure tenant is never loaded into context from a non-tenant-scoped lookup
**Why:** `ApiKeyEntity` may extend `TenantScopedBaseModel` (or be loaded while a stale `TenantContext` is set), meaning the key lookup itself could silently filter by the wrong tenant.
- [X] Confirm `ApiKeyEntity` does NOT extend `TenantScopedBaseModel`
- [x] Confirm `ApiKeyRepository.findByKeyHash()` runs without any active tenant context (call `TenantContext.clear()` before the lookup if needed)
- [X] Add a test: validate that a key belonging to tenant B cannot be resolved when the context is set to tenant A

---

## ⚠️ SHORT-TERM — This week

### 4. User suspension & access control tied to tenant status
**Why (your decision):** When a tenant's subscription lapses or the tenant is suspended, ALL users of that tenant must be blocked. Also, individual users can be suspended independently of the tenant.
- [ ] Add `UserStatus` enum to `BaseUser`: `ACTIVE`, `SUSPENDED`, `BLOCKED` (or a boolean `suspended` field + reason)
- [ ] Add `suspendedReason` (String) and `suspendedAt` (Instant) fields to `BaseUser`
- [ ] Update `TenantAccessFilter` to also check `baseUser` status — if user is suspended/blocked, deny access regardless of tenant status
- [ ] When `suspendTenant()` is called, propagate block to all users of that tenant (either eagerly update all users, or check tenant status in the user check — lazy approach is simpler)
- [ ] Expose suspend/unsuspend user endpoints in the admin UI
- [ ] Tenant admin (ADMIN role) should be able to suspend their own tenant's users, but not super-admin users

### 5. Store suspension reason on `TenantEntity`
**Why:** When a tenant or user asks "why is my account suspended?", there's currently no stored reason.
- [ ] Add `suspensionReason` (String, nullable) field to `TenantEntity`
- [ ] Update `suspendTenant(String tenantId, String reason)` to persist the reason
- [ ] Expose `suspensionReason` in the tenant detail view and in any error response shown to the user

### 6. Admin creates first user during tenant onboarding — no other signup path
**Why (your decision):** When a tenant is created, one admin account is created. That admin is responsible for creating all employee accounts. No self-service signup exists.
- [ ] `TenantOnboardingController` already creates the first admin — confirm this is the only user creation path
- [ ] Add UI for the tenant ADMIN to create/manage users within their tenant (name, email, role, password)
- [ ] Ensure `createUserForTenant()` enforces that only `SUPER_ADMIN` or the tenant's own `ADMIN` can create users for that tenant
- [ ] Add role validation: tenant admins cannot create `SUPER_ADMIN` users
- [ ] Add user listing page for tenant ADMIN: `/settings/users`

### 7. API key rotation and revocation
**Why:** There is currently no way to delete/revoke an API key once created.
- [X] Add `DELETE /settings/api-keys/{id}` endpoint
- [X] Add revoke button to `settings/api-keys.html`
- [X] Add `expiresAt` (Instant, nullable) field to `ApiKeyEntity` — null means no expiry
- [X] Update `validateAndGetTenantId()` to reject keys where `expiresAt != null && now.isAfter(expiresAt)`
- [X] Add `lastUsedAt` (Instant) field to `ApiKeyEntity` and update it on each successful validation (useful for auditing unused keys)
- [x] Expose key name, created date, last used, and expiry in the settings UI

---

## 📋 MEDIUM-TERM — Next sprint

### 8. Package domain events
**Why:** `assignToAccount`, future dispatch/delivery events need audit trails, label generation triggers, and notification hooks. Without events, side-effects get tangled into service methods.
- [ ] Define event classes: `PackageReceivedEvent`, `PackageAssignedEvent`, `PackageDispatchedEvent`, `PackageDeliveredEvent`
- [ ] Inject `ApplicationEventPublisher` into `PackageServiceImpl` and publish after each state transition
- [ ] Create `PackageEventEntity` listener that persists audit records (the entity already exists — wire it up)
- [ ] Later: use events to trigger label generation on assignment

### 9. Redirect after receiving + "received, unassigned" page
*(carried from old TODO #1)*
- [ ] After batch receive POST, redirect to `/packages/unassigned`
- [X] `/packages` shows all packages with optional status filter (includes RECEIVED_US_UNASSIGNED)
- [X] Quick-assign control on list page (assign modal with account search)

### 10. Auto-assign from inbound notice
*(carried from old TODO #2)*
- [ ] When a package is received with a tracking number that matches an existing `InboundNotice`, auto-assign to that notice's account
- [ ] Add this check inside `PackageServiceImpl.receivePackage()` (or via a `PackageReceivedEvent` listener)
- [ ] Surface auto-assign results in the batch receive response

### 11. Manual assign package to account
*(carried from old TODO #3)*
- [X] UI flow: select package → search/select account → confirm assignment (modal on `/packages`)
- [X] Call `PackageService.assignPackageToAccount(packageId, accountCode)`
- [X] Show success/error flash on redirect

### 12. Sequential account code generation
**Why:** `AccountCounterService` is a stub — all codes are random today. Sequential codes (e.g. `CR-000123`) are more operator-friendly.
- [ ] Create `account_code_counters` table: `(tenant_id, prefix, last_value)`
- [ ] Implement `AccountCounterService` with atomic `UPDATE ... RETURNING` or pessimistic lock
- [ ] Update `AccountServiceImpl.generateCode()` to use the counter when `supports()` returns true
- [ ] Add a `syncFromAccounts(prefix)` implementation to repair the counter if it drifts

### 13. Labels for assigned packages
*(carried from old TODO #5)*
- [ ] Define label data model: what goes on a label (account code, package tracking, internal code, QR/barcode)
- [ ] Create print-ready Thymeleaf view or PDF generation for labels
- [ ] Trigger label creation on `PackageAssignedEvent`

### 14. Package batches for transport
*(carried from old TODO #4, #6)*
- [X] Flesh out `PackageBatchEntity` — status transitions (OPEN → CLOSED → IN_TRANSIT → ARRIVED → COMPLETED), adding/removing packages
- [X] UI to create a batch and add assigned packages to it (`/packages/batches`, detail, add-packages, seal, in transit, receive at destination)
- [X] Batch status flow: OPEN → CLOSED → IN_TRANSIT → ARRIVED → COMPLETED

### 15. Manifest from package batch
*(carried from old TODO #7)*
- [ ] Generate a printable/exportable manifest from a sealed batch
- [ ] Include: batch code, transport mode, package list with tracking numbers and account codes

### 16. Receive packages at final warehouse
*(carried from old TODO #8)*
- [X] Flow to mark packages as arrived at final warehouse (batch receive at destination; packages → RECEIVED_FINAL)
- [X] Update `PackageStatus` accordingly (RECEIVED_FINAL)
- [ ] Trigger via scan or manual entry (manual flow exists)

### 17. Dispatch packages to customers
*(carried from old TODO #9)*
- [X] Mark package as dispatched / out for delivery / delivered (`/packages/dispatch`)
- [X] Record dispatch timestamp and operator (PackageEventEntity)

---

## 🔮 FUTURE / INFRASTRUCTURE

### 18. API completeness for external portal
- [ ] `GET /api/public/packages` — list packages for a tenant (paginated, filterable by status)
- [ ] `POST /api/integration/packages/receive` — receive a package via API
- [ ] `POST /api/integration/packages/{id}/assign` — assign via API
- [ ] Consistent API error envelope (currently only `ApiExceptionHandler` covers the integration controller)

### 19. Test coverage
**Currently: 1 smoke test (`CourierErpApplicationTests`) + 2 API key tests (`ApiKeyServiceTest`: tenant isolation and key suspension). Target: meaningful coverage of critical paths.**
- [ ] Tenant isolation test: query under tenant A must not return tenant B's data (`@DataJpaTest`)
- [ ] `PackageEntity.assignToAccount()` unit tests: inactive account, already-assigned, happy path
- [ ] `ApiKeyAuthenticationFilter` integration test: missing key → 401, invalid key → 401, valid key → 200 with correct tenant
- [ ] `AccountServiceImpl.ensureAccount()` idempotency test: concurrent calls same `externalRef`
- [ ] `TenantAccessFilter` test: suspended tenant → 403, expired tenant → 403

### 20. Infrastructure hardening (when deploying to AWS)
- [ ] Move all secrets to AWS Secrets Manager / Parameter Store (DB credentials, initial super-admin password)
- [ ] Set `spring.jpa.hibernate.ddl-auto=validate` in production profile (never `update` or `create`)
- [ ] Add `/actuator/health` endpoint (gated, not public) for load balancer health checks
- [ ] Structured logging (JSON) for CloudWatch ingestion
- [ ] Rate limiting on `/api/**` endpoints (per API key)

---

### In-code TODOs (for reference)

- **PackageServiceImpl** (around line 170): `// TODO: investigate why the actor is null here` — event actor null in some path.
- **PackageEntity** (around line 32): `// TODO: OCR Capable Scanner ?` — optional future enhancement.
- **SequentialCodeGenerator**: `// TODO: Change account code <CR> to be configurable by the customer` — prefix/config for sequential codes.

---

### TODOS Extras

- [ ] Azul
- [ ] Agregar parametros al @TenantEntity para integracion con Azul
