package com.menval.couriererp.modules.courier.account.api.controller;
import com.menval.couriererp.modules.courier.account.api.dto.EnsureAccountRequest;
import com.menval.couriererp.modules.courier.account.api.dto.EnsureAccountResponse;
import com.menval.couriererp.modules.courier.account.entities.AccountEntity;
import com.menval.couriererp.modules.courier.account.entities.EnsureAccountCommand;
import com.menval.couriererp.modules.courier.account.services.AccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integration/accounts")
public class AccountIntegrationController {

    private final com.menval.couriererp.modules.courier.account.services.AccountService accountService;

    public AccountIntegrationController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Idempotent: same externalRef will always return the same Account for this tenant.
     */
    @PostMapping("/ensure")
    public EnsureAccountResponse ensure(@Valid @RequestBody EnsureAccountRequest req) {
        AccountEntity a = accountService.ensureAccount(new EnsureAccountCommand(
                req.externalRef(),
                req.displayName(),
                req.email(),
                req.phone(),
                req.requestedCode()
        ));
        return toResponse(a);
    }

    private static EnsureAccountResponse toResponse(AccountEntity a) {
        return new EnsureAccountResponse(
                a.getId(),
                a.getPublicId(),
                a.getCode(),
                a.getDisplayName(),
                a.isActive()
        );
    }
}
