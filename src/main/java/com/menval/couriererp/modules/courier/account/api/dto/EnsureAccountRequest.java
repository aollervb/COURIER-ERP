package com.menval.couriererp.modules.courier.account.api.dto;

import jakarta.validation.constraints.NotBlank;

public record EnsureAccountRequest(
        @NotBlank String externalRef,
        @NotBlank String displayName,
        String email,
        String phone,
        String requestedCode // optional for migration/import
) {}
