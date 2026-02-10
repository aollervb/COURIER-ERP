package com.menval.couriererp.admin.dto;

import com.menval.couriererp.tenant.entities.SubscriptionPlan;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class OnboardTenantForm {

    // Tenant
    @NotBlank(message = "Tenant ID is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Tenant ID: lowercase letters, numbers, hyphens only")
    @Size(min = 2, max = 64)
    private String tenantId;

    @NotBlank(message = "Company name is required")
    @Size(max = 200)
    private String companyName;

    @Size(max = 100)
    private String domain;

    @NotBlank(message = "Primary contact name is required")
    @Size(max = 200)
    private String primaryContactName;

    @NotBlank(message = "Primary contact email is required")
    @Email
    @Size(max = 320)
    private String primaryContactEmail;

    @Pattern(regexp = "^\\+?[0-9\\-\\s()]{7,20}$", message = "Invalid phone format")
    private String primaryContactPhone;

    @NotNull(message = "Plan is required")
    private SubscriptionPlan plan;

    @Min(1) @Max(36)
    private Integer subscriptionMonths = 12;

    @Pattern(regexp = "^[A-Z]{2,4}$", message = "Code prefix: 2–4 uppercase letters")
    private String accountCodePrefix;

    // First admin user for this tenant
    @NotBlank(message = "Admin email is required")
    @Email
    @Size(max = 320)
    private String adminEmail;

    @NotBlank(message = "Admin first name is required")
    @Size(max = 120)
    private String adminFirstName;

    @NotBlank(message = "Admin last name is required")
    @Size(max = 120)
    private String adminLastName;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String adminPassword;

    @NotBlank(message = "Confirm password")
    private String adminConfirmPassword;
}
