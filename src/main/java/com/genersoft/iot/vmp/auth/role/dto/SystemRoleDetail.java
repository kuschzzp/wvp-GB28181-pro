package com.genersoft.iot.vmp.auth.role.dto;

import com.genersoft.iot.vmp.storager.dao.dto.Role;

import java.util.Set;

public record SystemRoleDetail(
        Role role,
        Set<Long> menuIds,
        Set<Long> deptIds,
        Set<Long> resourceGroupIds) {

    public SystemRoleDetail {
        menuIds = menuIds == null ? Set.of() : Set.copyOf(menuIds);
        deptIds = deptIds == null ? Set.of() : Set.copyOf(deptIds);
        resourceGroupIds = resourceGroupIds == null ? Set.of() : Set.copyOf(resourceGroupIds);
    }
}
