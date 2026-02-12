package com.menval.couriererp.modules.courier.packages.controllers;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.modules.courier.account.entities.AccountEntity;
import com.menval.couriererp.modules.courier.account.services.AccountService;
import com.menval.couriererp.modules.courier.packages.dto.AccountSearchItem;
import com.menval.couriererp.modules.courier.packages.entities.PackageEntity;
import com.menval.couriererp.modules.courier.packages.entities.PackageStatus;
import com.menval.couriererp.modules.courier.packages.services.PackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * List all packages (with optional status filter) and assign package to account via modal (search + pick).
 */
@Controller
@RequestMapping("/packages")
@RequiredArgsConstructor
public class PackageListController {

    private final PackageService packageService;
    private final AccountService accountService;

    @GetMapping
    public String list(Model model,
                       @RequestParam(name = "status", required = false) PackageStatus statusFilter,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<PackageEntity> packages = packageService.listAll(PageRequest.of(page, size), statusFilter);
        model.addAttribute("packages", packages);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("allStatuses", PackageStatus.values());
        return "packages/list";
    }

    @GetMapping("/accounts/search")
    @ResponseBody
    public List<AccountSearchItem> searchAccounts(@RequestParam(name = "q", required = false) String q,
                                                  @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<AccountEntity> accounts = accountService.search(q, true, PageRequest.of(0, size));
        return accounts.getContent().stream()
                .map(a -> new AccountSearchItem(a.getCode(), a.getDisplayName()))
                .toList();
    }

    @PostMapping("/{packageId}/assign")
    public String assign(@PathVariable Long packageId,
                        @RequestParam("accountCode") String accountCode,
                        @RequestParam(name = "status", required = false) String statusFilterParam,
                        @AuthenticationPrincipal BaseUser user,
                        RedirectAttributes redirectAttributes) {
        if (accountCode == null || accountCode.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Please select an account.");
            return redirectToPackagesList(statusFilterParam);
        }
        try {
            packageService.assignPackageToAccount(packageId, accountCode.trim(), user);
            redirectAttributes.addFlashAttribute("message", "Package assigned to account " + accountCode + ".");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectToPackagesList(statusFilterParam);
    }

    private static String redirectToPackagesList(String statusFilterParam) {
        if (statusFilterParam != null && !statusFilterParam.isBlank()) {
            return "redirect:/packages?status=" + statusFilterParam;
        }
        return "redirect:/packages";
    }
}
