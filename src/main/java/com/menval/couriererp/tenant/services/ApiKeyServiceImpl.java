package com.menval.couriererp.tenant.services;

import com.menval.couriererp.tenant.dto.ApiKeySummary;
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
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
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
                .filter(key -> !key.isSuspended())
                .map(ApiKeyEntity::getTenantId)
                .flatMap(tenantRepository::findByTenantId)
                .filter(TenantEntity::isActive)
                .filter(t -> !t.isExpired())
                .filter(TenantEntity::canAccess)
                .map(TenantEntity::getTenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKeySummary> listKeysForTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return List.of();
        }
        return apiKeyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional
    public void suspendKey(String tenantId, Long keyId, String reason) {
        ApiKeyEntity key = getKeyForTenant(tenantId, keyId);
        key.setSuspended(true);
        key.setSuspendedAt(Instant.now());
        key.setSuspensionReason(reason != null ? reason.trim() : null);
        apiKeyRepository.save(key);
    }

    @Override
    @Transactional
    public void unsuspendKey(String tenantId, Long keyId) {
        ApiKeyEntity key = getKeyForTenant(tenantId, keyId);
        key.setSuspended(false);
        key.setSuspendedAt(null);
        key.setSuspensionReason(null);
        apiKeyRepository.save(key);
    }

    private ApiKeyEntity getKeyForTenant(String tenantId, Long keyId) {
        ApiKeyEntity key = apiKeyRepository.findById(keyId).orElse(null);
        if (key == null || !tenantId.equals(key.getTenantId())) {
            throw new IllegalArgumentException("API key not found or access denied");
        }
        return key;
    }

    private ApiKeySummary toSummary(ApiKeyEntity e) {
        return new ApiKeySummary(
                e.getId(),
                e.getName(),
                e.getCreatedAt(),
                e.isSuspended(),
                e.getSuspendedAt(),
                e.getSuspensionReason()
        );
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
