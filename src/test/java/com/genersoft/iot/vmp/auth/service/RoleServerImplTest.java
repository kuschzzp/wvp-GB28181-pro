package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.dto.RoleCommand;
import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.department.DepartmentMapper;
import com.genersoft.iot.vmp.auth.department.RoleDepartmentMapper;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.service.impl.RoleServerImpl;
import com.genersoft.iot.vmp.authorization.resourcegroup.ResourceGroupService;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeType;
import com.genersoft.iot.vmp.storager.dao.LegacyRoleMapper;
import com.genersoft.iot.vmp.storager.dao.MenuMapper;
import com.genersoft.iot.vmp.storager.dao.RoleMapper;
import com.genersoft.iot.vmp.storager.dao.UserMapper;
import com.genersoft.iot.vmp.storager.dao.UserRoleMapper;
import com.genersoft.iot.vmp.storager.dao.dto.LegacyRole;
import com.genersoft.iot.vmp.storager.dao.dto.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServerImplTest {

    @Mock private RoleMapper roleMapper;
    @Mock private LegacyRoleMapper legacyRoleMapper;
    @Mock private MenuMapper menuMapper;
    @Mock private RoleDepartmentMapper roleDepartmentMapper;
    @Mock private DepartmentMapper departmentMapper;
    @Mock private UserMapper userMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private TokenVersionService tokenVersionService;
    @Mock private CurrentPrincipalService currentPrincipalService;
    @Mock private PermissionEnforcer permissionEnforcer;
    @Mock private ResourceGroupService resourceGroupService;

    private RoleServerImpl service;

    @BeforeEach
    void setUp() {
        service = service(true);
    }

    private RoleServerImpl service(boolean legacyRoleDualWriteEnabled) {
        return new RoleServerImpl(
                roleMapper,
                legacyRoleMapper,
                menuMapper,
                roleDepartmentMapper,
                departmentMapper,
                userMapper,
                userRoleMapper,
                tokenVersionService,
                currentPrincipalService,
                permissionEnforcer,
                resourceGroupService,
                new AuthorizationProperties(
                        com.genersoft.iot.vmp.auth.model.AuthorizationMode.AUDIT,
                        false,
                        Duration.ofMinutes(5),
                        false,
                        "2026-10-01T00:00:00Z",
                        "test-api-key-pepper-0123456789abcdef",
                        false,
                        "2026-10-01T00:00:00Z",
                        legacyRoleDualWriteEnabled));
    }

    @Test
    void createsLegacyRoleFirstAndReusesItsGeneratedIdForNewRole() {
        when(currentPrincipalService.requireCurrent()).thenReturn(systemPrincipal());
        when(menuMapper.countEnabledByIds(Set.of(110L))).thenReturn(1);
        doAnswer(invocation -> {
            LegacyRole role = invocation.getArgument(0);
            role.setId(7L);
            return 1;
        }).when(legacyRoleMapper).add(any(LegacyRole.class));
        when(roleMapper.add(any(Role.class))).thenReturn(1);

        service.saveRole(command(null, "operator"), Set.of(110L));

        ArgumentCaptor<LegacyRole> legacy = ArgumentCaptor.forClass(LegacyRole.class);
        ArgumentCaptor<Role> current = ArgumentCaptor.forClass(Role.class);
        InOrder writes = inOrder(legacyRoleMapper, roleMapper, menuMapper);
        writes.verify(legacyRoleMapper).add(legacy.capture());
        writes.verify(roleMapper).add(current.capture());
        writes.verify(menuMapper).deleteRoleMenus(7L);
        writes.verify(menuMapper).insertRoleMenus(7L, new java.util.TreeSet<>(Set.of(110L)));

        assertEquals(7L, current.getValue().getId());
        assertEquals("operator", legacy.getValue().getAuthority());
    }

    @Test
    void updatesCurrentAndLegacyRoleInOneServiceTransaction() {
        when(currentPrincipalService.requireCurrent()).thenReturn(systemPrincipal());
        Role existing = role(7L, false);
        when(roleMapper.selectByIdForUpdate(7L)).thenReturn(existing);
        when(roleMapper.update(existing)).thenReturn(1);
        when(legacyRoleMapper.update(any(LegacyRole.class))).thenReturn(1);

        service.saveRole(command(7L, "operator_v2"), Set.of());

        ArgumentCaptor<LegacyRole> legacy = ArgumentCaptor.forClass(LegacyRole.class);
        verify(legacyRoleMapper).update(legacy.capture());
        assertEquals(7L, legacy.getValue().getId());
        assertEquals("operator_v2", legacy.getValue().getAuthority());
        verify(tokenVersionService).bumpUsersByRoleId(7L, "ROLE_PERMISSIONS_CHANGED");
    }

    @Test
    void contractPhaseCreatesAndUpdatesRolesWithoutTouchingLegacyMasterTable() {
        service = service(false);
        when(currentPrincipalService.requireCurrent()).thenReturn(systemPrincipal());
        doAnswer(invocation -> {
            Role role = invocation.getArgument(0);
            role.setId(8L);
            return 1;
        }).when(roleMapper).add(any(Role.class));

        service.saveRole(command(null, "contract_operator"), Set.of());

        verify(roleMapper).add(any(Role.class));
        verify(legacyRoleMapper, org.mockito.Mockito.never()).add(any(LegacyRole.class));

        Role existing = role(8L, false);
        when(roleMapper.selectByIdForUpdate(8L)).thenReturn(existing);
        when(roleMapper.update(existing)).thenReturn(1);

        service.saveRole(command(8L, "contract_operator_v2"), Set.of());

        verify(roleMapper).update(existing);
        verify(legacyRoleMapper, org.mockito.Mockito.never()).update(any(LegacyRole.class));
    }

    @Test
    void ordinaryRoleEditCannotChangeStatusWithoutStatusCommand() {
        when(currentPrincipalService.requireCurrent()).thenReturn(systemPrincipal());
        Role existing = role(7L, false);
        existing.setStatus(1);
        when(roleMapper.selectByIdForUpdate(7L)).thenReturn(existing);
        when(roleMapper.update(existing)).thenReturn(1);
        when(legacyRoleMapper.update(any(LegacyRole.class))).thenReturn(1);

        service.saveRole(command(7L, "operator_v2"), Set.of());

        assertEquals(1, existing.getStatus());
    }

    @Test
    void dedicatedStatusCommandInvalidatesUsersAndProtectsSystemRole() {
        when(currentPrincipalService.requireCurrent()).thenReturn(systemPrincipal());
        Role ordinary = role(7L, false);
        ordinary.setStatus(0);
        when(roleMapper.selectById(7L)).thenReturn(ordinary);
        when(roleMapper.updateStatus(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(java.time.LocalDateTime.class)))
                .thenReturn(1);

        service.changeStatus(7L, 1);

        verify(tokenVersionService).bumpUsersByRoleId(7L, "ROLE_STATUS_CHANGED");

        when(roleMapper.selectById(1L)).thenReturn(role(1L, true));
        assertThrows(AccessDeniedException.class, () -> service.changeStatus(1L, 1));
    }

    @Test
    void customScopeReplacesExactDepartmentsAndBumpsAffectedUsersOnce() {
        when(currentPrincipalService.requireCurrent()).thenReturn(systemPrincipal());
        Role existing = role(7L, false);
        when(roleMapper.selectByIdForUpdate(7L)).thenReturn(existing);
        when(roleMapper.update(existing)).thenReturn(1);
        when(legacyRoleMapper.update(any(LegacyRole.class))).thenReturn(1);
        when(departmentMapper.countEnabledByIds(Set.of(10L, 11L))).thenReturn(2);

        service.saveRole(
                new RoleCommand(
                        7L, "部门管理员", "dept_admin", 10,
                        "CUSTOM", "NONE", 0, false),
                Set.of(),
                Set.of(11L, 10L));

        verify(roleDepartmentMapper).deleteByRoleId(7L);
        verify(roleDepartmentMapper).insertBatch(
                7L, new java.util.TreeSet<>(Set.of(10L, 11L)));
        verify(tokenVersionService).bumpUsersByRoleId(7L, "ROLE_PERMISSIONS_CHANGED");
    }

    @Test
    void customVideoScopePersistsResourceGroupsInsideRoleSave() {
        when(currentPrincipalService.requireCurrent()).thenReturn(systemPrincipal());
        Role existing = role(7L, false);
        when(roleMapper.selectByIdForUpdate(7L)).thenReturn(existing);
        when(roleMapper.update(existing)).thenReturn(1);
        when(legacyRoleMapper.update(any(LegacyRole.class))).thenReturn(1);
        when(permissionEnforcer.require("system:resource-group:assign")).thenReturn(true);

        service.saveRole(
                new RoleCommand(
                        7L, "视频资源管理员", "video_admin", 10,
                        "NONE", "CUSTOM", 0, false),
                Set.of(),
                Set.of(),
                Set.of(101L, 100L));

        verify(resourceGroupService).replaceRoleGroupsForRoleUpdate(
                7L, VideoScopeType.CUSTOM, Set.of(100L, 101L), 1L);
        verify(tokenVersionService).bumpUsersByRoleId(7L, "ROLE_PERMISSIONS_CHANGED");
    }

    @Test
    void changingVideoScopeRequiresResourceGroupAssignmentPermission() {
        when(currentPrincipalService.requireCurrent()).thenReturn(systemPrincipal());
        Role existing = role(7L, false);
        existing.setVideoScopeType("CUSTOM");
        when(roleMapper.selectByIdForUpdate(7L)).thenReturn(existing);
        when(resourceGroupService.findRoleGroupIds(7L)).thenReturn(Set.of(200L, 201L));
        when(permissionEnforcer.require("system:resource-group:assign")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.saveRole(
                new RoleCommand(
                        7L, "视频资源管理员", "video_admin", 10,
                        "NONE", "CUSTOM", 0, false),
                Set.of(),
                Set.of(),
                Set.of(200L, 202L)));

        verify(permissionEnforcer).require("system:resource-group:assign");
        verify(roleMapper, org.mockito.Mockito.never()).update(any(Role.class));
    }

    @Test
    void ordinaryRoleEditMayPreserveVideoScopeWithoutAssignmentPermission() {
        when(currentPrincipalService.requireCurrent()).thenReturn(systemPrincipal());
        Role existing = role(7L, false);
        existing.setVideoScopeType("CUSTOM");
        when(roleMapper.selectByIdForUpdate(7L)).thenReturn(existing);
        when(resourceGroupService.findRoleGroupIds(7L)).thenReturn(Set.of(200L, 201L));
        when(roleMapper.update(existing)).thenReturn(1);
        when(legacyRoleMapper.update(any(LegacyRole.class))).thenReturn(1);

        service.saveRole(
                new RoleCommand(
                        7L, "视频资源管理员", "video_admin", 10,
                        "NONE", "CUSTOM", 0, false),
                Set.of(),
                Set.of(),
                Set.of(200L, 201L));

        verify(permissionEnforcer, org.mockito.Mockito.never())
                .require("system:resource-group:assign");
        verify(roleMapper).update(existing);
    }

    @Test
    void creatingCustomVideoScopeRequiresResourceGroupAssignmentPermission() {
        when(currentPrincipalService.requireCurrent()).thenReturn(systemPrincipal());
        when(permissionEnforcer.require("system:resource-group:assign")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.saveRole(
                new RoleCommand(
                        null, "视频资源管理员", "video_admin", 10,
                        "NONE", "CUSTOM", 0, false),
                Set.of(),
                Set.of(),
                Set.of(200L)));

        verify(permissionEnforcer).require("system:resource-group:assign");
        verify(roleMapper, org.mockito.Mockito.never()).add(any(Role.class));
    }

    @Test
    void customScopeRequiresDepartmentsAndOtherScopesRejectHiddenDepartmentIds() {
        when(currentPrincipalService.requireCurrent()).thenReturn(systemPrincipal());

        assertThrows(IllegalArgumentException.class, () -> service.saveRole(
                new RoleCommand(
                        null, "空自定义", "empty_custom", 10,
                        "CUSTOM", "NONE", 0, false),
                Set.of(),
                Set.of()));
        assertThrows(IllegalArgumentException.class, () -> service.saveRole(
                command(null, "ordinary_role"),
                Set.of(),
                Set.of(10L)));
    }

    @Test
    void deletingRoleRepairsLegacyPrimaryRoleBeforeDeletingLegacyRow() {
        when(roleMapper.selectById(7L)).thenReturn(role(7L, false));
        when(userMapper.findUserIdsByRoleId(7L)).thenReturn(List.of(12L));
        when(userMapper.findAllUserIdsByRoleId(7L)).thenReturn(List.of(12L, 13L));
        when(roleMapper.delete(7L)).thenReturn(1);
        when(userRoleMapper.findFirstRoleIdByUserId(12L)).thenReturn(3L);
        when(userRoleMapper.findFirstRoleIdByUserId(13L)).thenReturn(null);
        when(legacyRoleMapper.delete(7L)).thenReturn(1);

        service.delete(7L);

        verify(userRoleMapper).updateLegacyRoleId(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.anyString());
        verify(userRoleMapper).updateLegacyRoleId(
                org.mockito.ArgumentMatchers.eq(13L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyString());
        verify(legacyRoleMapper).delete(7L);
        verify(tokenVersionService).bumpUsers(List.of(12L), "ROLE_DELETED");
    }

    private RoleCommand command(Long id, String roleKey) {
        return new RoleCommand(id, "视频操作员", roleKey, 10, "NONE", "NONE", 0, false);
    }

    private Role role(long id, boolean system) {
        Role role = new Role();
        role.setId(id);
        role.setSystemRole(system ? 1 : 0);
        role.setName("旧名称");
        role.setRoleKey("old_role");
        role.setVideoScopeType("NONE");
        return role;
    }

    private AuthPrincipal systemPrincipal() {
        return new AuthPrincipal(
                PrincipalType.USER,
                1L,
                1L,
                "admin",
                null,
                Set.of(1L),
                0L,
                true,
                Set.of("system:role:add", "system:role:edit", "system:role:remove"));
    }
}
