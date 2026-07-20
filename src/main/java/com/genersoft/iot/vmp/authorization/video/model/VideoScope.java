package com.genersoft.iot.vmp.authorization.video.model;

import java.util.Objects;
import java.util.Set;

public record VideoScope(
        boolean all,
        Set<Long> grantingRoleIds,
        Set<VideoResourceRef> sourceResources,
        Set<Long> explicitChannelIds) {

    public VideoScope {
        if (all) {
            grantingRoleIds = Set.of();
            sourceResources = Set.of();
            explicitChannelIds = Set.of();
        } else {
            grantingRoleIds = immutablePositiveIds(grantingRoleIds, "grantingRoleIds");
            sourceResources = immutableSourceResources(sourceResources);
            explicitChannelIds = immutablePositiveIds(
                    explicitChannelIds, "explicitChannelIds");
        }
    }

    public static VideoScope unrestricted() {
        return new VideoScope(true, Set.of(), Set.of(), Set.of());
    }

    public static VideoScope none() {
        return new VideoScope(false, Set.of(), Set.of(), Set.of());
    }

    public boolean isNone() {
        return !all && sourceResources.isEmpty() && explicitChannelIds.isEmpty();
    }

    public VideoScopeQuery toQuery(String permissionCode, long principalId) {
        return toQuery(permissionCode, principalId, false);
    }

    public VideoScopeQuery toQuery(
            String permissionCode,
            long principalId,
            boolean systemUser) {
        return new VideoScopeQuery(
                all,
                grantingRoleIds,
                sourceResources,
                explicitChannelIds,
                permissionCode,
                principalId,
                systemUser);
    }

    private static Set<Long> immutablePositiveIds(Set<Long> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        if (values.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException(fieldName + " must contain only positive IDs");
        }
        return Set.copyOf(values);
    }

    private static Set<VideoResourceRef> immutableSourceResources(
            Set<VideoResourceRef> resources) {
        Objects.requireNonNull(resources, "sourceResources must not be null");
        for (VideoResourceRef resource : resources) {
            Objects.requireNonNull(resource, "sourceResources must not contain null");
            if (!resource.type().isBindable() || resource.type() == VideoResourceType.CHANNEL) {
                throw new IllegalArgumentException(
                        "sourceResources 只能包含非统一通道的可授权资源");
            }
        }
        return Set.copyOf(resources);
    }
}
