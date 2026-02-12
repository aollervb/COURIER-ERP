package com.menval.couriererp.admin.controllers;

import com.menval.couriererp.tenant.entities.TenantEntity;
import com.menval.couriererp.tenant.services.ApiKeyService;
import com.menval.couriererp.tenant.services.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * SUPER_ADMIN only: ERP views to list tenants, view detail, suspend, activate, create API keys.
 * All interaction via browser (session auth), not REST.
 */
@Controller
@RequestMapping("/admin/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminTenantMvcController {

    private final TenantService tenantService;
    private final ApiKeyService apiKeyService;

    @GetMapping
    public String list(Model model) {
        List<TenantEntity> tenants = tenantService.listAllTenants();
        model.addAttribute("tenants", tenants);
        return "admin/tenants/list";
    }

    @GetMapping("/{tenantId}")
    public String detail(@PathVariable String tenantId, Model model) {
        TenantEntity tenant = tenantService.getTenantById(tenantId);
        model.addAttribute("tenant", tenant);
        model.addAttribute("apiKeys", apiKeyService.listKeysForTenant(tenantId));
        return "admin/tenants/detail";
    }

    @PostMapping("/{tenantId}/suspend")
    public String suspend(@PathVariable String tenantId, @RequestParam(defaultValue = "") String reason,
                          RedirectAttributes redirectAttributes) {
        tenantService.suspendTenant(tenantId, reason);
        redirectAttributes.addFlashAttribute("message", "Tenant suspended.");
        return "redirect:/admin/tenants/" + tenantId;
    }

    @PostMapping("/{tenantId}/activate")
    public String activate(@PathVariable String tenantId, RedirectAttributes redirectAttributes) {
        tenantService.activateTenant(tenantId);
        redirectAttributes.addFlashAttribute("message", "Tenant activated.");
        return "redirect:/admin/tenants/" + tenantId;
    }

    @GetMapping("/{tenantId}/api-keys/new")
    public String newApiKeyForm(@PathVariable String tenantId, Model model) {
        TenantEntity tenant = tenantService.getTenantById(tenantId);
        model.addAttribute("tenant", tenant);
        model.addAttribute("name", "");
        return "admin/tenants/api-key-new";
    }

    @PostMapping("/{tenantId}/api-keys")
    public String createApiKey(@PathVariable String tenantId, @RequestParam(defaultValue = "API key") String name,
                               RedirectAttributes redirectAttributes) {
        String rawKey = apiKeyService.createApiKey(tenantId, name != null ? name.trim() : "API key");
        redirectAttributes.addFlashAttribute("newApiKey", rawKey);
        redirectAttributes.addFlashAttribute("message", "API key created. Copy it now; it won’t be shown again.");
        return "redirect:/admin/tenants/" + tenantId + "/api-keys/new";
    }

    @PostMapping("/{tenantId}/api-keys/{id}/suspend")
    public String suspendApiKey(@PathVariable String tenantId, @PathVariable Long id,
                                @RequestParam(required = false) String reason,
                                RedirectAttributes redirectAttributes) {
        try {
            apiKeyService.suspendKey(tenantId, id, reason);
            redirectAttributes.addFlashAttribute("message", "API key suspended.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "API key not found or access denied.");
        }
        return "redirect:/admin/tenants/" + tenantId;
    }

    @PostMapping("/{tenantId}/api-keys/{id}/unsuspend")
    public String unsuspendApiKey(@PathVariable String tenantId, @PathVariable Long id,
                                  RedirectAttributes redirectAttributes) {
        try {
            apiKeyService.unsuspendKey(tenantId, id);
            redirectAttributes.addFlashAttribute("message", "API key reactivated.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "API key not found or access denied.");
        }
        return "redirect:/admin/tenants/" + tenantId;
    }
}
