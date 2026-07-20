package com.genersoft.iot.vmp.authorization.video.impl;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.authorization.video.ResourceAuthorizationService;
import com.genersoft.iot.vmp.authorization.video.VideoResourceScopeService;
import com.genersoft.iot.vmp.authorization.video.model.ResourceDenialMode;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScope;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ResourceAuthorizationServiceImpl implements ResourceAuthorizationService {

    private final VideoResourceScopeService scopeService;
    private final ResourceResolverRegistry resolverRegistry;

    public ResourceAuthorizationServiceImpl(
            VideoResourceScopeService scopeService,
            ResourceResolverRegistry resolverRegistry) {
        this.scopeService = scopeService;
        this.resolverRegistry = resolverRegistry;
    }

    @Override
    public void requireVideoResource(
            AuthPrincipal principal,
            String permissionCode,
            VideoResourceRef resource,
            ResourceDenialMode denialMode) {
        Objects.requireNonNull(principal, "principal must not be null");
        requirePermissionCode(permissionCode);
        Objects.requireNonNull(resource, "resource must not be null");
        Objects.requireNonNull(denialMode, "denialMode must not be null");

        Set<VideoResourceRef> candidates = requireCandidates(resource);
        VideoScope scope = scopeService.resolveEffectiveScope(principal, permissionCode);
        if (!isAllowed(scope, candidates)) {
            throw denial(denialMode);
        }
    }

    @Override
    public void requireAllVideoResources(
            AuthPrincipal principal,
            String permissionCode,
            Collection<VideoResourceRef> resources) {
        Objects.requireNonNull(principal, "principal must not be null");
        requirePermissionCode(permissionCode);
        Objects.requireNonNull(resources, "resources must not be null");
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("resources must not be empty");
        }

        LinkedHashSet<VideoResourceRef> distinct = new LinkedHashSet<>();
        for (VideoResourceRef resource : resources) {
            distinct.add(Objects.requireNonNull(resource, "resources must not contain null"));
        }
        List<Set<VideoResourceRef>> resolvedCandidates = new ArrayList<>(distinct.size());
        for (VideoResourceRef resource : distinct) {
            resolvedCandidates.add(requireCandidates(resource));
        }

        VideoScope scope = scopeService.resolveEffectiveScope(principal, permissionCode);
        for (Set<VideoResourceRef> candidates : resolvedCandidates) {
            if (!isAllowed(scope, candidates)) {
                throw denial(ResourceDenialMode.FORBID);
            }
        }
    }

    private Set<VideoResourceRef> requireCandidates(VideoResourceRef resource) {
        Set<VideoResourceRef> candidates = resolverRegistry.resolveAuthorizationCandidates(resource);
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("视频资源解析器没有返回授权候选项");
        }
        return Set.copyOf(candidates);
    }

    private boolean isAllowed(VideoScope scope, Set<VideoResourceRef> candidates) {
        Objects.requireNonNull(scope, "video scope must not be null");
        if (scope.all()) {
            return true;
        }
        for (VideoResourceRef candidate : candidates) {
            if (candidate.type() == VideoResourceType.CHANNEL) {
                if (scope.explicitChannelIds().contains(candidate.id())) {
                    return true;
                }
            } else if (scope.sourceResources().contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private RuntimeException denial(ResourceDenialMode mode) {
        return switch (mode) {
            case FORBID -> new AccessDeniedException("无权访问目标视频资源");
            case HIDE_AS_NOT_FOUND ->
                    new VideoResourceNotFoundException("视频资源不存在");
        };
    }

    private void requirePermissionCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            throw new IllegalArgumentException("permissionCode must not be blank");
        }
    }
}
