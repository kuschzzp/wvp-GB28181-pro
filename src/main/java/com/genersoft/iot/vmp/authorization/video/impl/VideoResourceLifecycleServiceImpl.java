package com.genersoft.iot.vmp.authorization.video.impl;

import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.authorization.resourcegroup.ResourceGroupItemMapper;
import com.genersoft.iot.vmp.authorization.resourcegroup.RoleResourceGroupMapper;
import com.genersoft.iot.vmp.authorization.video.VideoResourceLifecycleService;
import com.genersoft.iot.vmp.authorization.video.VideoResourceMigrationIssueRecorder;
import com.genersoft.iot.vmp.authorization.video.VideoResourceMigrationRequiredException;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class VideoResourceLifecycleServiceImpl
        implements VideoResourceLifecycleService {

    private static final String REMOVED_REASON = "VIDEO_RESOURCE_REMOVED";
    private static final String MIGRATED_REASON = "VIDEO_RESOURCE_ID_MIGRATED";
    private static final String MISSING_REASON =
            "RESOURCE_GRANT_MIGRATION_TARGET_MISSING";
    private static final String AMBIGUOUS_REASON =
            "RESOURCE_GRANT_MIGRATION_AMBIGUOUS";

    private final ResourceGroupItemMapper itemMapper;
    private final RoleResourceGroupMapper roleGroupMapper;
    private final ResourceResolverRegistry resolverRegistry;
    private final TokenVersionService tokenVersionService;
    private final VideoResourceMigrationIssueRecorder issueRecorder;

    public VideoResourceLifecycleServiceImpl(
            ResourceGroupItemMapper itemMapper,
            RoleResourceGroupMapper roleGroupMapper,
            ResourceResolverRegistry resolverRegistry,
            TokenVersionService tokenVersionService,
            VideoResourceMigrationIssueRecorder issueRecorder) {
        this.itemMapper = itemMapper;
        this.roleGroupMapper = roleGroupMapper;
        this.resolverRegistry = resolverRegistry;
        this.tokenVersionService = tokenVersionService;
        this.issueRecorder = issueRecorder;
    }

    @Override
    @Transactional
    public void remove(VideoResourceRef resource) {
        removeAll(List.of(requireBindable(resource)));
    }

    @Override
    @Transactional
    public void removeAll(Collection<VideoResourceRef> resources) {
        LinkedHashSet<VideoResourceRef> normalized =
                normalizeBindableResources(resources);
        if (normalized.isEmpty()) {
            return;
        }

        Set<Long> groupIds = findGroupIds(normalized);
        if (groupIds.isEmpty()) {
            return;
        }
        Set<Long> affectedUserIds = findAffectedUserIds(groupIds);
        for (VideoResourceRef resource : normalized) {
            itemMapper.deleteByResource(resource.type(), resource.id());
        }
        bumpUsers(affectedUserIds, REMOVED_REASON);
    }

    @Override
    @Transactional
    public void migrate(
            VideoResourceRef source,
            VideoResourceRef target,
            Long operatorId) {
        VideoResourceRef normalizedSource = requireBindable(source);
        VideoResourceRef normalizedTarget = requireBindable(target);
        requireOperatorId(operatorId);
        if (normalizedSource.equals(normalizedTarget)) {
            return;
        }
        if (normalizedSource.type() != normalizedTarget.type()) {
            throw new IllegalArgumentException("视频资源授权只能在相同类型之间迁移");
        }

        VideoResourceRef existingTarget =
                resolverRegistry.requireExisting(normalizedTarget);
        if (!normalizedTarget.equals(existingTarget)) {
            throw new IllegalStateException("资源解析器返回了不一致的迁移目标");
        }
        Set<Long> sourceGroupIds = findGroupIds(Set.of(normalizedSource));
        if (sourceGroupIds.isEmpty()) {
            return;
        }
        Set<Long> affectedUserIds = findAffectedUserIds(sourceGroupIds);
        itemMapper.copyResourceMemberships(
                normalizedSource.type(),
                normalizedSource.id(),
                normalizedTarget.type(),
                normalizedTarget.id(),
                operatorId,
                LocalDateTime.now());
        int removed = itemMapper.deleteByResource(
                normalizedSource.type(), normalizedSource.id());
        if (removed != sourceGroupIds.size()) {
            throw new IllegalStateException("视频资源授权迁移期间关系发生变化");
        }
        bumpUsers(affectedUserIds, MIGRATED_REASON);
    }

    @Override
    @Transactional
    public void migrateUnique(
            VideoResourceRef source,
            Collection<VideoResourceRef> candidates,
            String stableSource,
            Long operatorId) {
        VideoResourceRef normalizedSource = requireBindable(source);
        String normalizedStableSource = requireStableSource(stableSource);
        requireOperatorId(operatorId);
        if (findGroupIds(Set.of(normalizedSource)).isEmpty()) {
            return;
        }

        LinkedHashSet<VideoResourceRef> normalizedCandidates =
                normalizeBindableResources(candidates);
        for (VideoResourceRef candidate : normalizedCandidates) {
            if (candidate.type() != normalizedSource.type()) {
                throw new IllegalArgumentException(
                        "稳定来源迁移候选必须与原资源类型一致");
            }
        }
        if (normalizedCandidates.size() != 1) {
            String reasonCode = normalizedCandidates.isEmpty()
                    ? MISSING_REASON
                    : AMBIGUOUS_REASON;
            issueRecorder.record(
                    normalizedSource,
                    Set.copyOf(normalizedCandidates),
                    normalizedStableSource,
                    operatorId,
                    reasonCode);
            throw new VideoResourceMigrationRequiredException(
                    reasonCode,
                    "稳定来源无法唯一确定授权迁移目标，请人工处理");
        }
        migrate(
                normalizedSource,
                normalizedCandidates.iterator().next(),
                operatorId);
    }

    private LinkedHashSet<VideoResourceRef> normalizeBindableResources(
            Collection<VideoResourceRef> resources) {
        Objects.requireNonNull(resources, "resources must not be null");
        LinkedHashSet<VideoResourceRef> result = new LinkedHashSet<>();
        for (VideoResourceRef resource : resources) {
            result.add(requireBindable(resource));
        }
        return result;
    }

    private VideoResourceRef requireBindable(VideoResourceRef resource) {
        Objects.requireNonNull(resource, "video resource must not be null");
        if (!resource.type().isBindable()) {
            throw new IllegalArgumentException(
                    "该视频资源类型不能维护资源组关系: " + resource.type());
        }
        return resource;
    }

    private Set<Long> findGroupIds(Collection<VideoResourceRef> resources) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (VideoResourceRef resource : resources) {
            List<Long> groupIds = itemMapper.findGroupIdsByResource(
                    resource.type(), resource.id());
            if (groupIds != null) {
                groupIds.stream()
                        .filter(Objects::nonNull)
                        .filter(groupId -> groupId > 0)
                        .forEach(result::add);
            }
        }
        return Set.copyOf(result);
    }

    private Set<Long> findAffectedUserIds(Collection<Long> groupIds) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long groupId : groupIds) {
            List<Long> userIds = roleGroupMapper.findUserIdsCoveringGroup(groupId);
            if (userIds != null) {
                userIds.stream()
                        .filter(Objects::nonNull)
                        .filter(userId -> userId > 0)
                        .forEach(result::add);
            }
        }
        return Set.copyOf(result);
    }

    private void bumpUsers(Collection<Long> userIds, String reason) {
        if (userIds != null && !userIds.isEmpty()) {
            tokenVersionService.bumpUsers(userIds, reason);
        }
    }

    private String requireStableSource(String stableSource) {
        if (stableSource == null || stableSource.isBlank()) {
            throw new IllegalArgumentException("稳定来源标识不能为空");
        }
        return stableSource.trim();
    }

    private void requireOperatorId(Long operatorId) {
        if (operatorId != null && operatorId <= 0) {
            throw new IllegalArgumentException("操作人ID非法");
        }
    }
}
