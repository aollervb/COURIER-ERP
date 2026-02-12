# Courier ERP – Test Run Document

*Last updated: February 12, 2026*

---

## 1. How to Run Tests

### Prerequisites

- **Java 21** (project uses Java 21 toolchain in `build.gradle`)
- **Gradle:** Use the wrapper included in the project (`./gradlew`)

### Run all tests

```bash
./gradlew test
```

### Run tests with verbose output

```bash
./gradlew test --info
```

### Run tests without daemon (CI-friendly)

```bash
./gradlew test --no-daemon
```

### Run a single test class

```bash
./gradlew test --tests "com.menval.couriererp.CourierErpApplicationTests"
./gradlew test --tests "com.menval.couriererp.tenant.services.ApiKeyServiceTest"
```

### Run from IDE

- **IntelliJ / Eclipse:** Right-click `src/test/java` or a test class → Run Tests.
- Tests use `@SpringBootTest` and load the full application context; they require a configured datasource (in-memory H2 or test profile with Postgres).

---

## 2. Test Configuration

- **Framework:** JUnit 5 (Jupiter), `@SpringBootTest` for integration tests.
- **Dependencies (from `build.gradle`):**
  - `spring-boot-starter-webmvc-test`
  - `spring-security-test`
  - `junit-platform-launcher` (test runtime)
- **Datasource:** Spring Boot test slice uses the same `application.properties` / `application-docker.yml` by default. For tests that hit the DB, ensure either:
  - An in-memory DB (e.g. H2) is configured in a test profile, or
  - A running Postgres instance is available and the test profile points to it.

---

## 3. Current Test Inventory

| Test Class | Type | Purpose |
|------------|------|---------|
| `CourierErpApplicationTests` | Smoke | Verifies application context loads (`contextLoads()`). |
| `ApiKeyServiceTest` | Integration | Validates API key tenant isolation and suspension: (1) `validateAndGetTenantId` returns the key owner’s tenant even when `TenantContext` is set to another tenant; (2) suspended keys return empty. Uses `@SpringBootTest` and `@Transactional`. |

**Total:** 2 test classes; 3 test methods (1 smoke + 2 API key tests).

---

## 4. Expected Results

- **All tests passing:** Exit code 0; report under `build/reports/tests/test/`.
- **Failures:** Check `build/reports/tests/test/index.html` for details. Common causes:
  - No Java 21 or wrong JDK.
  - Database not available or wrong URL (test profile).
  - `TenantContext` not cleared between tests (e.g. `ApiKeyServiceTest` uses `@AfterEach` to clear).

---

## 5. Test Coverage Goals (from TODO)

The project TODO lists the following as desired coverage:

- Tenant isolation test: query under tenant A must not return tenant B’s data (`@DataJpaTest`).
- `PackageEntity.assignToAccount()` unit tests: inactive account, already-assigned, happy path.
- `ApiKeyAuthenticationFilter` integration test: missing key → 401, invalid key → 401, valid key → 200 with correct tenant.
- `AccountServiceImpl.ensureAccount()` idempotency test: concurrent calls with same `externalRef`.
- `TenantAccessFilter` test: suspended tenant → 403, expired tenant → 403.

See `agent-documents/TODO.md` (section 19) for the full list.

---

## 6. CI / Headless Run

For CI pipelines, run tests in a clean environment:

```bash
./gradlew clean test --no-daemon
```

Ensure the CI environment has Java 21 and, if tests use Postgres, a running database or test container configured for the test profile.
