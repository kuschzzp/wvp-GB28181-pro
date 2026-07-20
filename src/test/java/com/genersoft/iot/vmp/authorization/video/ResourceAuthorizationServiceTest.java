package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.auth.department.DepartmentScopeService;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.impl.AuthorizationFacadeImpl;
import com.genersoft.iot.vmp.authorization.video.impl.ResourceAuthorizationServiceImpl;
import com.genersoft.iot.vmp.authorization.video.model.ResourceDenialMode;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScope;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceAuthorizationServiceTest {

    private static final String CHANNEL_PLAY = "video:channel:play";

    @Mock private VideoResourceScopeService scopeService;
    @Mock private ResourceResolverRegistry resolverRegistry;
    @Mock private CurrentPrincipalService currentPrincipalService;
    @Mock private PermissionEnforcer permissionEnforcer;
    @Mock private DepartmentScopeService departmentScopeService;

    private ResourceAuthorizationService service;
    private AuthPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new ResourceAuthorizationServiceImpl(scopeService, resolverRegistry);
        principal = principal(false);
    }

    @Test
    void explicitChannelAndItsAuthorizedSourceBothAllowTheChannel() {
        VideoResourceRef channel = ref(VideoResourceType.CHANNEL, 101L);
        VideoResourceRef device = ref(VideoResourceType.GB_DEVICE, 11L);
        when(resolverRegistry.resolveAuthorizationCandidates(channel))
                .thenReturn(Set.of(channel, device));
        when(scopeService.resolveEffectiveScope(principal, CHANNEL_PLAY)).thenReturn(
                new VideoScope(false, Set.of(7L), Set.of(device), Set.of()));

        assertDoesNotThrow(() -> service.requireVideoResource(
                principal, CHANNEL_PLAY, channel, ResourceDenialMode.FORBID));

        when(scopeService.resolveEffectiveScope(principal, CHANNEL_PLAY)).thenReturn(
                new VideoScope(false, Set.of(7L), Set.of(), Set.of(101L)));
        assertDoesNotThrow(() -> service.requireVideoResource(
                principal, CHANNEL_PLAY, channel, ResourceDenialMode.FORBID));
    }

    @Test
    void missingFunctionalOrResourceScopeIsDeniedWithTheRequestedMode() {
        VideoResourceRef channel = ref(VideoResourceType.CHANNEL, 101L);
        when(resolverRegistry.resolveAuthorizationCandidates(channel)).thenReturn(Set.of(channel));
        when(scopeService.resolveEffectiveScope(principal, CHANNEL_PLAY))
                .thenReturn(VideoScope.none());

        assertThrows(AccessDeniedException.class, () -> service.requireVideoResource(
                principal, CHANNEL_PLAY, channel, ResourceDenialMode.FORBID));
        assertThrows(VideoResourceNotFoundException.class, () -> service.requireVideoResource(
                principal, CHANNEL_PLAY, channel, ResourceDenialMode.HIDE_AS_NOT_FOUND));
    }

    @Test
    void nonexistentObjectAlwaysRemainsNotFoundAndSkipsScopeCalculation() {
        VideoResourceRef channel = ref(VideoResourceType.CHANNEL, 999L);
        when(resolverRegistry.resolveAuthorizationCandidates(channel))
                .thenThrow(new VideoResourceNotFoundException("missing"));

        assertThrows(VideoResourceNotFoundException.class, () -> service.requireVideoResource(
                principal, CHANNEL_PLAY, channel, ResourceDenialMode.FORBID));

        verifyNoInteractions(scopeService);
    }

    @Test
    void batchWithOneUnauthorizedResourceIsRejectedAtomically() {
        VideoResourceRef first = ref(VideoResourceType.CHANNEL, 101L);
        VideoResourceRef second = ref(VideoResourceType.CHANNEL, 202L);
        VideoResourceRef device = ref(VideoResourceType.GB_DEVICE, 11L);
        when(resolverRegistry.resolveAuthorizationCandidates(first))
                .thenReturn(Set.of(first, device));
        when(resolverRegistry.resolveAuthorizationCandidates(second)).thenReturn(Set.of(second));
        when(scopeService.resolveEffectiveScope(principal, CHANNEL_PLAY)).thenReturn(
                new VideoScope(false, Set.of(7L), Set.of(device), Set.of()));

        assertThrows(AccessDeniedException.class, () -> service.requireAllVideoResources(
                principal, CHANNEL_PLAY, List.of(first, second)));

        verify(scopeService).resolveEffectiveScope(principal, CHANNEL_PLAY);
    }

    @Test
    void systemUserStillRequiresTheObjectToExistButBypassesResourceSets() {
        AuthPrincipal systemUser = principal(true);
        VideoResourceRef legacyRecord = ref(VideoResourceType.LEGACY_UNBOUND, 88L);
        when(resolverRegistry.resolveAuthorizationCandidates(legacyRecord))
                .thenReturn(Set.of(legacyRecord));
        when(scopeService.resolveEffectiveScope(systemUser, CHANNEL_PLAY))
                .thenReturn(VideoScope.unrestricted());

        assertDoesNotThrow(() -> service.requireVideoResource(
                systemUser, CHANNEL_PLAY, legacyRecord, ResourceDenialMode.FORBID));
        verify(resolverRegistry).resolveAuthorizationCandidates(legacyRecord);
    }

    @Test
    void facadeUsesEffectivePermissionAndDefaultsObjectDenialToForbid() {
        ResourceAuthorizationService resourceService = org.mockito.Mockito.mock(
                ResourceAuthorizationService.class);
        AuthorizationFacade facade = new AuthorizationFacadeImpl(
                currentPrincipalService,
                permissionEnforcer,
                resourceService,
                scopeService,
                departmentScopeService);
        VideoResourceRef channel = ref(VideoResourceType.CHANNEL, 101L);
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(permissionEnforcer.check(CHANNEL_PLAY)).thenReturn(true);

        facade.requireVideoResource(CHANNEL_PLAY, channel.type(), channel.id());

        verify(resourceService).requireVideoResource(
                principal, CHANNEL_PLAY, channel, ResourceDenialMode.FORBID);
    }

    @Test
    void facadeStopsBeforeObjectLookupWhenFunctionalPermissionIsEffectivelyDenied() {
        ResourceAuthorizationService resourceService = org.mockito.Mockito.mock(
                ResourceAuthorizationService.class);
        AuthorizationFacade facade = new AuthorizationFacadeImpl(
                currentPrincipalService,
                permissionEnforcer,
                resourceService,
                scopeService,
                departmentScopeService);
        when(permissionEnforcer.check(CHANNEL_PLAY)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> facade.requireVideoResource(CHANNEL_PLAY, VideoResourceType.CHANNEL, 101L));

        verifyNoInteractions(currentPrincipalService, resourceService);
    }

    @Test
    void facadeBuildsMapperScopeWithPermissionAndCurrentPrincipalIdentity() {
        ResourceAuthorizationService resourceService = org.mockito.Mockito.mock(
                ResourceAuthorizationService.class);
        AuthorizationFacade facade = new AuthorizationFacadeImpl(
                currentPrincipalService,
                permissionEnforcer,
                resourceService,
                scopeService,
                departmentScopeService);
        VideoScope scope = new VideoScope(
                false,
                Set.of(7L),
                Set.of(ref(VideoResourceType.GB_DEVICE, 11L)),
                Set.of(101L));
        when(permissionEnforcer.check(CHANNEL_PLAY)).thenReturn(true);
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(scopeService.resolveEffectiveScope(principal, CHANNEL_PLAY)).thenReturn(scope);

        VideoScopeQuery query = facade.resolveVideoScopeQuery(CHANNEL_PLAY);

        assertEquals(scope.toQuery(CHANNEL_PLAY, principal.id()), query);
    }

    private VideoResourceRef ref(VideoResourceType type, long id) {
        return new VideoResourceRef(type, id);
    }

    private AuthPrincipal principal(boolean systemUser) {
        return new AuthPrincipal(
                PrincipalType.USER,
                12L,
                12L,
                "operator",
                10L,
                Set.of(7L),
                0L,
                systemUser,
                Set.of(CHANNEL_PLAY));
    }
}
