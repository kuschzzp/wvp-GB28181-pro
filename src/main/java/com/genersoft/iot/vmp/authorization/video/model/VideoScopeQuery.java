package com.genersoft.iot.vmp.authorization.video.model;

import java.util.Objects;
import java.util.Set;

/**
 * Mapper-facing snapshot of a permission-coupled video scope.
 * Resource IDs stay out of authentication tokens and are resolved per request.
 */
public record VideoScopeQuery(
        boolean all,
        Set<Long> grantingRoleIds,
        Set<VideoResourceRef> sourceResources,
        Set<Long> explicitChannelIds,
        String permissionCode,
        long principalId,
        boolean systemUser) {

    public VideoScopeQuery(
            boolean all,
            Set<Long> grantingRoleIds,
            Set<VideoResourceRef> sourceResources,
            Set<Long> explicitChannelIds,
            String permissionCode,
            long principalId) {
        this(
                all,
                grantingRoleIds,
                sourceResources,
                explicitChannelIds,
                permissionCode,
                principalId,
                false);
    }

    public VideoScopeQuery {
        VideoScope normalized = new VideoScope(
                all, grantingRoleIds, sourceResources, explicitChannelIds);
        all = normalized.all();
        grantingRoleIds = normalized.grantingRoleIds();
        sourceResources = normalized.sourceResources();
        explicitChannelIds = normalized.explicitChannelIds();
        Objects.requireNonNull(permissionCode, "permissionCode must not be null");
        if (permissionCode.isBlank()) {
            throw new IllegalArgumentException("permissionCode must not be blank");
        }
        permissionCode = permissionCode.trim();
        if (principalId <= 0) {
            throw new IllegalArgumentException("principalId must be positive");
        }
    }

    public boolean isNone() {
        return !all && sourceResources.isEmpty() && explicitChannelIds.isEmpty();
    }

    public VideoScope toScope() {
        return new VideoScope(all, grantingRoleIds, sourceResources, explicitChannelIds);
    }
}
