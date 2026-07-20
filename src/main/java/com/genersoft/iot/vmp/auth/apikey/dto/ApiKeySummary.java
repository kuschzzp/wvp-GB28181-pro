package com.genersoft.iot.vmp.auth.apikey.dto;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyStatus;
import com.genersoft.iot.vmp.storager.dao.dto.UserApiKey;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record ApiKeySummary(
        long id,
        long ownerId,
        String username,
        String app,
        String prefix,
        String last4,
        Instant expiresAt,
        String remark,
        ApiKeyStatus status,
        LocalDateTime lastUsedAt,
        String createTime,
        String updateTime,
        Set<Long> permissionMenuIds,
        Set<Long> resourceGroupIds,
        List<String> ipAllowlist) {

    public static ApiKeySummary from(UserApiKey key) {
        return from(key, new ApiKeyScopeSnapshot(Set.of(), Set.of(), List.of()));
    }

    public static ApiKeySummary from(UserApiKey key, ApiKeyScopeSnapshot scope) {
        return new ApiKeySummary(
                key.getId(),
                key.getUserId(),
                key.getUsername(),
                key.getApp(),
                key.getKeyPrefix(),
                key.getKeyLast4(),
                key.getExpiredAt() == null || key.getExpiredAt() <= 0
                        ? null
                        : Instant.ofEpochMilli(key.getExpiredAt()),
                key.getRemark(),
                key.getStatus(),
                key.getLastUsedAt(),
                key.getCreateTime(),
                key.getUpdateTime(),
                scope.permissionMenuIds(),
                scope.resourceGroupIds(),
                scope.ipAllowlist());
    }
}
