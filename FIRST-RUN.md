# First run – Courier ERP

This guide walks through starting the app, creating a super-admin, creating a tenant, creating an employee in that tenant, and receiving packages.

## Prerequisites

- **Java 21**
- **PostgreSQL** (or use Docker Compose: the project has `compose.yaml`; run `docker compose up -d` then start the app)

## 1. Start the application

```bash
./gradlew bootRun
```

Or from your IDE: run `CourierErpApplication`.

- On first start, the app creates:
  - Tenant **default** (if no tenants exist)
  - Tenant **system** (if missing)
  - User **superadmin@example.com** with role SUPER_ADMIN (if no super-admin exists yet), password: **changeme**

- Default URL: **http://localhost:8080**

---

## 2. Log in as super-admin

1. Open **http://localhost:8080/auth/login**
2. Fill the form:
   - **Tenant ID:** `system` (for platform admin)
   - **Email:** `superadmin@example.com`
   - **Password:** `changeme`
3. Click **Login**. You should be redirected to the home page.

---

## 3. Create a tenant and its first admin (employee)

1. Go to **http://localhost:8080/admin/tenants** (tenant list).
2. Click **Create tenant** → **http://localhost:8080/admin/tenants/new**
3. Fill the form:
   - **Tenant ID:** e.g. `jetpack` (lowercase, numbers, hyphens)
   - **Company name:** e.g. Jetpack Inc
   - **Client subdomain (their ERP URL):** e.g. **jetpack.myerp.com** — the full subdomain this client will use. Required.
   - **Primary contact name / email** (required)
   - **Plan:** e.g. STARTER
   - **Subscription months:** e.g. 12
   - **First admin user:** email, first name, last name, password, confirm password (this is the employee account for this tenant)
4. Submit. You are redirected to the tenant list; the new tenant and its first admin user exist.

---

## 4. Log out and log in as the tenant employee

1. **Log out:** open **http://localhost:8080/auth/logout** (GET or POST; no CSRF needed). You can use a simple link or a form that POSTs to `/auth/logout`.
2. Open **http://localhost:8080/auth/login**
3. Fill the form: **Tenant ID** = your tenant (e.g. `jetpack`), **Email** = first admin email, **Password** = that user's password.
4. You are now in the ERP as that tenant’s user (DIRECTOR/ADMIN).

**Tenant ID on login:** The **only** way to set the tenant is the **Tenant ID** field on the login form. Enter the tenant identifier (e.g. `jetpack`) or **`system`** for platform super-admin. Only users belonging to that tenant can log in.

---

## 5. Receive packages

1. As the tenant employee, go to **http://localhost:8080/packages/receiving**
2. Choose a **carrier** (e.g. DHL, FedEx).
3. Enter **tracking numbers**, one per line, e.g.:
   ```
   1234567890
   ABC123456789
   ```
4. Click **Submit**. The packages are received and appear in the list below (status: received, unassigned).

---

## Optional

- **Logout:** GET or POST to **http://localhost:8080/auth/logout** (no CSRF token required).
- **Create API key (tenant user):** **http://localhost:8080/settings/api-keys** — create a key to call `/api/public/**` and `/api/integration/**`.
- **Create API key (super-admin for a tenant):** **http://localhost:8080/admin/tenants** → open a tenant → **Create API key for this tenant**.
- **Change super-admin password:** Use your own user-management flow or update the user in the database; the bootstrap only creates the first super-admin if none exist.

---

## Summary

| Step | URL / action |
|------|------------------|
| Start app | `./gradlew bootRun` |
| Login (super-admin) | `/auth/login` → superadmin@example.com / changeme |
| Create tenant + first employee | `/admin/tenants` → Create tenant → fill form |
| Login (tenant employee) | `/auth/login` → Tenant ID = e.g. jetpack, then email + password |
| Receive packages | `/packages/receiving` → carrier + tracking numbers |
