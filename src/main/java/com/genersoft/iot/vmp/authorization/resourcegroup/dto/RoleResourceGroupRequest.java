package com.genersoft.iot.vmp.authorization.resourcegroup.dto;

import com.genersoft.iot.vmp.authorization.video.model.VideoScopeType;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record RoleResourceGroupRequest(
        @NotNull VideoScopeType videoScopeType,
        @NotNull Set<Long> resourceGroupIds) {

    public RoleResourceGroupRequest {
        resourceGroupIds = resourceGroupIds == null ? Set.of() : Set.copyOf(resourceGroupIds);
    }
}
