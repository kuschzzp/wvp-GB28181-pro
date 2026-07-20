package com.genersoft.iot.vmp.auth.service.impl;

import com.genersoft.iot.vmp.auth.repository.PermissionCache;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.storager.dao.UserMapper;
import com.genersoft.iot.vmp.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class TokenVersionServiceImpl implements TokenVersionService {

    private final UserMapper userMapper;
    private final PermissionCache permissionCache;

    public TokenVersionServiceImpl(UserMapper userMapper, PermissionCache permissionCache) {
        this.userMapper = userMapper;
        this.permissionCache = permissionCache;
    }

    @Override
    @Transactional
    public void bumpUser(long userId, String reason) {
        bumpUsers(List.of(userId), reason);
    }

    @Override
    @Transactional
    public void bumpUsers(Collection<Long> userIds, String reason) {
        Set<Long> normalizedIds = normalize(userIds);
        if (normalizedIds.isEmpty()) {
            return;
        }
        int changed = userMapper.bumpTokenVersions(normalizedIds, DateUtil.getNow());
        if (changed <= 0) {
            throw new IllegalStateException("没有可递增认证版本的有效用户");
        }
        evictAfterCommit(normalizedIds);
        log.info("已递增 {} 个用户的 tokenVersion，原因：{}", changed, normalizeReason(reason));
    }

    @Override
    @Transactional
    public void bumpUsersByRoleId(long roleId, String reason) {
        bumpUsers(userMapper.findUserIdsByRoleId(roleId), reason);
    }

    @Override
    public boolean matches(long userId, long tokenVersion) {
        Long currentVersion = userMapper.findActiveTokenVersion(userId);
        return currentVersion != null && currentVersion == tokenVersion;
    }

    private Set<Long> normalize(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null && userId > 0) {
                result.add(userId);
            }
        }
        return Set.copyOf(result);
    }

    private void evictAfterCommit(Set<Long> userIds) {
        Runnable eviction = () -> userIds.forEach(permissionCache::evictUser);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eviction.run();
                }
            });
        } else {
            eviction.run();
        }
    }

    private String normalizeReason(String reason) {
        return Objects.requireNonNullElse(reason, "UNSPECIFIED");
    }
}
