package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.apikey.ApiKeyScopeService;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.repository.AuthorizationAuditSink;
import com.genersoft.iot.vmp.auth.service.PermissionService;
import com.genersoft.iot.vmp.authorization.video.impl.VideoResourceScopeServiceImpl;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoRoleScopeGrant;
import com.genersoft.iot.vmp.authorization.video.model.VideoScope;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeResourceRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoResourceScopeServiceTest {

    private static final String CHANNEL_PLAY = "video:channel:play";
    private static final String RECORD_LIST = "video:record:list";

    @Mock private PermissionService permissionService;
    @Mock private VideoScopeMapper scopeMapper;
    @Mock private ApiKeyScopeService apiKeyScopeService;
    @Mock private AuthorizationAuditSink auditSink;

    private VideoResourceScopeService service;

    @BeforeEach
    void setUp() {
        service = service(AuthorizationMode.ENFORCE);
    }

    @Test
    void permissionScopesCannotBorrowResourcesFromAnotherRole() {
        when(permissionService.findGrantingRoleIds(12L, CHANNEL_PLAY)).thenReturn(Set.of(10L));
        when(scopeMapper.findEnabledRoleScopes(Set.of(10L)))
                .thenReturn(List.of(new VideoRoleScopeGrant(10L, "CUSTOM")));
        when(scopeMapper.findEnabledResources(Set.of(10L))).thenReturn(List.of(
                new VideoScopeResourceRow(VideoResourceType.GB_DEVICE, 1001L)));

        when(permissionService.findGrantingRoleIds(12L, RECORD_LIST)).thenReturn(Set.of(20L));
        when(scopeMapper.findEnabledRoleScopes(Set.of(20L)))
                .thenReturn(List.of(new VideoRoleScopeGrant(20L, "CUSTOM")));
        when(scopeMapper.findEnabledResources(Set.of(20L))).thenReturn(List.of(
                new VideoScopeResourceRow(VideoResourceType.CHANNEL, 2001L)));

        VideoScope playScope = service.resolveDecisionScope(principal(false), CHANNEL_PLAY);
        VideoScope recordScope = service.resolveDecisionScope(principal(false), RECORD_LIST);

        assertEquals(Set.of(10L), playScope.grantingRoleIds());
        assertEquals(Set.of(new VideoResourceRef(VideoResourceType.GB_DEVICE, 1001L)),
                playScope.sourceResources());
        assertTrue(playScope.explicitChannelIds().isEmpty());
        assertEquals(Set.of(20L), recordScope.grantingRoleIds());
        assertTrue(recordScope.sourceResources().isEmpty());
        assertEquals(Set.of(2001L), recordScope.explicitChannelIds());
    }

    @Test
    void allShortCircuitsBeforeCustomResourcesAreExpanded() {
        when(permissionService.findGrantingRoleIds(12L, CHANNEL_PLAY))
                .thenReturn(Set.of(10L, 20L));
        when(scopeMapper.findEnabledRoleScopes(Set.of(10L, 20L))).thenReturn(List.of(
                new VideoRoleScopeGrant(10L, "CUSTOM"),
                new VideoRoleScopeGrant(20L, "ALL")));

        VideoScope scope = service.resolveDecisionScope(principal(false), CHANNEL_PLAY);

        assertTrue(scope.all());
        assertTrue(scope.grantingRoleIds().isEmpty());
        verify(scopeMapper, never()).findEnabledResources(anyCollection());
    }

    @Test
    void noneAndRolesExcludedByTheMapperContributeNoResources() {
        when(permissionService.findGrantingRoleIds(12L, CHANNEL_PLAY))
                .thenReturn(Set.of(10L, 11L));
        when(scopeMapper.findEnabledRoleScopes(Set.of(10L, 11L)))
                .thenReturn(List.of(new VideoRoleScopeGrant(10L, "NONE")));

        VideoScope scope = service.resolveDecisionScope(principal(false), CHANNEL_PLAY);

        assertTrue(scope.isNone());
        verify(scopeMapper, never()).findEnabledResources(anyCollection());
    }

    @Test
    void customRolesAndDuplicateGroupItemsAreMergedAsSets() {
        when(permissionService.findGrantingRoleIds(12L, CHANNEL_PLAY))
                .thenReturn(Set.of(10L, 20L));
        when(scopeMapper.findEnabledRoleScopes(Set.of(10L, 20L))).thenReturn(List.of(
                new VideoRoleScopeGrant(10L, "CUSTOM"),
                new VideoRoleScopeGrant(20L, "CUSTOM")));
        when(scopeMapper.findEnabledResources(Set.of(10L, 20L))).thenReturn(List.of(
                new VideoScopeResourceRow(VideoResourceType.STREAM_PROXY, 3001L),
                new VideoScopeResourceRow(VideoResourceType.STREAM_PROXY, 3001L),
                new VideoScopeResourceRow(VideoResourceType.CHANNEL, 4001L),
                new VideoScopeResourceRow(VideoResourceType.CHANNEL, 4001L)));

        VideoScope scope = service.resolveDecisionScope(principal(false), CHANNEL_PLAY);

        assertEquals(Set.of(10L, 20L), scope.grantingRoleIds());
        assertEquals(Set.of(new VideoResourceRef(VideoResourceType.STREAM_PROXY, 3001L)),
                scope.sourceResources());
        assertEquals(Set.of(4001L), scope.explicitChannelIds());
        assertFalse(scope.isNone());
    }

    @Test
    void systemUserAlwaysGetsAllWithoutDatabaseScopeQueries() {
        assertTrue(service.resolveDecisionScope(principal(true), CHANNEL_PLAY).all());

        verifyNoInteractions(permissionService, scopeMapper, auditSink);
    }

    @Test
    void apiKeyUsesItsOwnRealtimeIntersectionEvenWhenOwnerWasSystemUser() {
        AuthPrincipal apiKey = new AuthPrincipal(
                PrincipalType.API_KEY,
                88L,
                12L,
                "machine-key",
                10L,
                Set.of(10L),
                7L,
                false,
                Set.of(CHANNEL_PLAY));
        VideoScope restricted = new VideoScope(false, Set.of(), Set.of(), Set.of(1001L));
        when(apiKeyScopeService.resolveVideoScope(88L, CHANNEL_PLAY, apiKey))
                .thenReturn(restricted);

        assertEquals(restricted, service.resolveDecisionScope(apiKey, CHANNEL_PLAY));
        verifyNoInteractions(permissionService, scopeMapper);
    }

    @Test
    void dependencyFailuresAreFailClosed() {
        when(permissionService.findGrantingRoleIds(12L, CHANNEL_PLAY))
                .thenThrow(new IllegalStateException("cache unavailable"));

        assertTrue(service.resolveDecisionScope(principal(false), CHANNEL_PLAY).isNone());
        verifyNoInteractions(scopeMapper);

        when(permissionService.findGrantingRoleIds(12L, RECORD_LIST)).thenReturn(Set.of(20L));
        when(scopeMapper.findEnabledRoleScopes(Set.of(20L)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertTrue(service.resolveDecisionScope(principal(false), RECORD_LIST).isNone());
    }

    @Test
    void auditExpandsOnlyTheEffectiveScopeAndRecordsTheDifference() {
        service = service(AuthorizationMode.AUDIT);
        when(permissionService.findGrantingRoleIds(12L, CHANNEL_PLAY)).thenReturn(Set.of(10L));
        when(scopeMapper.findEnabledRoleScopes(Set.of(10L)))
                .thenReturn(List.of(new VideoRoleScopeGrant(10L, "CUSTOM")));
        when(scopeMapper.findEnabledResources(Set.of(10L))).thenReturn(List.of(
                new VideoScopeResourceRow(VideoResourceType.CHANNEL, 1001L)));

        VideoScope effective = service.resolveEffectiveScope(principal(false), CHANNEL_PLAY);

        assertTrue(effective.all());
        ArgumentCaptor<AuthzAuditRecord> captor = ArgumentCaptor.forClass(AuthzAuditRecord.class);
        verify(auditSink).record(captor.capture());
        assertEquals("VIDEO_SCOPE_AUDIT_EXPANDED", captor.getValue().reasonCode());
        assertEquals("VIDEO_SCOPE", captor.getValue().resourceType());
        assertFalse(captor.getValue().allowed());
    }

    @Test
    void disabledModeExpandsWithoutWritingAuditAndBlankPermissionIsRejected() {
        service = service(AuthorizationMode.DISABLED);
        when(permissionService.findGrantingRoleIds(12L, CHANNEL_PLAY)).thenReturn(Set.of());

        assertTrue(service.resolveEffectiveScope(principal(false), CHANNEL_PLAY).all());
        verifyNoInteractions(auditSink);
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveDecisionScope(principal(false), " "));
    }

    @Test
    void mapperQuerySnapshotIsImmutableAndCarriesPermissionAndPrincipalMetadata() {
        Set<Long> roleIds = new LinkedHashSet<>(Set.of(10L));
        Set<VideoResourceRef> sources = new LinkedHashSet<>(Set.of(
                new VideoResourceRef(VideoResourceType.GB_DEVICE, 1001L)));
        Set<Long> channelIds = new LinkedHashSet<>(Set.of(2001L));
        VideoScope scope = new VideoScope(false, roleIds, sources, channelIds);

        VideoScopeQuery query = scope.toQuery("  " + CHANNEL_PLAY + "  ", 88L);
        roleIds.clear();
        sources.clear();
        channelIds.clear();

        assertEquals(CHANNEL_PLAY, query.permissionCode());
        assertEquals(88L, query.principalId());
        assertEquals(Set.of(10L), query.grantingRoleIds());
        assertEquals(scope, query.toScope());
        assertThrows(UnsupportedOperationException.class,
                () -> query.grantingRoleIds().add(20L));
    }

    private VideoResourceScopeService service(AuthorizationMode mode) {
        return new VideoResourceScopeServiceImpl(
                permissionService,
                scopeMapper,
                apiKeyScopeService,
                auditSink,
                new AuthorizationProperties(mode, false, Duration.ofMinutes(5)));
    }

    private AuthPrincipal principal(boolean systemUser) {
        return new AuthPrincipal(
                PrincipalType.USER,
                12L,
                12L,
                "operator",
                10L,
                Set.of(10L, 20L),
                0L,
                systemUser,
                Set.of(CHANNEL_PLAY, RECORD_LIST));
    }
}
