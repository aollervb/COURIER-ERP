package com.menval.couriererp.tenant.controllers;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.tenant.services.ApiKeyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ERP view for tenant users (DIRECTOR, ADMIN) to manage settings, e.g. create API keys for their tenant.
 * Session auth only; not a REST API.
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
    public String apiKeysPage(@AuthenticationPrincipal BaseUser user, Model model) {
        String tenantId = user != null ? user.getUserTenantId() : null;
        if (tenantId == null || tenantId.isBlank()) {
            return "redirect:/?error=no-tenant";
        }
        model.addAttribute("tenantId", tenantId);
        return "settings/api-keys";
    }

    @PostMapping("/api-keys")
    public String createApiKey(@AuthenticationPrincipal BaseUser user,
                               @RequestParam(defaultValue = "API key") String name,
                               RedirectAttributes redirectAttributes) {
        String tenantId = user != null ? user.getUserTenantId() : null;
        if (tenantId == null || tenantId.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "No tenant assigned.");
            return "redirect:/settings/api-keys";
        }
        String rawKey = apiKeyService.createApiKey(tenantId, name != null ? name.trim() : "API key");
        redirectAttributes.addFlashAttribute("newApiKey", rawKey);
        redirectAttributes.addFlashAttribute("message", "API key created. Copy it now; it won’t be shown again.");
        return "redirect:/settings/api-keys";
    }
}
