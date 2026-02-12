# Courier ERP – Development TODO
*Last updated: February 12, 2026*

**Related documents:** [architecture.md](architecture.md) | [test-run.md](test-run.md)

---

## ✅ Done

**Auth & tenant**
- ~~Tenant isolation via `X-Tenant-ID` header~~ → tenant now set from authenticated `BaseUser` or validated API key
- ~~API key authentication~~ → `ApiKeyAuthenticationFilter`, SHA-256 hashed storage, `ApiKeyService` complete
- ~~Tenant lifecycle~~ → suspend, activate, expire, plan limits wired up
- ~~Remove public signup page~~ (IMMEDIATE #1) → signup controller/template removed or disabled; signup is not a user entry path
- ~~TenantBootstrap expiry~~ (IMMEDIATE #2) → system/default tenants use `subscriptionExpiresAt = null` (never expire); documented in `TenantEntity.isExpired()`
- ~~ApiKeyAuthenticationFilter tenant safety~~ (IMMEDIATE #3) → `ApiKeyEntity` not tenant-scoped; key lookup clears tenant context; test: key owner tenant returned regardless of context

**API keys**
- ~~API key rotation and revocation~~ (SHORT-TERM #7) → `DELETE /settings/api-keys/{id}`, revoke button, `expiresAt` and `lastUsedAt` on `ApiKeyEntity`, validation rejects expired keys, settings UI shows name/created/last used/expiry

**Packages & batches**
- ~~`PackageEntity.assignToAccount()`~~ → domain method with pre-condition validation
- ~~Package list + assign~~ → `/packages` with status filter; assign via modal (search account, assign); `PackageListController`, `PackageService.listAll` with owner fetch
- ~~Packages list with filter + quick-assign~~ (MEDIUM #9 partial) → `/packages` shows all packages with optional status filter; quick-assign control on list page
- ~~Manual assign package to account~~ (MEDIUM #11) → UI flow (modal), `assignPackageToAccount`, success/error flash
- ~~Package batches for transport~~ (MEDIUM #14) → `PackageBatchEntity` status flow OPEN→CLOSED→IN_TRANSIT→ARRIVED→COMPLETED; UI create/add packages/seal/in transit/receive at destination
- ~~Receive at final warehouse~~ (MEDIUM #16 partial) → batch receive at destination; packages → RECEIVED_FINAL; flow and status in place (scan/trigger optional)
- ~~Dispatch to customers~~ (MEDIUM #17) → `/packages/dispatch`; mark out for delivery / delivered; dispatch timestamp and operator in `PackageEventEntity`

**Technical**
- ~~LazyInitializationException on package list~~ → repository methods with `@EntityGraph(attributePaths = {"owner"})` for list, dispatch, batch detail, add-packages
- ~~Database seed~~ → `DatabaseSeedRunner`: demo tenant, admin user, sample accounts, packages, batches

---

## IMMEDIATE — Fix before next feature

*(All immediate items are done; see DONE section.)*

---

## SHORT-TERM — This week

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

---

## MEDIUM-TERM — Next sprint

### 8. Package domain events
**Why:** `assignToAccount`, future dispatch/delivery events need audit trails, label generation triggers, and notification hooks. Without events, side-effects get tangled into service methods.
- [ ] Define event classes: `PackageReceivedEvent`, `PackageAssignedEvent`, `PackageDispatchedEvent`, `PackageDeliveredEvent`
- [ ] Inject `ApplicationEventPublisher` into `PackageServiceImpl` and publish after each state transition
- [ ] Create `PackageEventEntity` listener that persists audit records (the entity already exists — wire it up)
- [ ] Later: use events to trigger label generation on assignment

### 9. Redirect after receiving + "received, unassigned" page
- [ ] After batch receive POST, redirect to `/packages/unassigned`

### 10. Auto-assign from inbound notice
- [ ] When a package is received with a tracking number that matches an existing `InboundNotice`, auto-assign to that notice's account
- [ ] Add this check inside `PackageServiceImpl.receivePackage()` (or via a `PackageReceivedEvent` listener)
- [ ] Surface auto-assign results in the batch receive response

### 12. Sequential account code generation
**Why:** `AccountCounterService` is a stub — all codes are random today. Sequential codes (e.g. `CR-000123`) are more operator-friendly.
- [ ] Create `account_code_counters` table: `(tenant_id, prefix, last_value)`
- [ ] Implement `AccountCounterService` with atomic `UPDATE ... RETURNING` or pessimistic lock
- [ ] Update `AccountServiceImpl.generateCode()` to use the counter when `supports()` returns true
- [ ] Add a `syncFromAccounts(prefix)` implementation to repair the counter if it drifts

### 13. Labels for assigned packages
- [ ] Define label data model: what goes on a label (account code, package tracking, internal code, QR/barcode)
- [ ] Create print-ready Thymeleaf view or PDF generation for labels
- [ ] Trigger label creation on `PackageAssignedEvent`

### 15. Manifest from package batch
- [ ] Generate a printable/exportable manifest from a sealed batch
- [ ] Include: batch code, transport mode, package list with tracking numbers and account codes

### 16. Receive packages at final warehouse
- [ ] Trigger receive-at-destination via scan or manual entry (manual flow exists; add scan/automation if desired)

### 18. Tenant-defined Facilities
**Why:** Tenants need to define their warehouses/hubs so we can record where a package was received and where a batch is destined. Employees assigned to a facility see only that facility’s packages when creating batches, and receive-at facility can be set automatically from the current user.
- [ ] Add `FacilityEntity` (tenant-scoped): e.g. code, name, address/timezone optional; tenant_id via `TenantScopedBaseModel`
- [ ] Facility CRUD: repository, service, MVC (e.g. `/settings/facilities` or under tenant admin)
- [ ] UI for tenant to create/edit/list facilities (at least code + name)
- [ ] **Employee–facility assignment:** Add facility to the user/employee model (e.g. `BaseUser` or tenant user entity: `assignedFacility_id`, nullable). UI for tenant ADMIN to assign users to a facility (e.g. on user create/edit or `/settings/users`).
- [ ] On package receive: **auto-set facility** from the logged-in user’s assigned facility when present; otherwise allow selecting the facility. Persist on `PackageEntity` (e.g. `receivedAtFacility_id`).
- [ ] On batch: allow setting the **destination facility** (final resting place) for the batch; persist on `PackageBatchEntity` (e.g. `destinationFacility_id`).
- [ ] **Batch creation / add-packages:** When listing assignable packages for a batch, filter by the current user’s facility when the user is assigned to a facility (show only packages received at that facility). If user has no facility, show all tenant packages (or require facility assignment for batch workflow).
- [ ] Show facility on package list/detail and batch list/detail where relevant.
- [ ] Optional: validate that receive-at facility and batch destination facility belong to the current tenant.

### 19. Package list refactor + package audit module
**Why:** Separate the “unassigned” workflow from a general package list, and provide a dedicated module where employees can see all packages across all facilities and audit each one.
- [ ] **Refactor:** Rename/change `PackageListController` to **PackageUnassignedController** (or equivalent): focus on unassigned packages (e.g. `/packages/unassigned`), assign modal, redirect after receive to unassigned; keep scope limited to RECEIVED_US_UNASSIGNED workflow.
- [ ] **New controller:** Add a separate **package list** controller (e.g. `PackageListController` or `PackageOverviewController`) for general listing: all packages with status/facility filter, pagination; route e.g. `/packages` or `/packages/list`.
- [ ] **New module — package audit:** View all packages in all facilities; audit each package (detail, verify/update status, audit notes, discrepancies); persist audit trail.
- [ ] Decide URLs and nav: `/packages/unassigned`, `/packages` or `/packages/list`, `/packages/audit` or `/inventory/audit`; update templates and links.

---

## FUTURE / INFRASTRUCTURE

### 20. API completeness for external portal
- [ ] `GET /api/public/packages` — list packages for a tenant (paginated, filterable by status)
- [ ] `POST /api/integration/packages/receive` — receive a package via API
- [ ] `POST /api/integration/packages/{id}/assign` — assign via API
- [ ] Consistent API error envelope (currently only `ApiExceptionHandler` covers the integration controller)

### 21. Test coverage
**Currently: 1 smoke test (`CourierErpApplicationTests`) + 2 API key tests (`ApiKeyServiceTest`: tenant isolation and key suspension). Target: meaningful coverage of critical paths.**
- [ ] Tenant isolation test: query under tenant A must not return tenant B's data (`@DataJpaTest`)
- [ ] `PackageEntity.assignToAccount()` unit tests: inactive account, already-assigned, happy path
- [ ] `ApiKeyAuthenticationFilter` integration test: missing key → 401, invalid key → 401, valid key → 200 with correct tenant
- [ ] `AccountServiceImpl.ensureAccount()` idempotency test: concurrent calls same `externalRef`
- [ ] `TenantAccessFilter` test: suspended tenant → 403, expired tenant → 403

### 22. Infrastructure hardening (when deploying to AWS)
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
