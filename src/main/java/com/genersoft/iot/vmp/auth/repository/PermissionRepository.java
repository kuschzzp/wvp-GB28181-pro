package com.genersoft.iot.vmp.auth.repository;

import java.util.List;

public interface PermissionRepository {

    boolean isSystemUser(long userId);

    List<String> findPermissionCodes(long userId);

    List<String> findAllEnabledPermissionCodes();

    List<Long> findEnabledRoleIds(long userId);

    List<Long> findGrantingRoleIds(long userId, String permissionCode);
}
