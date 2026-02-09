package com.menval.couriererp.modules.courier.account.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAccountRequest(
        @NotBlank String displayName,
        String email,
        String phone
) {}
