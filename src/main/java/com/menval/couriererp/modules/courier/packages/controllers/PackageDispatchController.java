package com.menval.couriererp.modules.courier.packages.controllers;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.modules.courier.packages.entities.PackageEntity;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * List packages ready for dispatch (RECEIVED_FINAL, OUT_FOR_DELIVERY) and mark out for delivery / delivered.
 */
@Controller
@RequestMapping("/packages/dispatch")
@RequiredArgsConstructor
public class PackageDispatchController {

    private final PackageService packageService;

    @GetMapping
    public String dispatchList(Model model,
                               @RequestParam(name = "page", defaultValue = "0") int page,
                               @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<PackageEntity> packages = packageService.findReadyForDispatch(PageRequest.of(page, size));
        model.addAttribute("packages", packages);
        return "packages/dispatch";
    }

    @PostMapping("/{id}/out-for-delivery")
    public String markOutForDelivery(@PathVariable Long id,
                                     @org.springframework.security.core.annotation.AuthenticationPrincipal BaseUser user,
                                     RedirectAttributes redirectAttributes) {
        try {
            packageService.markOutForDelivery(id, user);
            redirectAttributes.addFlashAttribute("message", "Package marked out for delivery.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/packages/dispatch";
    }

    @PostMapping("/{id}/delivered")
    public String markDelivered(@PathVariable Long id,
                               @org.springframework.security.core.annotation.AuthenticationPrincipal BaseUser user,
                               RedirectAttributes redirectAttributes) {
        try {
            packageService.markDelivered(id, user);
            redirectAttributes.addFlashAttribute("message", "Package marked delivered.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/packages/dispatch";
    }
}
