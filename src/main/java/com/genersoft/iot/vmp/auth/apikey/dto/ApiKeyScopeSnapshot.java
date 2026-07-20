package com.genersoft.iot.vmp.auth.apikey.dto;

import java.util.List;
import java.util.Set;

public record ApiKeyScopeSnapshot(
        Set<Long> permissionMenuIds,
        Set<Long> resourceGroupIds,
        List<String> ipAllowlist) {

    public ApiKeyScopeSnapshot {
        permissionMenuIds = permissionMenuIds == null ? Set.of() : Set.copyOf(permissionMenuIds);
        resourceGroupIds = resourceGroupIds == null ? Set.of() : Set.copyOf(resourceGroupIds);
        ipAllowlist = ipAllowlist == null ? List.of() : List.copyOf(ipAllowlist);
    }
}
