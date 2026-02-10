## Courier ERP – Development plan

### 0. Tenant-from-auth (security) – do first

- **Stop trusting `X-Tenant-ID` header** – Any client can send any tenant and see that tenant’s data.
- **Set tenant from authenticated user** – In the tenant filter, read tenant from `SecurityContext` (e.g. `BaseUser`’s tenant), not from the request header.
- **API integrations** – For `/api/integration/**` and `/api/public/**`, derive tenant from a validated API key (or similar), not from a client-provided header.
- Optionally expose tenant on `BaseUser` (e.g. `getUserTenantId()`) and use it in the filter.

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
