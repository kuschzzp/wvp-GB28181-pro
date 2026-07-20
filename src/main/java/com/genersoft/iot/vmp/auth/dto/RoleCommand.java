package com.genersoft.iot.vmp.auth.dto;

public record RoleCommand(
        Long id,
        String name,
        String roleKey,
        int roleSort,
        String deptDataScope,
        String videoScopeType,
        int status,
        boolean systemRole) {
}
