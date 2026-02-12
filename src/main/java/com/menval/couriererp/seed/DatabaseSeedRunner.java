package com.menval.couriererp.seed;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.auth.models.UserRoles;
import com.menval.couriererp.auth.repository.UserRepository;
import com.menval.couriererp.modules.courier.account.entities.EnsureAccountCommand;
import com.menval.couriererp.modules.courier.account.services.AccountService;
import com.menval.couriererp.modules.courier.packages.entities.Carrier;
import com.menval.couriererp.modules.courier.packages.entities.batchPackages.TransportMode;
import com.menval.couriererp.modules.courier.packages.services.PackageBatchService;
import com.menval.couriererp.modules.courier.packages.services.PackageService;
import com.menval.couriererp.tenant.TenantContext;
import com.menval.couriererp.tenant.entities.*;
import com.menval.couriererp.tenant.repositories.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * Seeds demo tenant, admin user, customer accounts, and optional packages/batches
 * so you can run a full package workflow test after emptying the database.
 * <p>
 * Runs only when demo tenant "default" does not exist (e.g. after empty DB + restart).
 * Credentials after seed: superadmin@example.com / changeme (existing bootstrap);
 * admin@demo.com / demo (tenant "default") for package flow.
 */
@Component
@Order(300)
@RequiredArgsConstructor
public class DatabaseSeedRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseSeedRunner.class);

    public static final String DEMO_TENANT_ID = "default";
    private static final String DEMO_ADMIN_EMAIL = "admin@demo.com";
    private static final String DEMO_ADMIN_PASSWORD = "demo";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accountService;
    private final PackageService packageService;
    private final PackageBatchService packageBatchService;

    @Override
    public void run(ApplicationArguments args) {
        if (tenantRepository.findByTenantId(DEMO_TENANT_ID).isPresent()) {
            logger.debug("Demo tenant '{}' already exists, skipping seed.", DEMO_TENANT_ID);
            return;
        }

        logger.info("Seeding demo data (tenant {}, admin {}, accounts, packages, batches).", DEMO_TENANT_ID, DEMO_ADMIN_EMAIL);

        TenantSettings settings = new TenantSettings();
        settings.setAccountCodePrefix("CR");
        settings.setAccountCodeLength(6);

        TenantEntity demoTenant = TenantEntity.builder()
                .tenantId(DEMO_TENANT_ID)
                .companyName("Demo Courier")
                .domain(null)
                .active(true)
                .status(TenantStatus.ACTIVE)
                .plan(SubscriptionPlan.ENTERPRISE)
                .subscriptionStartsAt(Instant.now())
                .subscriptionExpiresAt(null)
                .settings(settings)
                .build();
        tenantRepository.save(demoTenant);

        TenantContext.setTenantId(DEMO_TENANT_ID);
        try {
            BaseUser admin = BaseUser.builder()
                    .email(DEMO_ADMIN_EMAIL)
                    .firstName("Demo")
                    .lastName("Admin")
                    .password(passwordEncoder.encode(DEMO_ADMIN_PASSWORD))
                    .roles(Set.of(UserRoles.ADMIN))
                    .accountNonExpired(true)
                    .credentialsNonExpired(true)
                    .accountNonLocked(true)
                    .enabled(true)
                    .build();
            admin.setTenantId(DEMO_TENANT_ID);
            userRepository.save(admin);

            seedAccounts();
            seedPackagesAndBatches();
        } finally {
            TenantContext.clear();
        }

        logger.info("Demo seed complete. Log in as {} / {} (tenant {}) to test package flow.", DEMO_ADMIN_EMAIL, DEMO_ADMIN_PASSWORD, DEMO_TENANT_ID);
    }

    private void seedAccounts() {
        accountService.ensureAccount(new EnsureAccountCommand("SEED-CUST-1", "Customer One", "c1@example.com", null, null));
        accountService.ensureAccount(new EnsureAccountCommand("SEED-CUST-2", "Customer Two", "c2@example.com", null, null));
        accountService.ensureAccount(new EnsureAccountCommand("SEED-CUST-3", "Customer Three", "c3@example.com", null, null));
        accountService.ensureAccount(new EnsureAccountCommand("SEED-CUST-4", "Customer Four", "c4@example.com", null, null));
        accountService.ensureAccount(new EnsureAccountCommand("SEED-CUST-5", "Customer Five", "c5@example.com", null, null));
    }

    private void seedPackagesAndBatches() {
        var p1 = packageService.receivePackage(Carrier.DHL, "SEED-TRK-001", null);
        var p2 = packageService.receivePackage(Carrier.UPS, "SEED-TRK-002", null);
        var p3 = packageService.receivePackage(Carrier.FEDEX, "SEED-TRK-003", null);

        var acc1 = accountService.getByExternalRef("SEED-CUST-1");
        var acc2 = accountService.getByExternalRef("SEED-CUST-2");
        packageService.assignPackageToAccount(p2.getId(), acc1.getCode(), null);
        packageService.assignPackageToAccount(p3.getId(), acc2.getCode(), null);

        var batch1 = packageBatchService.createBatch(
                "SEED-BATCH-001", TransportMode.AIR,
                "MIA", "SDQ", "DO",
                null, null, null);
        packageBatchService.addPackageToBatch(batch1.getId(), p2.getId());
        packageBatchService.sealBatch(batch1.getId());
        packageBatchService.markBatchInTransit(batch1.getId());

        var batch2 = packageBatchService.createBatch(
                "SEED-BATCH-002", TransportMode.AIR,
                "MIA", "SDQ", "DO",
                null, null, null);
        packageBatchService.addPackageToBatch(batch2.getId(), p3.getId());
        packageBatchService.sealBatch(batch2.getId());
        packageBatchService.markBatchInTransit(batch2.getId());
        packageBatchService.markBatchArrived(batch2.getId(), "SDQ", null);
    }
}
