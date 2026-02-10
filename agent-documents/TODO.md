## Courier ERP – Development plan

### ~~0. Tenant-from-auth (security) – do first~~ ✅ Done

- ~~**Stop trusting `X-Tenant-ID` header** – Any client can send any tenant and see that tenant’s data.~~ (For authenticated requests, tenant now comes from user; header only used for login/signup.)
- ~~**Set tenant from authenticated user** – In the tenant filter, read tenant from `SecurityContext` (e.g. `BaseUser`’s tenant), not from the request header.~~ (`TenantAccessFilter` sets tenant from `BaseUser.getUserTenantId()`.)
- ~~**API integrations** – For `/api/integration/**` and `/api/public/**`, derive tenant from a validated API key.~~ ✅ Done. API key auth via X-API-Key or Authorization: Bearer. SUPER_ADMIN can create keys for any tenant (POST /api/admin/tenants/{tenantId}/api-keys); DIRECTOR and ADMIN can create keys for their own tenant (POST /api/tenant/api-keys).
- ~~Optionally expose tenant on `BaseUser` (e.g. `getUserTenantId()`) and use it in the filter.~~ (`BaseUser.getUserTenantId()`, `isSuperAdmin()`, `isTenantAdmin()` added.)

### 1. Redirect after receiving + “received, unassigned” page

- When all packages are received (batch submit), redirect to a new page.
- That page shows all packages that are received and not assigned to a customer.

### 2. Auto-assign from inbound notice

- If a customer sent an inbound notice with the same tracking number (and carrier) as a package we’ve received, auto-assign that package to that customer.

### 3. Assign package to an account

- Manual flow: assign a package to an account (customer) from the ERP UI.
- Later: move assignment logic into the domain (e.g. `PackageEntity.assignToAccount(...)`).

### 4. Batching for transport

- Work on batching packages to prepare them for transport (use/expand `PackageBatchEntity` and related flows).

---

## Tomorrow / next

### 5. Assign unassigned packages to customers + labels

- Assign all packages that are in “received, unassigned” to customers (accounts).
- When a package is assigned to a customer, create a **label** that can be printed and pasted onto the package (label entity/data, print-ready view or PDF).

### 6. Create package batches

- Create package batches (use/expand `PackageBatchEntity`) to group packages for transport.

### 7. Create manifest from package batches

- Generate a **manifest** from one or more package batches (e.g. list of packages in the batch for the carrier or last warehouse).

### 8. Receive packages in last warehouse

- Flow to **receive** packages when they arrive at the last warehouse (confirm arrival, update status).

### 9. Dispatch packages to customers

- Flow to **dispatch** packages to customers (mark as out for delivery / delivered, or hand off to carrier).
