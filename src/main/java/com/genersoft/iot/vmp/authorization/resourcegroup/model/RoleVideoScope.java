package com.genersoft.iot.vmp.authorization.resourcegroup.model;

public record RoleVideoScope(
        long roleId,
        String videoScopeType,
        int status,
        int systemRole) {

    public boolean isSystemRole() {
        return systemRole == 1;
    }
}
