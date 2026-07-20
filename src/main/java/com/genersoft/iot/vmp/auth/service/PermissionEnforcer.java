package com.genersoft.iot.vmp.auth.service;

public interface PermissionEnforcer {

    boolean check(String permissionCode);

    boolean require(String permissionCode);
}
