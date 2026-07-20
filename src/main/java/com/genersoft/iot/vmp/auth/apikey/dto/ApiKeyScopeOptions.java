package com.genersoft.iot.vmp.auth.apikey.dto;

import com.genersoft.iot.vmp.authorization.resourcegroup.dto.ResourceGroupTreeNode;

import java.util.List;

public record ApiKeyScopeOptions(
        List<ApiKeyPermissionOption> permissionMenus,
        List<ResourceGroupTreeNode> resourceGroups) {

    public ApiKeyScopeOptions {
        permissionMenus = permissionMenus == null ? List.of() : List.copyOf(permissionMenus);
        resourceGroups = resourceGroups == null ? List.of() : List.copyOf(resourceGroups);
    }
}
