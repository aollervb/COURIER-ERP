package com.menval.couriererp.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record SuspendRequest(@NotBlank String reason) {}
