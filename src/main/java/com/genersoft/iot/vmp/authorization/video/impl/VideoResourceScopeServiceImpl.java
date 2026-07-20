package com.genersoft.iot.vmp.authorization.video.impl;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.apikey.ApiKeyScopeService;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.repository.AuthorizationAuditSink;
import com.genersoft.iot.vmp.auth.service.PermissionService;
import com.genersoft.iot.vmp.authorization.video.VideoResourceScopeService;
import com.genersoft.iot.vmp.authorization.video.VideoScopeMapper;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoRoleScopeGrant;
import com.genersoft.iot.vmp.authorization.video.model.VideoScope;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeResourceRow;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
@Slf4j
public class VideoResourceScopeServiceImpl implements VideoResourceScopeService {

    private final PermissionService permissionService;
    private final VideoScopeMapper scopeMapper;
    private final ApiKeyScopeService apiKeyScopeService;
    private final AuthorizationAuditSink auditSink;
    private final AuthorizationMode mode;

    public VideoResourceScopeServiceImpl(
            PermissionService permissionService,
            VideoScopeMapper scopeMapper,
            ApiKeyScopeService apiKeyScopeService,
            AuthorizationAuditSink auditSink,
            AuthorizationProperties properties) {
        this.permissionService = permissionService;
        this.scopeMapper = scopeMapper;
        this.apiKeyScopeService = apiKeyScopeService;
        this.auditSink = auditSink;
        this.mode = properties.mode();
    }

    @Override
    public VideoScope resolveDecisionScope(AuthPrincipal principal, String permissionCode) {
        Objects.requireNonNull(principal, "principal must not be null");
        requirePermissionCode(permissionCode);
        if (principal.type() == PrincipalType.API_KEY) {
            try {
                return apiKeyScopeService.resolveVideoScope(
                        principal.id(), permissionCode, principal);
            } catch (RuntimeException exception) {
                log.error("API Key 视频资源范围计算失败，按无权限处理, keyId={}, ownerId={}, permission={}",
                        principal.id(), principal.userId(), permissionCode, exception);
                return VideoScope.none();
            }
        }
        if (principal.systemUser()) {
            return VideoScope.unrestricted();
        }
        Long userId = principal.userId();
        if (userId == null || userId <= 0) {
            return VideoScope.none();
        }

        try {
            return loadDecisionScope(userId, permissionCode);
        } catch (RuntimeException exception) {
            log.error("视频资源范围计算失败，按无权限处理, principalType={}, principalId={}, permission={}",
                    principal.type(), principal.id(), permissionCode, exception);
            return VideoScope.none();
        }
    }

    @Override
    public VideoScope resolveEffectiveScope(AuthPrincipal principal, String permissionCode) {
        VideoScope decisionScope = resolveDecisionScope(principal, permissionCode);
        if (mode == AuthorizationMode.ENFORCE || decisionScope.all()) {
            return decisionScope;
        }
        if (mode == AuthorizationMode.AUDIT) {
            auditSink.record(new AuthzAuditRecord(
                    principal.type(),
                    principal.id(),
                    principal.deptId(),
                    permissionCode,
                    "VIDEO_SCOPE",
                    null,
                    false,
                    "VIDEO_SCOPE_AUDIT_EXPANDED",
                    null,
                    null,
                    LocalDateTime.now()));
        }
        return VideoScope.unrestricted();
    }

    private VideoScope loadDecisionScope(long userId, String permissionCode) {
        Set<Long> roleIds = normalizeIds(
                permissionService.findGrantingRoleIds(userId, permissionCode));
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
            VideoScopeType scopeType = VideoScopeType.fromDatabaseValue(grant.scopeType());
            switch (scopeType) {
                case ALL -> {
                    return VideoScope.unrestricted();
                }
                case CUSTOM -> customRoleIds.add(grant.roleId());
                case NONE -> {
                    // NONE deliberately contributes no video resources.
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

    private void requirePermissionCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            throw new IllegalArgumentException("permissionCode must not be blank");
        }
    }
}
