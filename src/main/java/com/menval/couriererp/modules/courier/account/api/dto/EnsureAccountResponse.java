package com.menval.couriererp.modules.courier.account.api.dto;

public record EnsureAccountResponse(
        Long id,
        String publicId,
        String code,
        String displayName,
        boolean active
) {}
