package com.menval.couriererp.modules.courier.account.services;

import com.menval.couriererp.modules.courier.account.components.AccountCodeGenerator;
import com.menval.couriererp.modules.courier.account.components.AccountCodePolicy;
import com.menval.couriererp.modules.courier.account.entities.AccountEntity;
import com.menval.couriererp.modules.courier.account.entities.EnsureAccountCommand;
import com.menval.couriererp.modules.courier.account.repositories.AccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepo;
    private final AccountCodePolicy codePolicy;
    private final AccountCodeGenerator codeGen;
    private final AccountCounterService counterService; // optional; can be noop for now

    public AccountServiceImpl(AccountRepository accountRepo,
                              AccountCodePolicy codePolicy,
                              AccountCodeGenerator codeGen,
                              AccountCounterService counterService) {
        this.accountRepo = accountRepo;
        this.codePolicy = codePolicy;
        this.codeGen = codeGen;
        this.counterService = counterService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountEntity> search(String q, Boolean active, Pageable pageable) {

        boolean hasQuery = q != null && !q.isBlank();

        // q blank and active == null → findAll(pageable)
        if (!hasQuery && active == null) {
            return accountRepo.findAll(pageable);
        }

        // q blank and active != null → findByActive(active, pageable)
        if (!hasQuery) {
            return accountRepo.findByActive(active, pageable);
        }

        String query = q.trim();
        Long id = parseLongOrNull(query);

        // q present (active can be null or not) → unified search query
        return accountRepo.search(query, id, active, pageable);
    }

    private static Long parseLongOrNull(String s) {
        try {
            // only treat it as an ID if it's purely digits (avoid "JP-123")
            if (!s.matches("^\\d+$")) return null;
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional
    public AccountEntity ensureAccount(EnsureAccountCommand cmd) {
        if (cmd.externalRef() == null || cmd.externalRef().isBlank()) {
            throw new IllegalArgumentException("externalRef is required for ensureAccount");
        }

        return accountRepo.findByExternalRef(cmd.externalRef())
                .orElseGet(() -> createNewEnsured(cmd));
    }

    private AccountEntity createNewEnsured(EnsureAccountCommand cmd) {
        AccountEntity account = new AccountEntity();
        account.setPublicId(UUID.randomUUID().toString());
        account.setExternalRef(cmd.externalRef().trim());
        account.setDisplayName(required(cmd.displayName(), "displayName"));
        account.setEmail(blankToNull(cmd.email()));
        account.setPhone(blankToNull(cmd.phone()));
        account.setActive(true);

        // Migration-friendly: if requestedCode is provided, use it.
        String requested = codePolicy.normalize(cmd.requestedCode());
        if (requested != null) {
            codePolicy.validate(requested);
            account.setCode(requested);
            try {
                return accountRepo.save(account);
            } catch (DataIntegrityViolationException e) {
                // Could be: code already used OR concurrent create with same externalRef.
                // If externalRef already exists now, return that (idempotent).
                return accountRepo.findByExternalRef(cmd.externalRef().trim()).orElseThrow(() -> e);
            }
        }

        // Otherwise generate. Prefer sequential counter if you have it, else random.
        for (int i = 0; i < 10; i++) {
            account.setCode(generateCode());
            try {
                return accountRepo.save(account);
            } catch (DataIntegrityViolationException e) {
                // if concurrent create with same externalRef, return it
                var existing = accountRepo.findByExternalRef(cmd.externalRef().trim());
                if (existing.isPresent()) return existing.get();
                // else assume code collision and retry
            }
        }
        throw new IllegalStateException("Could not allocate a unique account code after retries");
    }

    @Override
    @Transactional(readOnly = true)
    public AccountEntity getById(Long id) {
        return accountRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountEntity getByPublicId(String publicId) {
        return accountRepo.findByPublicId(publicId).orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountEntity getByCode(String code) {
        String normalized = codePolicy.normalize(code);
        if (normalized == null) throw new IllegalArgumentException("Invalid code");
        return accountRepo.findByCode(normalized).orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountEntity getByExternalRef(String externalRef) {
        String ref = externalRef == null ? null : externalRef.trim();
        if (ref == null || ref.isBlank()) throw new IllegalArgumentException("externalRef is required");
        return accountRepo.findByExternalRef(ref).orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    @Override
    @Transactional
    public void deactivateAccount(Long accountId) {
        AccountEntity a = getById(accountId);
        a.setActive(false);
        accountRepo.save(a);
    }

    @Override
    @Transactional
    public void activateAccount(Long accountId) {
        AccountEntity a = getById(accountId);
        a.setActive(true);
        accountRepo.save(a);
    }

    @Override
    @Transactional
    public void syncAccountCodeCounters(String prefix) {
        counterService.syncFromAccounts(prefix);
    }

    private String generateCode() {
        // If you have a counter table, use it; else random.
        // Example: tenant-specific prefix could come from TenantConfig later.
        String prefix = "CR";
        return counterService.supports(prefix)
                ? counterService.nextCode(prefix, 6)  // e.g., CR-000123
                : codeGen.generate();           // e.g., CR-7K2P9D
    }

    private static String required(String v, String field) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(field + " is required");
        return v.trim();
    }

    private static String blankToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isBlank() ? null : t;
    }
}
