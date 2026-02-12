# Courier ERP

Multi-tenant courier and warehouse management application: receive packages, assign to customer accounts, batch for transport, receive at destination, and dispatch/deliver. Built with Spring Boot and PostgreSQL.

---

## Features

- **Multi-tenancy** — Tenant isolation; login by tenant + email; API keys per tenant
- **Packages** — Receive by carrier + tracking; list with status filter; assign to accounts (customer codes)
- **Batches** — Create batches, add/remove packages, seal, mark in transit, receive at destination
- **Dispatch** — Mark packages out for delivery or delivered; audit events
- **Accounts** — Customer accounts (tenant-scoped); search for assignment
- **Admin** — Super-admin: create tenants, onboard first user, manage API keys
- **API** — Integration and public endpoints with API key authentication

---

## Tech stack

| Layer        | Technology                    |
|-------------|-------------------------------|
| Runtime     | Java 21                       |
| Framework   | Spring Boot 4.x               |
| Web         | Spring MVC, Thymeleaf         |
| Security    | Spring Security               |
| Data        | Spring Data JPA, PostgreSQL   |
| Build       | Gradle                        |
| Deployment  | Docker Compose (app + DB)    |

---

## Prerequisites

- **Java 21**
- **PostgreSQL** (or run everything via Docker Compose)

---

## Quick start

### Option A: Local (Gradle + your own Postgres)

1. Configure a PostgreSQL database and set `spring.datasource.*` (e.g. in `application.properties` or profile).
2. Run the application:

   ```bash
   ./gradlew bootRun
   ```

3. Open **http://localhost:8080** and log in (see [First run](#first-run) below).

### Option B: Docker Compose

1. Start app and database:

   ```bash
   docker compose up -d
   ```

2. App runs at **http://localhost:8080** (port 8080). Postgres is on port 5432.

3. On first start the app creates default/system tenants and a super-admin user (see [First run](#first-run)).

---

## First run

1. **Login (super-admin)**  
   Go to **http://localhost:8080/auth/login**  
   - Tenant ID: `system`  
   - Email: `superadmin@example.com`  
   - Password: `changeme`

2. **Create a tenant**  
   Go to **http://localhost:8080/admin/tenants** → Create tenant → fill form (tenant ID, company name, first admin user, etc.).

3. **Use as tenant user**  
   Log out, then log in with Tenant ID = your tenant (e.g. `default` or the one you created), and the first admin email/password.

4. **Receive packages**  
   As tenant user: **http://localhost:8080/packages/receiving** — choose carrier, paste tracking numbers, submit.

5. **Full walkthrough**  
   See **[FIRST-RUN.md](FIRST-RUN.md)** for step-by-step instructions.

---

## Project structure

```
src/main/java/com/menval/couriererp/
├── CourierErpApplication.java
├── admin/           # Super-admin: tenants, onboarding
├── auth/            # Login (no public signup)
├── modules/courier/
│   ├── account/     # Customer accounts, MVC + API
│   └── packages/    # Packages, batches, receiving, dispatch, list
├── security/        # API key filter, Security config
├── seed/            # DatabaseSeedRunner (demo data)
└── tenant/          # TenantContext, filters, entities, services
```

---

## Documentation

| Document | Description |
|----------|-------------|
| [FIRST-RUN.md](FIRST-RUN.md) | Step-by-step first run and login |
| [agent-documents/architecture.md](agent-documents/architecture.md) | Architecture, multi-tenancy, security, modules |
| [agent-documents/test-run.md](agent-documents/test-run.md) | How to run tests, current test inventory |
| [agent-documents/TODO.md](agent-documents/TODO.md) | Development TODO and roadmap |

---

## Tests

```bash
./gradlew test
```

See **[agent-documents/test-run.md](agent-documents/test-run.md)** for details and test inventory.

---

## License

Proprietary. All rights reserved.
