package com.menval.couriererp.modules.courier.account.api.dto;

public record AccountResponse(
        Long id,
        String publicId,
        String code,
        String displayName,
        String email,
        String phone,
        boolean active
) {}
