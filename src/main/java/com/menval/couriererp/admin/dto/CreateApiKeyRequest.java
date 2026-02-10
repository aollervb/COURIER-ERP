package com.menval.couriererp.admin.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional body for creating an API key. Name is optional.
 */
public record CreateApiKeyRequest(
        @Size(max = 120)
        String name
) {}
