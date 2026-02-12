# Courier ERP – Architecture Document

*Last updated: February 12, 2026*

---

## 1. Overview

Courier ERP is a multi-tenant Spring Boot (4.x) application for courier/warehouse operations: receiving packages, assigning them to customer accounts, batching for transport, receiving at destination, and dispatching/delivery. Each tenant has isolated data; authentication is form-based (MVC) or API-key-based for integrations.

---

## 2. Technology Stack

| Layer | Technology |
|-------|------------|
| Runtime | Java 21 |
| Framework | Spring Boot 4.0.2 |
| Web | Spring MVC, Thymeleaf |
| Security | Spring Security 7.x |
| Data | Spring Data JPA, Hibernate 7.x, PostgreSQL |
| Build | Gradle 8.x |
| Dev/Deploy | Docker Compose (app + Postgres) |

---

## 3. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           HTTP Request                                   │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Security Filter Chain (login, API key, CSRF, session)                   │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  TenantContextFilter   → sets tenant from login (tenant field)           │
│  TenantAccessFilter    → validates tenant (active, not expired/suspended)│
│  TenantAccessFilter    → sets TenantContext for JPA multi-tenancy       │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Controllers (MVC: packages, batches, accounts, admin, auth, settings)   │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Services (Package, PackageBatch, Account, Tenant, ApiKey, Auth)         │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Repositories (JPA)  →  PostgreSQL (tenant-discriminated tables)         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Multi-Tenancy

- **Tenant identification:** Stored in `TenantContext` (ThreadLocal). Set by:
  - **MVC:** Login form “Tenant ID” field; user must belong to that tenant.
  - **API:** `ApiKeyAuthenticationFilter` — validates API key and sets tenant from the key’s owning tenant (ignores any header; key lookup is tenant-agnostic).
- **Data isolation:** Entities that are tenant-scoped extend `TenantScopedBaseModel` and use `TenantIdentifierResolver` so Hibernate applies `tenant_id` on all queries and inserts.
- **Non-tenant entities:** e.g. `TenantEntity`, `ApiKeyEntity` — not filtered by tenant; used for cross-tenant admin and key validation.
- **Special tenant IDs:** `system` (super-admin on `/admin/*`), `default` (fallback when tenant not set). System and default tenants are created by `TenantBootstrap` with `subscriptionExpiresAt = null` (never expire).

---

## 5. Security

- **Authentication:**
  - **Form login:** `/auth/login` — tenant ID + email + password; `AuthService` loads user by tenant + email.
  - **API:** `Authorization: ApiKey <raw-key>` — `ApiKeyAuthenticationFilter` hashes key, looks up `ApiKeyEntity` by hash, validates tenant/expiry/suspension, sets `TenantContext` and security context.
- **Roles:** `SUPER_ADMIN` (platform), `DIRECTOR`, `ADMIN` (tenant). Super-admin can access `/admin/*` with tenant `system`.
- **Public endpoints:** Login, logout, static resources, and any path explicitly marked public in `TenantAccessFilter` (e.g. health, public API) — no tenant required.
- **Signup:** Public signup removed; tenant onboarding is the only way to create tenant users (first admin created during onboarding).

---

## 6. Module Structure

```
com.menval.couriererp
├── CourierErpApplication.java
├── admin/            → Super-admin: tenant CRUD, onboarding, API keys for tenants
├── auth/             → Login, user model (BaseUser), no signup
├── configuration/    → JPA auditing
├── modules/
│   ├── common/       → BaseModel, TenantScopedBaseModel
│   └── courier/
│       ├── account/  → Accounts (customers), code generation, MVC + integration API
│       └── packages  → Packages, batches, receiving, dispatch, list, events
├── security/         → ApiKey filter, Spring Security config
├── seed/             → DatabaseSeedRunner (demo tenant, accounts, packages, batches)
└── tenant/           → TenantContext, filters, entities, services, repos, exceptions
```

---

## 7. Package Workflow (Domain)

- **Package lifecycle (status):**  
  `RECEIVED_US_UNASSIGNED` → (assign) → `RECEIVED_US_ASSIGNED` → (add to batch) → … → `RECEIVED_FINAL` / `OUT_FOR_DELIVERY` → (dispatch) → `DELIVERED`.
- **Batch lifecycle:**  
  `OPEN` → (seal) → `CLOSED` → (mark in transit) → `IN_TRANSIT` → (receive at destination) → `ARRIVED` → `COMPLETED`.
- **Key entities:** `PackageEntity` (carrier, tracking, status, owner `AccountEntity`, optional batch), `PackageBatchEntity` (status, transport mode), `PackageEventEntity` (audit), `AccountEntity` (customer account, tenant-scoped).
- **Lazy loading:** With `open-in-view: false` (e.g. Docker profile), list/detail views use repository methods that eager-fetch `owner` via `@EntityGraph(attributePaths = {"owner"})` (e.g. `findAllWithOwner`, `findByStatusWithOwner`, `findByBatch_IdWithOwner`) to avoid `LazyInitializationException` in Thymeleaf.

---

## 8. Controllers and Entry Points

| Area | Controllers | Main URLs |
|------|-------------|-----------|
| Auth | AuthController | `/auth/login`, `/auth/logout` |
| Admin | AdminTenantMvcController, TenantOnboardingController | `/admin/tenants`, `/admin/tenants/new`, etc. |
| Packages | PackageListController, PackageReceivingController, PackageBatchController, PackageDispatchController | `/packages`, `/packages/receiving`, `/packages/batches`, `/packages/dispatch` |
| Accounts | AccountMVCController | `/accounts` |
| Settings | SettingsMvcController | `/settings/api-keys` |
| API (integration) | AccountIntegrationController, PublicPackageController | `/api/integration/*`, `/api/public/*` |

---

## 9. Configuration Highlights

- **Profiles:** `default` (local), `docker` (compose: DB URL, JPA, `open-in-view: false`).
- **Database:** PostgreSQL; schema via Hibernate `ddl-auto` (e.g. `update` in dev/docker; production should use `validate` + Flyway).
- **Docker:** `compose.yaml` — app service (port 8080) + Postgres 16 with healthcheck; app uses `SPRING_PROFILES_ACTIVE=docker`.

---

## 10. Cross-Cutting Concerns

- **Auditing:** JPA `@CreatedDate` / `@LastModifiedDate` and custom audit for package events (e.g. receive, assign, dispatch) with actor reference.
- **Errors:** `TenantExceptionHandler` (tenant exceptions), `ApiExceptionHandler` (integration API); validation via Spring Validation.
- **Seeding:** `DatabaseSeedRunner` creates demo tenant, admin user, sample accounts, packages, and batches for testing the full flow.

---

## 11. References

- **First run:** `FIRST-RUN.md`
- **Development TODO:** `agent-documents/TODO.md`
- **Test run:** `agent-documents/test-run.md`
