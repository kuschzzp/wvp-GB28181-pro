package com.genersoft.iot.vmp.auth.service.impl;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.repository.PermissionCache;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.service.PermissionService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository repository;
    private final PermissionCache cache;
    private final AuthorizationProperties properties;

    public PermissionServiceImpl(
            PermissionRepository repository,
            PermissionCache cache,
            AuthorizationProperties properties) {
        this.repository = repository;
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public Set<String> resolvePermissions(long userId, long tokenVersion) {
        return cache.get(userId, tokenVersion).orElseGet(() -> loadPermissions(userId, tokenVersion));
    }

    @Override
    public Set<Long> findGrantingRoleIds(long userId, String permissionCode) {
        requirePermissionCode(permissionCode);
        List<Long> roleIds = repository.findGrantingRoleIds(userId, permissionCode);
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long roleId : roleIds) {
            if (roleId != null) {
                distinct.add(roleId);
            }
        }
        return Set.copyOf(distinct);
    }

    @Override
    public boolean hasPermission(AuthPrincipal principal, String permissionCode) {
        Objects.requireNonNull(principal, "principal must not be null");
        requirePermissionCode(permissionCode);
        return principal.permissions().contains(permissionCode);
    }

    private Set<String> loadPermissions(long userId, long tokenVersion) {
        List<String> codes = repository.isSystemUser(userId)
                ? repository.findAllEnabledPermissionCodes()
                : repository.findPermissionCodes(userId);
        Set<String> permissions = normalize(codes);
        cache.put(userId, tokenVersion, permissions, properties.principalCacheTtl());
        return permissions;
    }

    private Set<String> normalize(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        TreeSet<String> permissions = new TreeSet<>();
        for (String code : codes) {
            if (code != null && !code.isBlank()) {
                permissions.add(code.trim());
            }
        }
        return Collections.unmodifiableSet(permissions);
    }

    private void requirePermissionCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            throw new IllegalArgumentException("permissionCode must not be blank");
        }
    }
}
