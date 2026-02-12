# How to test the whole application (using database seed)

This guide describes how to run the application with a **seeded database** so you can test the full package workflow (list, assign, batches, receive at destination, dispatch) without manually creating tenants or receiving packages first.

---

## Prerequisites

- **Java 21**
- **PostgreSQL** (or use Docker Compose: run `docker compose up -d` for app + database)

---

## 1. Start the application (with empty or fresh database)

For the **database seed** to run, the demo tenant must not exist. Use a fresh/empty database, or drop and recreate it, then start the app.

**Option A — Docker Compose (recommended)**

```bash
docker compose up -d
```

App runs at **http://localhost:8080**. The seed runs on first startup if tenant `default` does not exist.

**Option B — Local (Gradle + your own Postgres)**

1. Point the app at an empty PostgreSQL database (e.g. set `spring.datasource.url` in `application.properties` or a profile).
2. Run:

```bash
./gradlew bootRun
```

Or run `CourierErpApplication` from your IDE.

**What runs on first start**

- **TenantBootstrap** (if needed): creates tenant **system**, tenant **default** (if no tenants exist), and user **superadmin@example.com** / **changeme** (super-admin).
- **DatabaseSeedRunner** (only if tenant `default` does not exist): seeds demo data (see below). If `default` already exists, the seed is skipped.

---

## 2. What the seed creates

| Data | Details |
|------|---------|
| **Demo tenant** | `default` — "Demo Courier" |
| **Demo admin user** | **admin@demo.com** / **demo** (tenant: `default`) |
| **Customer accounts** | SEED-CUST-1 … SEED-CUST-5 (Customer One … Five) |
| **Packages** | SEED-TRK-001 (DHL, unassigned), SEED-TRK-002 (UPS, assigned to SEED-CUST-1), SEED-TRK-003 (FEDEX, assigned to SEED-CUST-2) |
| **Batches** | SEED-BATCH-001: 1 package, sealed, **IN_TRANSIT** — SEED-BATCH-002: 1 package, sealed, **IN_TRANSIT** then **ARRIVED** (received at destination); package in batch 2 is RECEIVED_FINAL |

---

## 3. Log in as the demo tenant user

1. Open **http://localhost:8080/auth/login**
2. Use the **seeded** tenant user:
   - **Tenant ID:** `default`
   - **Email:** `admin@demo.com`
   - **Password:** `demo`
3. Click **Login**. You are in the ERP as the demo tenant’s admin.

---

## 4. Test the package list and assign

1. Go to **http://localhost:8080/packages**
2. You should see **3 packages** (SEED-TRK-001, SEED-TRK-002, SEED-TRK-003). One is unassigned (RECEIVED_US_UNASSIGNED), two are assigned.
3. Use the **Filter by status** dropdown (e.g. "All" or "RECEIVED_US_UNASSIGNED") to verify filtering.
4. **Assign** the unassigned package: click **Assign**, search for an account (e.g. SEED-CUST-3), select it, confirm. The list updates and the package shows as assigned.

---

## 5. Test batches

1. Go to **http://localhost:8080/packages/batches**
2. You should see **2 batches** (e.g. SEED-BATCH-001, SEED-BATCH-002). One is IN_TRANSIT, one is ARRIVED.
3. Open **SEED-BATCH-001** (IN_TRANSIT): view package, check that seal/in-transit actions are reflected.
4. Open **SEED-BATCH-002** (ARRIVED): view package; it should be RECEIVED_FINAL (ready for dispatch).
5. **Optional:** Create a new batch: **New batch** → fill code, transport mode, origin/destination → Save. Then **Add packages**, pick assignable packages (e.g. the one you just assigned), add to batch, **Seal**, **Mark in transit**. For a batch that is IN_TRANSIT, you can **Receive at destination** to move it to ARRIVED and set packages to RECEIVED_FINAL.

---

## 6. Test dispatch

1. Go to **http://localhost:8080/packages/dispatch**
2. You should see packages that are **RECEIVED_FINAL** or **OUT_FOR_DELIVERY** (e.g. the package from SEED-BATCH-002).
3. Use **Out for delivery** or **Delivered** to move a package through the dispatch flow. Timestamp and operator are recorded.

---

## 7. Test receiving more packages

1. Go to **http://localhost:8080/packages/receiving**
2. Choose a **carrier** (e.g. DHL) and enter **tracking numbers** (one per line). Submit.
3. New packages appear with status RECEIVED_US_UNASSIGNED. Go to **Packages** to assign them or add them to a batch.

---

## 8. Test super-admin (optional)

1. Log out: **http://localhost:8080/auth/logout**
2. Log in as **super-admin**:
   - **Tenant ID:** `system`
   - **Email:** `superadmin@example.com`
   - **Password:** `changeme`
3. Go to **http://localhost:8080/admin/tenants** to see tenants (including `default`). You can create more tenants, onboard first users, and manage API keys from here.

---

## Summary

| Step | URL / action |
|------|------------------|
| Start app (fresh DB so seed runs) | `docker compose up -d` or `./gradlew bootRun` |
| Login (demo tenant) | `/auth/login` → Tenant: **default**, Email: **admin@demo.com**, Password: **demo** |
| Package list + assign | `/packages` → filter, assign unassigned package |
| Batches | `/packages/batches` → list, detail, (optional) new batch, add packages, seal, in transit, receive at destination |
| Dispatch | `/packages/dispatch` → out for delivery / delivered |
| Receive more | `/packages/receiving` → carrier + tracking numbers |
| Super-admin | Logout → login with **system** / **superadmin@example.com** / **changeme** → `/admin/tenants` |

---

## Resetting the seed

The seed runs **only once**: when tenant `default` does not exist. To run it again (e.g. after emptying the database):

1. Drop the database or delete all data (and ensure tenant `default` is gone).
2. Restart the application. `DatabaseSeedRunner` will create the demo tenant, user, accounts, packages, and batches again.

If tenant `default` already exists, the seed is skipped and no duplicate demo data is created.
