package com.genersoft.iot.vmp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RoleRequest(
        Long id,
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String roleKey,
        int roleSort,
        @NotBlank String deptDataScope,
        @NotBlank String videoScopeType,
        int status,
        boolean systemRole,
        @NotNull Set<Long> menuIds,
        @NotNull Set<Long> deptIds,
        @NotNull Set<Long> resourceGroupIds) {

    public RoleRequest {
        menuIds = menuIds == null ? Set.of() : Set.copyOf(menuIds);
        deptIds = deptIds == null ? Set.of() : Set.copyOf(deptIds);
        resourceGroupIds = resourceGroupIds == null ? Set.of() : Set.copyOf(resourceGroupIds);
    }

    public RoleCommand toCommand() {
        return toCommand(id);
    }

    public RoleCommand toCommand(Long roleId) {
        return new RoleCommand(
                roleId, name, roleKey, roleSort, deptDataScope, videoScopeType, status, systemRole);
    }
}
