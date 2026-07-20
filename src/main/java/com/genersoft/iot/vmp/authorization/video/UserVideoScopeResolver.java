package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.auth.service.PermissionService;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoRoleScopeGrant;
import com.genersoft.iot.vmp.authorization.video.model.VideoScope;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeResourceRow;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Component
public class UserVideoScopeResolver {

    private final PermissionService permissionService;
    private final VideoScopeMapper scopeMapper;

    public UserVideoScopeResolver(
            PermissionService permissionService,
            VideoScopeMapper scopeMapper) {
        this.permissionService = permissionService;
        this.scopeMapper = scopeMapper;
    }

    public VideoScope resolve(long userId, String permissionCode, boolean systemUser) {
        if (userId <= 0) {
            return VideoScope.none();
        }
        if (permissionCode == null || permissionCode.isBlank()) {
            throw new IllegalArgumentException("permissionCode must not be blank");
        }
        if (systemUser) {
            return VideoScope.unrestricted();
        }
        Set<Long> roleIds = normalizeIds(
                permissionService.findGrantingRoleIds(userId, permissionCode.trim()));
        if (roleIds.isEmpty()) {
            return VideoScope.none();
        }

        List<VideoRoleScopeGrant> grants = scopeMapper.findEnabledRoleScopes(roleIds);
        if (grants == null || grants.isEmpty()) {
            return VideoScope.none();
        }
        TreeSet<Long> customRoleIds = new TreeSet<>();
        for (VideoRoleScopeGrant grant : grants) {
            if (grant == null || !roleIds.contains(grant.roleId())) {
                continue;
            }
            switch (VideoScopeType.fromDatabaseValue(grant.scopeType())) {
                case ALL -> {
                    return VideoScope.unrestricted();
                }
                case CUSTOM -> customRoleIds.add(grant.roleId());
                case NONE -> {
                    // NONE deliberately contributes no resources.
                }
            }
        }
        if (customRoleIds.isEmpty()) {
            return VideoScope.none();
        }

        List<VideoScopeResourceRow> rows = scopeMapper.findEnabledResources(customRoleIds);
        LinkedHashSet<VideoResourceRef> sourceResources = new LinkedHashSet<>();
        LinkedHashSet<Long> explicitChannelIds = new LinkedHashSet<>();
        if (rows != null) {
            for (VideoScopeResourceRow row : rows) {
                addResource(row, sourceResources, explicitChannelIds);
            }
        }
        return new VideoScope(
                false,
                Set.copyOf(customRoleIds),
                sourceResources,
                explicitChannelIds);
    }

    private void addResource(
            VideoScopeResourceRow row,
            Set<VideoResourceRef> sourceResources,
            Set<Long> explicitChannelIds) {
        if (row == null || row.resourceType() == null || row.resourceId() <= 0) {
            throw new IllegalStateException("资源组包含非法视频资源");
        }
        VideoResourceType type = row.resourceType();
        if (!type.isBindable()) {
            throw new IllegalStateException("资源组包含不可直接授权的资源类型: " + type);
        }
        if (type == VideoResourceType.CHANNEL) {
            explicitChannelIds.add(row.resourceId());
        } else {
            sourceResources.add(new VideoResourceRef(type, row.resourceId()));
        }
    }

    private Set<Long> normalizeIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        TreeSet<Long> normalized = new TreeSet<>();
        ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .forEach(normalized::add);
        return Set.copyOf(normalized);
    }
}
