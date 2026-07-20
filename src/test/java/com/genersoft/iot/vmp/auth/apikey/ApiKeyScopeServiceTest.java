package com.genersoft.iot.vmp.auth.apikey;

import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyScopeCommand;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyScopeOptions;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.service.PermissionService;
import com.genersoft.iot.vmp.authorization.video.UserVideoScopeResolver;
import com.genersoft.iot.vmp.authorization.resourcegroup.ResourceGroupService;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.ResourceGroupTreeNode;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScope;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeResourceRow;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import com.genersoft.iot.vmp.storager.dao.ApiKeyPermissionMapper;
import com.genersoft.iot.vmp.storager.dao.ApiKeyResourceGroupMapper;
import com.genersoft.iot.vmp.storager.dao.MenuMapper;
import com.genersoft.iot.vmp.storager.dao.UserApiKeyMapper;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import com.genersoft.iot.vmp.storager.dao.dto.Menu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyScopeServiceTest {

    private static final String LIST = "video:channel:list";
    private static final String PLAY = "video:channel:play";

    @Mock private ApiKeyPermissionMapper permissionMapper;
    @Mock private ApiKeyResourceGroupMapper resourceGroupMapper;
    @Mock private UserApiKeyMapper userApiKeyMapper;
    @Mock private PermissionService permissionService;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserVideoScopeResolver ownerScopeResolver;
    @Mock private ResourceResolverRegistry resolverRegistry;
    @Mock private MenuMapper menuMapper;
    @Mock private ResourceGroupService resourceGroupService;

    private ApiKeyScopeService service;

    @BeforeEach
    void setUp() {
        service = new ApiKeyScopeService(
                permissionMapper,
                resourceGroupMapper,
                userApiKeyMapper,
                permissionService,
                permissionRepository,
                ownerScopeResolver,
                resolverRegistry,
                menuMapper,
                resourceGroupService);
    }

    @Test
    void effectivePermissionsAreTheRealtimeIntersectionOfOwnerAndKey() {
        when(permissionService.resolvePermissions(12L, 7L)).thenReturn(Set.of(LIST, PLAY));
        when(permissionMapper.findEnabledPermissionCodes(88L))
                .thenReturn(List.of(LIST, "video:channel:edit"));

        assertEquals(Set.of(LIST), service.resolvePermissions(88L, ownerPrincipal()));

        when(permissionService.resolvePermissions(12L, 7L)).thenReturn(Set.of(PLAY));
        assertTrue(service.resolvePermissions(88L, ownerPrincipal()).isEmpty(),
                "所属用户收回权限后 Key 必须立即收缩");
    }

    @Test
    void resourceScopeKeepsOnlyKeyResourcesStillAuthorizedForTheOwner() {
        VideoResourceRef ownerDevice = ref(VideoResourceType.GB_DEVICE, 10L);
        VideoResourceRef allowedChannel = ref(VideoResourceType.CHANNEL, 30L);
        VideoResourceRef deniedChannel = ref(VideoResourceType.CHANNEL, 40L);
        when(permissionService.resolvePermissions(12L, 7L)).thenReturn(Set.of(PLAY));
        when(permissionMapper.findEnabledPermissionCodes(88L)).thenReturn(List.of(PLAY));
        when(permissionRepository.isSystemUser(12L)).thenReturn(false);
        when(ownerScopeResolver.resolve(12L, PLAY, false)).thenReturn(new VideoScope(
                false, Set.of(3L), Set.of(ownerDevice), Set.of(20L)));
        when(resourceGroupMapper.findEnabledResources(88L)).thenReturn(List.of(
                row(VideoResourceType.GB_DEVICE, 10L),
                row(VideoResourceType.CHANNEL, 30L),
                row(VideoResourceType.CHANNEL, 40L)));
        when(resolverRegistry.resolveAuthorizationCandidates(ownerDevice))
                .thenReturn(Set.of(ownerDevice));
        when(resolverRegistry.resolveAuthorizationCandidates(allowedChannel))
                .thenReturn(Set.of(allowedChannel, ownerDevice));
        when(resolverRegistry.resolveAuthorizationCandidates(deniedChannel))
                .thenReturn(Set.of(deniedChannel, ref(VideoResourceType.GB_DEVICE, 11L)));

        VideoScope scope = service.resolveVideoScope(88L, PLAY, ownerPrincipal());

        assertEquals(Set.of(ownerDevice), scope.sourceResources());
        assertEquals(Set.of(30L), scope.explicitChannelIds());
        assertTrue(scope.grantingRoleIds().isEmpty());
    }

    @Test
    void missingKeyPermissionShortCircuitsResourceLookup() {
        when(permissionService.resolvePermissions(12L, 7L)).thenReturn(Set.of(PLAY));
        when(permissionMapper.findEnabledPermissionCodes(88L)).thenReturn(List.of(LIST));

        assertTrue(service.resolveVideoScope(88L, PLAY, ownerPrincipal()).isNone());

        verifyNoInteractions(resourceGroupMapper, ownerScopeResolver, resolverRegistry);
    }

    @Test
    void systemOwnerIsStillCappedByTheKeyResourceGroups() {
        when(permissionService.resolvePermissions(12L, 7L)).thenReturn(Set.of(PLAY));
        when(permissionMapper.findEnabledPermissionCodes(88L)).thenReturn(List.of(PLAY));
        when(permissionRepository.isSystemUser(12L)).thenReturn(true);
        when(ownerScopeResolver.resolve(12L, PLAY, true)).thenReturn(VideoScope.unrestricted());
        when(resourceGroupMapper.findEnabledResources(88L))
                .thenReturn(List.of(row(VideoResourceType.CHANNEL, 30L)));

        VideoScope scope = service.resolveVideoScope(88L, PLAY, ownerPrincipal());

        assertEquals(Set.of(30L), scope.explicitChannelIds());
        assertTrue(!scope.all());
    }

    @Test
    void scopeCreationRejectsPermissionsAndResourcesOutsideOwnerRange() {
        User owner = owner();
        ApiKeyScopeCommand command = new ApiKeyScopeCommand(
                Set.of(501L), Set.of(601L), List.of("10.0.0.0/8"));
        when(permissionMapper.countEnabledPermissionMenus(Set.of(501L))).thenReturn(1);
        when(permissionMapper.findEnabledPermissionCodesByMenuIds(Set.of(501L)))
                .thenReturn(List.of(PLAY));
        when(permissionService.resolvePermissions(12L, 7L)).thenReturn(Set.of(LIST));

        assertThrows(AccessDeniedException.class,
                () -> service.replaceScope(88L, owner, command));

        verify(resourceGroupMapper, never()).deleteByApiKeyId(88L);
        verify(userApiKeyMapper, never()).updateIpAllowlist(88L, "10.0.0.0/8");
    }

    @Test
    void scopeReplacementValidatesThenAtomicallyReplacesBothRelationsAndCidr() {
        User owner = owner();
        VideoResourceRef device = ref(VideoResourceType.GB_DEVICE, 10L);
        ApiKeyScopeCommand command = new ApiKeyScopeCommand(
                Set.of(501L), Set.of(601L), List.of("10.0.0.0/8", "2001:db8::/32"));
        when(permissionMapper.countEnabledPermissionMenus(Set.of(501L))).thenReturn(1);
        when(permissionMapper.findEnabledPermissionCodesByMenuIds(Set.of(501L)))
                .thenReturn(List.of(PLAY));
        when(permissionService.resolvePermissions(12L, 7L)).thenReturn(Set.of(PLAY));
        when(resourceGroupMapper.countEnabledResourceGroups(Set.of(601L))).thenReturn(1);
        when(resourceGroupMapper.findEnabledResourcesByGroupIds(Set.of(601L)))
                .thenReturn(List.of(row(VideoResourceType.GB_DEVICE, 10L)));
        when(permissionRepository.isSystemUser(12L)).thenReturn(false);
        when(ownerScopeResolver.resolve(12L, PLAY, false)).thenReturn(new VideoScope(
                false, Set.of(3L), Set.of(device), Set.of()));
        when(resolverRegistry.resolveAuthorizationCandidates(device)).thenReturn(Set.of(device));
        when(permissionMapper.insert(88L, 501L)).thenReturn(1);
        when(resourceGroupMapper.insert(88L, 601L)).thenReturn(1);
        when(userApiKeyMapper.updateIpAllowlist(
                88L, "10.0.0.0/8,2001:db8::/32")).thenReturn(1);

        service.replaceScope(88L, owner, command);

        verify(permissionMapper).deleteByApiKeyId(88L);
        verify(resourceGroupMapper).deleteByApiKeyId(88L);
        verify(permissionMapper).insert(88L, 501L);
        verify(resourceGroupMapper).insert(88L, 601L);
        verify(userApiKeyMapper).updateIpAllowlist(
                88L, "10.0.0.0/8,2001:db8::/32");
    }

    @Test
    void dependencyFailuresPropagateSoAuthenticationCanFailClosed() {
        when(permissionMapper.findEnabledPermissionCodes(88L))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class,
                () -> service.resolvePermissions(88L, ownerPrincipal()));
        verify(permissionService, never()).resolvePermissions(12L, 7L);
    }

    @Test
    void optionsExposeOnlyOwnerPermissionsAndDisableDescendantsOfStoppedGroups() {
        Menu root = menu(100L, 0L, "视频管理", null, 0);
        Menu allowed = menu(101L, 100L, "实时播放", PLAY, 0);
        Menu denied = menu(102L, 100L, "设备删除", "video:device:remove", 0);
        Menu stopped = menu(103L, 100L, "已停用功能", LIST, 1);
        when(permissionService.resolvePermissions(12L, 7L)).thenReturn(Set.of(PLAY, LIST));
        when(menuMapper.findAll()).thenReturn(List.of(root, allowed, denied, stopped));
        when(resourceGroupService.tree()).thenReturn(List.of(new ResourceGroupTreeNode(
                1L, 0L, "ROOT_VIDEO", "全部视频资源", 0, 1, null,
                List.of(new ResourceGroupTreeNode(
                        2L, 1L, "CHILD", "子资源组", 0, 0, null, List.of())))));

        ApiKeyScopeOptions options = service.options(owner());

        assertEquals(List.of(100L, 101L), options.permissionMenus().stream()
                .map(option -> option.id()).toList());
        assertTrue(!options.permissionMenus().getFirst().selectable());
        assertTrue(options.permissionMenus().getLast().selectable());
        assertEquals(1, options.resourceGroups().getFirst().children().getFirst().status());
    }

    private AuthPrincipal ownerPrincipal() {
        return new AuthPrincipal(
                PrincipalType.USER,
                12L,
                12L,
                "owner",
                10L,
                Set.of(3L),
                7L,
                false,
                Set.of());
    }

    private User owner() {
        User user = new User();
        user.setId(12L);
        user.setUsername("owner");
        user.setTokenVersion(7L);
        return user;
    }

    private VideoResourceRef ref(VideoResourceType type, long id) {
        return new VideoResourceRef(type, id);
    }

    private VideoScopeResourceRow row(VideoResourceType type, long id) {
        return new VideoScopeResourceRow(type, id);
    }

    private Menu menu(long id, long parentId, String name, String permission, int status) {
        Menu menu = new Menu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setMenuType(permission == null ? "M" : "F");
        menu.setPerms(permission);
        menu.setOrderNum((int) id);
        menu.setStatus(status);
        return menu;
    }
}
