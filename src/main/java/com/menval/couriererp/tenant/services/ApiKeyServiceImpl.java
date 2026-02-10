package com.menval.couriererp.tenant.services;

import com.menval.couriererp.tenant.entities.ApiKeyEntity;
import com.menval.couriererp.tenant.entities.TenantEntity;
import com.menval.couriererp.tenant.repositories.ApiKeyRepository;
import com.menval.couriererp.tenant.repositories.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final String KEY_PREFIX = "ce_";
    private static final int KEY_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;

    @Override
    @Transactional
    public String createApiKey(String tenantId, String name) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
        if (name == null || name.isBlank()) {
            name = "API key";
        }
        if (apiKeyRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new IllegalArgumentException("An API key with name '" + name + "' already exists for this tenant");
        }
        String rawKey = KEY_PREFIX + generateSecureRandomHex(KEY_BYTES);
        String keyHash = hash(rawKey);
        apiKeyRepository.save(ApiKeyEntity.builder()
                .tenantId(tenantId)
                .keyHash(keyHash)
                .name(name.trim())
                .build());
        return rawKey;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> validateAndGetTenantId(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        String keyHash = hash(rawKey);
        return apiKeyRepository.findByKeyHash(keyHash)
                .map(ApiKeyEntity::getTenantId)
                .flatMap(tenantRepository::findByTenantId)
                .filter(TenantEntity::isActive)
                .filter(t -> !t.isExpired())
                .filter(TenantEntity::canAccess)
                .map(TenantEntity::getTenantId);
    }

    private static String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " not available", e);
        }
    }

    private static String generateSecureRandomHex(int numBytes) {
        byte[] bytes = new byte[numBytes];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
