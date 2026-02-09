package com.menval.couriererp.modules.courier.account.controllers;

import com.menval.couriererp.modules.courier.account.entities.AccountEntity;
import com.menval.couriererp.modules.courier.account.services.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/accounts")
public class AccountMVCController {

    private final AccountService accountService;

    /**
     * List accounts with optional search.
     * q can match code or display name depending on your service/repo implementation.
     */
    @GetMapping
    public String list(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size,
            Model model
    ) {
        Page<AccountEntity> accounts = accountService.search(q, active, PageRequest.of(page, size));

        model.addAttribute("accounts", accounts);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("active", active);
        return "accounts/list";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id,
                             @RequestHeader(value = "Referer", required = false) String referer) {
        accountService.deactivateAccount(id);
        return redirectBackOrList(referer);
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id,
                           @RequestHeader(value = "Referer", required = false) String referer) {
        accountService.activateAccount(id);
        return redirectBackOrList(referer);
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model) {
        model.addAttribute("account", accountService.getById(id));
        return "accounts/details";
    }

    private String redirectBackOrList(String referer) {
        if (referer != null && !referer.isBlank()) return "redirect:" + referer;
        return "redirect:/accounts";
    }
}
