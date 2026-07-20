package com.genersoft.iot.vmp.auth.apikey.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record ApiKeyScopeUpdateRequest(
        @Size(max = 256) Set<Long> permissionMenuIds,
        @Size(max = 256) Set<Long> resourceGroupIds,
        @Size(max = 64) List<@Size(max = 128) String> ipAllowlist) {

    public ApiKeyScopeCommand toCommand() {
        return new ApiKeyScopeCommand(permissionMenuIds, resourceGroupIds, ipAllowlist);
    }
}
