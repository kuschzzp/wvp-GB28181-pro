package com.genersoft.iot.vmp.auth.apikey.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record CreateApiKeyCommand(
        String app,
        String remark,
        Instant expiresAt,
        boolean enabled,
        Set<Long> permissionMenuIds,
        Set<Long> resourceGroupIds,
        List<String> ipAllowlist) {

    public CreateApiKeyCommand {
        permissionMenuIds = permissionMenuIds == null ? Set.of() : Set.copyOf(permissionMenuIds);
        resourceGroupIds = resourceGroupIds == null ? Set.of() : Set.copyOf(resourceGroupIds);
        ipAllowlist = ipAllowlist == null ? List.of() : List.copyOf(ipAllowlist);
    }

    public CreateApiKeyCommand(
            String app,
            String remark,
            Instant expiresAt,
            boolean enabled) {
        this(app, remark, expiresAt, enabled, Set.of(), Set.of(), List.of());
    }

    public ApiKeyScopeCommand scope() {
        return new ApiKeyScopeCommand(permissionMenuIds, resourceGroupIds, ipAllowlist);
    }
}
