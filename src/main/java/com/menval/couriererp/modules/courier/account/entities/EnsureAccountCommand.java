package com.menval.couriererp.modules.courier.account.entities;

public record EnsureAccountCommand(
        String externalRef,
        String displayName,
        String email,
        String phone,
        String requestedCode // nullable for normal portal signups
) {}
