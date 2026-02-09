package com.menval.couriererp.modules.courier.account.components;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class AccountCodePolicy {
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9-]{3,32}$");

    public String normalize(String raw) {
        if (raw == null) return null;
        String c = raw.trim().toUpperCase().replace(' ', '-');
        return c.isBlank() ? null : c;
    }

    public void validate(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Account code is blank");
        if (!CODE.matcher(code).matches()) throw new IllegalArgumentException("Account code has invalid characters");
    }
}


