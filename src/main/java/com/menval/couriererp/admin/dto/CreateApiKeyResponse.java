package com.menval.couriererp.admin.dto;

/**
 * Response when creating an API key. The raw key is returned only once.
 */
public record CreateApiKeyResponse(String apiKey) {}
