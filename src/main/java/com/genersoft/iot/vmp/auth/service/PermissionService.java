package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;

import java.util.Set;

public interface PermissionService {

    Set<String> resolvePermissions(long userId, long tokenVersion);

    Set<Long> findGrantingRoleIds(long userId, String permissionCode);

    boolean hasPermission(AuthPrincipal principal, String permissionCode);
}
