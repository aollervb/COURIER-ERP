package com.menval.couriererp.modules.courier.packages.controllers.api;

import com.menval.couriererp.modules.courier.account.entities.AccountEntity;
import com.menval.couriererp.modules.courier.account.services.AccountService;
import com.menval.couriererp.modules.courier.packages.dto.AccountSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API endpoints used by the package list (assign modal). Kept separate from MVC controllers.
 */
@RestController
@RequestMapping("/packages/accounts")
@RequiredArgsConstructor
public class AccountSearchController {

    private final AccountService accountService;

    @GetMapping("/search")
    public List<AccountSearchItem> search(@RequestParam(name = "q", required = false) String q,
                                          @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<AccountEntity> accounts = accountService.search(q, true, PageRequest.of(0, size));
        return accounts.getContent().stream()
                .map(a -> new AccountSearchItem(a.getCode(), a.getDisplayName()))
                .toList();
    }
}
