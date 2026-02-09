package com.menval.couriererp.modules.courier.account.services;

import com.menval.couriererp.modules.courier.account.entities.AccountEntity;
import com.menval.couriererp.modules.courier.account.entities.EnsureAccountCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccountService {
    /* ---------- Creation / Integration ---------- */

    /**
     * Ensures an account exists for the given external reference.
     * Idempotent: same externalRef always returns the same account.
     */
    AccountEntity ensureAccount(EnsureAccountCommand command);


    /* ---------- Lookup ---------- */
    Page<AccountEntity> search(String q, Boolean active, Pageable pageable);

    AccountEntity getById(Long id);

    AccountEntity getByPublicId(String publicId);

    AccountEntity getByCode(String code);

    AccountEntity getByExternalRef(String externalRef);


    /* ---------- Lifecycle ---------- */

    void deactivateAccount(Long accountId);

    void activateAccount(Long accountId);


    /* ---------- Migration / Admin ---------- */

    /**
     * Syncs account code counters based on existing data
     * (used after bulk migration).
     */
    void syncAccountCodeCounters(String prefix);
}
