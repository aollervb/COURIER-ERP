package com.menval.couriererp.admin.controllers;

import com.menval.couriererp.admin.dto.OnboardTenantForm;
import com.menval.couriererp.auth.models.UserRoles;
import com.menval.couriererp.auth.services.AuthService;
import com.menval.couriererp.tenant.entities.SubscriptionPlan;
import com.menval.couriererp.tenant.services.CreateTenantCommand;
import com.menval.couriererp.tenant.services.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * SUPER_ADMIN only: form to create a tenant and its first admin user.
 */
@Controller
@RequestMapping("/admin/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class TenantOnboardingController {

    private final TenantService tenantService;
    private final AuthService authService;

    @GetMapping("/new")
    public String newTenantForm(Model model) {
        model.addAttribute("form", new OnboardTenantForm());
        model.addAttribute("plans", SubscriptionPlan.values());
        return "admin/tenants/new";
    }

    @PostMapping
    public String onboardTenant(@Valid OnboardTenantForm form, BindingResult bindingResult,
                                Model model, RedirectAttributes redirectAttributes) {
        if (!form.getAdminPassword().equals(form.getAdminConfirmPassword())) {
            bindingResult.rejectValue("adminConfirmPassword", "password.mismatch", "Passwords do not match");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("plans", SubscriptionPlan.values());
            return "admin/tenants/new";
        }

        Instant expiresAt = form.getSubscriptionMonths() != null
                ? Instant.now().plus(form.getSubscriptionMonths() * 30L, ChronoUnit.DAYS)
                : Instant.now().plus(365, ChronoUnit.DAYS);

        CreateTenantCommand command = new CreateTenantCommand(
                form.getTenantId().trim().toLowerCase(),
                form.getCompanyName().trim(),
                form.getDomain() != null ? form.getDomain().trim().toLowerCase() : null,
                form.getPrimaryContactName().trim(),
                form.getPrimaryContactEmail().trim(),
                form.getPrimaryContactPhone() != null ? form.getPrimaryContactPhone().trim() : null,
                form.getPlan(),
                expiresAt,
                form.getAccountCodePrefix()
        );

        tenantService.createTenant(command);
        authService.createUserForTenant(
                form.getTenantId().trim().toLowerCase(),
                form.getAdminEmail().trim(),
                form.getAdminFirstName().trim(),
                form.getAdminLastName().trim(),
                form.getAdminPassword(),
                UserRoles.ADMIN
        );

        redirectAttributes.addFlashAttribute("message", "Tenant and admin user created: " + form.getTenantId());
        return "redirect:/admin/tenants";
    }
}
