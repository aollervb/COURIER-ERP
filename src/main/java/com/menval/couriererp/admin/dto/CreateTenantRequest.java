package com.menval.couriererp.admin.dto;

import com.menval.couriererp.tenant.entities.SubscriptionPlan;
import jakarta.validation.constraints.*;

public record CreateTenantRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9-]+$") @Size(min = 2, max = 64) String tenantId,
        @NotBlank @Size(max = 200) String companyName,
        @Size(max = 100) String domain,
        @NotBlank String primaryContactName,
        @NotBlank @Email String primaryContactEmail,
        @Pattern(regexp = "^\\+?[0-9\\-\\s()]{7,20}$") String primaryContactPhone,
        @NotNull SubscriptionPlan plan,
        @Min(1) @Max(36) Integer subscriptionMonths,
        @Pattern(regexp = "^[A-Z]{2,4}$") String accountCodePrefix
) {}
