package com.menval.couriererp.tenant.services;

import com.menval.couriererp.tenant.entities.*;
import com.menval.couriererp.tenant.exceptions.TenantAlreadyExistsException;
import com.menval.couriererp.tenant.exceptions.TenantNotFoundException;
import com.menval.couriererp.tenant.repositories.TenantRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;

    public TenantServiceImpl(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public TenantEntity createTenant(CreateTenantCommand command) {
        if (tenantRepository.existsByTenantId(command.tenantId())) {
            throw new TenantAlreadyExistsException("Tenant with ID '" + command.tenantId() + "' already exists");
        }
        if (command.domain() != null && !command.domain().isBlank() && tenantRepository.existsByDomain(command.domain())) {
            throw new TenantAlreadyExistsException("Domain '" + command.domain() + "' is already in use");
        }

        TenantSettings settings = new TenantSettings();
        settings.setAccountCodePrefix(command.accountCodePrefix() != null ? command.accountCodePrefix() : deriveCodePrefix(command.companyName()));
        settings.setAccountCodeLength(6);
        settings.setTimezone("UTC");
        settings.setCurrency("USD");
        applyPlanLimits(settings, command.plan());

        TenantEntity tenant = TenantEntity.builder()
                .tenantId(command.tenantId())
                .companyName(command.companyName())
                .domain(command.domain())
                .active(true)
                .status(command.plan() == SubscriptionPlan.TRIAL ? TenantStatus.TRIAL : TenantStatus.ACTIVE)
                .plan(command.plan())
                .subscriptionStartsAt(Instant.now())
                .subscriptionExpiresAt(command.subscriptionExpiresAt())
                .primaryContactName(command.primaryContactName())
                .primaryContactEmail(command.primaryContactEmail())
                .primaryContactPhone(command.primaryContactPhone())
                .billingEmail(command.primaryContactEmail())
                .settings(settings)
                .build();

        return tenantRepository.save(tenant);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<TenantEntity> listAllTenants() {
        return tenantRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public TenantEntity getTenantById(String tenantId) {
        return tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void suspendTenant(String tenantId, String reason) {
        TenantEntity tenant = getTenantById(tenantId);
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenant.setActive(false);
        tenantRepository.save(tenant);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void activateTenant(String tenantId) {
        TenantEntity tenant = getTenantById(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setActive(true);
        tenantRepository.save(tenant);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void updateTenantSettings(String tenantId, TenantSettings settings) {
        TenantEntity tenant = getTenantById(tenantId);
        tenant.setSettings(settings);
        tenantRepository.save(tenant);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void extendSubscription(String tenantId, Instant newExpirationDate) {
        TenantEntity tenant = getTenantById(tenantId);
        tenant.setSubscriptionExpiresAt(newExpirationDate);
        if (tenant.getStatus() == TenantStatus.EXPIRED) {
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant.setActive(true);
        }
        tenantRepository.save(tenant);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void changePlan(String tenantId, SubscriptionPlan newPlan) {
        TenantEntity tenant = getTenantById(tenantId);
        tenant.setPlan(newPlan);
        applyPlanLimits(tenant.getSettings(), newPlan);
        tenantRepository.save(tenant);
    }

    private static void applyPlanLimits(TenantSettings settings, SubscriptionPlan plan) {
        if (plan == null) return;
        switch (plan) {
            case TRIAL, STARTER -> {
                settings.setMaxUsers(5);
                settings.setMaxPackagesPerMonth(500);
            }
            case PROFESSIONAL -> {
                settings.setMaxUsers(20);
                settings.setMaxPackagesPerMonth(5000);
            }
            case ENTERPRISE -> {
                settings.setMaxUsers(100);
                settings.setMaxPackagesPerMonth(999999);
            }
        }
    }

    private static String deriveCodePrefix(String companyName) {
        if (companyName == null || companyName.length() < 2) return "CR";
        String[] words = companyName.trim().split("\\s+");
        if (words.length >= 3) {
            return (words[0].substring(0, 1) + words[1].substring(0, 1) + words[2].substring(0, 1)).toUpperCase();
        }
        if (words.length == 2) {
            return (words[0].substring(0, Math.min(2, words[0].length())) + words[1].substring(0, 1)).toUpperCase();
        }
        return words[0].substring(0, Math.min(3, words[0].length())).toUpperCase();
    }
}
