package com.genersoft.iot.vmp.auth.apikey.dto;

public record ApiKeyPermissionOption(
        long id,
        long parentId,
        String menuName,
        String menuType,
        String permissionCode,
        int orderNum,
        boolean selectable) {
}
