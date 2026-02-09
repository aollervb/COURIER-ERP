package com.menval.couriererp.modules.courier.account.entities;

public record CreateAccountCommand(
        String displayName,
        String email,
        String phone,
        String code,        // required for manual/migration create
        String externalRef  // optional
) {}

