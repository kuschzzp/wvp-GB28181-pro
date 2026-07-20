package com.genersoft.iot.vmp.authorization.impl;

import com.genersoft.iot.vmp.auth.department.DepartmentScopeService;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.ResourceAuthorizationService;
import com.genersoft.iot.vmp.authorization.video.VideoResourceScopeService;
import com.genersoft.iot.vmp.authorization.video.model.ResourceDenialMode;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScope;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Objects;

@Service
public class AuthorizationFacadeImpl implements AuthorizationFacade {

    private final CurrentPrincipalService currentPrincipalService;
    private final PermissionEnforcer permissionEnforcer;
    private final ResourceAuthorizationService resourceAuthorizationService;
    private final VideoResourceScopeService videoScopeService;
    private final DepartmentScopeService departmentScopeService;

    public AuthorizationFacadeImpl(
            CurrentPrincipalService currentPrincipalService,
            PermissionEnforcer permissionEnforcer,
            ResourceAuthorizationService resourceAuthorizationService,
            VideoResourceScopeService videoScopeService,
            DepartmentScopeService departmentScopeService) {
        this.currentPrincipalService = currentPrincipalService;
        this.permissionEnforcer = permissionEnforcer;
        this.resourceAuthorizationService = resourceAuthorizationService;
        this.videoScopeService = videoScopeService;
        this.departmentScopeService = departmentScopeService;
    }

    @Override
    public void requirePermission(String permissionCode) {
        if (!permissionEnforcer.check(permissionCode)) {
            throw new AccessDeniedException("缺少功能权限");
        }
    }

    @Override
    public void requireVideoResource(
            String permissionCode,
            VideoResourceType resourceType,
            long resourceId) {
        requireVideoResource(
                permissionCode, resourceType, resourceId, ResourceDenialMode.FORBID);
    }

    @Override
    public void requireVideoResource(
            String permissionCode,
            VideoResourceType resourceType,
            long resourceId,
            ResourceDenialMode denialMode) {
        requirePermission(permissionCode);
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        resourceAuthorizationService.requireVideoResource(
                principal,
                permissionCode,
                new VideoResourceRef(resourceType, resourceId),
                Objects.requireNonNull(denialMode, "denialMode must not be null"));
    }

    @Override
    public void requireAllVideoResources(
            String permissionCode,
            Collection<VideoResourceRef> resources) {
        requirePermission(permissionCode);
        resourceAuthorizationService.requireAllVideoResources(
                currentPrincipalService.requireCurrent(), permissionCode, resources);
    }

    @Override
    public VideoScope resolveVideoScope(String permissionCode) {
        requirePermission(permissionCode);
        return videoScopeService.resolveEffectiveScope(
                currentPrincipalService.requireCurrent(), permissionCode);
    }

    @Override
    public VideoScopeQuery resolveVideoScopeQuery(String permissionCode) {
        requirePermission(permissionCode);
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        return videoScopeService.resolveEffectiveScope(principal, permissionCode)
                .toQuery(permissionCode, principal.id(), principal.systemUser());
    }

    @Override
    public DepartmentScope resolveDepartmentScope(String permissionCode) {
        requirePermission(permissionCode);
        return departmentScopeService.resolveEffectiveScope(
                currentPrincipalService.requireCurrent(), permissionCode);
    }
}
