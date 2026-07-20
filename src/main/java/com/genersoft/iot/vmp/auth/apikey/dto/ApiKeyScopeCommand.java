package com.genersoft.iot.vmp.auth.apikey.dto;

import java.util.List;
import java.util.Set;

public record ApiKeyScopeCommand(
        Set<Long> permissionMenuIds,
        Set<Long> resourceGroupIds,
        List<String> ipAllowlist) {

    public ApiKeyScopeCommand {
        permissionMenuIds = permissionMenuIds == null ? Set.of() : Set.copyOf(permissionMenuIds);
        resourceGroupIds = resourceGroupIds == null ? Set.of() : Set.copyOf(resourceGroupIds);
        ipAllowlist = ipAllowlist == null ? List.of() : List.copyOf(ipAllowlist);
    }

    public static ApiKeyScopeCommand empty() {
        return new ApiKeyScopeCommand(Set.of(), Set.of(), List.of());
    }
}
