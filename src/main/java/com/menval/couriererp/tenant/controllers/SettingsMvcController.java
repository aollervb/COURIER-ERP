package com.menval.couriererp.tenant.controllers;

import com.menval.couriererp.tenant.TenantContext;
import com.menval.couriererp.tenant.services.ApiKeyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ERP view for tenant users (DIRECTOR, ADMIN) to manage settings, e.g. create API keys for their tenant.
 * Session auth only; not a REST API.
 * Uses {@link TenantContext} for current tenant (set by {@link com.menval.couriererp.tenant.TenantAccessFilter} from the authenticated user).
 */
@Controller
@RequestMapping("/settings")
@PreAuthorize("hasAnyRole('DIRECTOR', 'ADMIN')")
public class SettingsMvcController {

    private final ApiKeyService apiKeyService;

    public SettingsMvcController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping("/api-keys")
    public String apiKeysPage(Model model) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return "redirect:/?error=no-tenant";
        }
        model.addAttribute("tenantId", tenantId);
        model.addAttribute("apiKeys", apiKeyService.listKeysForTenant(tenantId));
        return "settings/api-keys";
    }

    @PostMapping("/api-keys")
    public String createApiKey(@RequestParam(defaultValue = "API key") String name,
                               RedirectAttributes redirectAttributes) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "No tenant assigned.");
            return "redirect:/settings/api-keys";
        }
        String rawKey = apiKeyService.createApiKey(tenantId, name != null ? name.trim() : "API key");
        redirectAttributes.addFlashAttribute("newApiKey", rawKey);
        redirectAttributes.addFlashAttribute("message", "API key created. Copy it now; it won’t be shown again.");
        return "redirect:/settings/api-keys";
    }

    @PostMapping("/api-keys/{id}/suspend")
    public String suspendApiKey(@PathVariable Long id,
                                @RequestParam(required = false) String reason,
                                RedirectAttributes redirectAttributes) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "No tenant assigned.");
            return "redirect:/settings/api-keys";
        }
        try {
            apiKeyService.suspendKey(tenantId, id, reason);
            redirectAttributes.addFlashAttribute("message", "API key suspended.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "API key not found or access denied.");
        }
        return "redirect:/settings/api-keys";
    }

    @PostMapping("/api-keys/{id}/unsuspend")
    public String unsuspendApiKey(@PathVariable Long id,
                                  RedirectAttributes redirectAttributes) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "No tenant assigned.");
            return "redirect:/settings/api-keys";
        }
        try {
            apiKeyService.unsuspendKey(tenantId, id);
            redirectAttributes.addFlashAttribute("message", "API key reactivated.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "API key not found or access denied.");
        }
        return "redirect:/settings/api-keys";
    }
}
