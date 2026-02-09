package com.menval.couriererp.modules.courier.account.services;

import org.springframework.stereotype.Service;

@Service
public class AccountCounterService {
    public boolean supports(String prefix) {
        return false;
    }

    public String nextCode(String prefix, int width) {
        throw new UnsupportedOperationException("Counter-based codes not enabled");
    }

    public void syncFromAccounts(String prefix) {
        // no-op for now
    }
}
