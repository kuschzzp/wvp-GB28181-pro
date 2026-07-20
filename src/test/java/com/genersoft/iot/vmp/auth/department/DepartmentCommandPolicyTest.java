package com.genersoft.iot.vmp.auth.department;

import com.genersoft.iot.vmp.auth.department.impl.DepartmentCommandPolicyImpl;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.RoleAssignmentService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.auth.user.dto.AdminUpdateUserRequest;
import com.genersoft.iot.vmp.auth.user.dto.ChangeUserStatusRequest;
import com.genersoft.iot.vmp.auth.user.dto.UpdateOwnProfileRequest;
import com.genersoft.iot.vmp.auth.user.dto.UserSummary;
import com.genersoft.iot.vmp.auth.user.impl.SystemUserServiceImpl;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.RoleMapper;
import com.genersoft.iot.vmp.storager.dao.UserMapper;
import com.genersoft.iot.vmp.storager.dao.UserRoleMapper;
import com.genersoft.iot.vmp.storager.dao.dto.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentCommandPolicyTest {

    @Mock private UserMapper userMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private DepartmentMapper departmentMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private CurrentPrincipalService currentPrincipalService;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private IUserService legacyUserService;
    @Mock private RoleAssignmentService roleAssignmentService;
    @Mock private TokenVersionService tokenVersionService;

    private DepartmentCommandPolicy policy;
    private SystemUserServiceImpl userService;

    @BeforeEach
    void setUp() {
        policy = new DepartmentCommandPolicyImpl(userMapper, departmentMapper, roleMapper);
        userService = new SystemUserServiceImpl(
                userMapper,
                userRoleMapper,
                policy,
                currentPrincipalService,
                permissionRepository,
                passwordEncoder,
                legacyUserService,
                roleAssignmentService,
                tokenVersionService);
    }

    @Test
    void outOfScopeObjectsAreHiddenAsNotFound() {
        DepartmentScope scope = scoped(10L, Set.of(100L));

        ControllerException userError = assertThrows(ControllerException.class,
                () -> policy.requireUserInScope(20L, scope));
        assertEquals(404, userError.getCode());

        ControllerException departmentError = assertThrows(ControllerException.class,
                () -> policy.requireDepartmentInScope(200L, scope));
        assertEquals(404, departmentError.getCode());
    }

    @Test
    void selfScopeDoesNotGrantDepartmentAdministration() {
        ControllerException error = assertThrows(ControllerException.class,
                () -> policy.requireDepartmentInScope(100L, DepartmentScope.self(10L)));

        assertEquals(404, error.getCode());
    }

    @Test
    void roleAssignmentRejectsMissingDisabledAndSystemRolesForOrdinaryOperator() {
        AuthPrincipal operator = principal(10L, 100L, false);
        when(roleMapper.findByIds(Set.of(3L))).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> policy.requireRoleAssignmentAllowed(operator, Set.of(3L)));

        when(roleMapper.findByIds(Set.of(4L))).thenReturn(List.of(role(4L, 1, false)));
        assertThrows(IllegalArgumentException.class,
                () -> policy.requireRoleAssignmentAllowed(operator, Set.of(4L)));

        when(roleMapper.findByIds(Set.of(1L))).thenReturn(List.of(role(1L, 0, true)));
        assertThrows(AccessDeniedException.class,
                () -> policy.requireRoleAssignmentAllowed(operator, Set.of(1L)));

        assertDoesNotThrow(() -> policy.requireRoleAssignmentAllowed(
                principal(1L, 1L, true), Set.of(1L)));
    }

    @Test
    void adminUpdateCannotMutateSelfOrProtectedSystemUser() {
        AuthPrincipal operator = principal(10L, 100L, false);
        when(currentPrincipalService.requireCurrent()).thenReturn(operator);
        when(userMapper.findVisibleScoped(10L, scoped(10L, Set.of(100L))))
                .thenReturn(summary(10L, 100L));

        assertThrows(AccessDeniedException.class, () -> userService.update(
                10L, updateRequest(100L), scoped(10L, Set.of(100L))));
        verify(userMapper, never()).updateAdminProfile(
                anyLong(), anyString(), anyLong(), anyLong(), anyString());

        when(userMapper.findVisibleScoped(20L, scoped(10L, Set.of(100L))))
                .thenReturn(summary(20L, 100L));
        when(permissionRepository.isSystemUser(20L)).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> userService.update(
                20L, updateRequest(100L), scoped(10L, Set.of(100L))));
        verifyNoInteractions(roleAssignmentService);
    }

    @Test
    void adminUpdateCannotMoveUserOutsideScope() {
        DepartmentScope scope = scoped(10L, Set.of(100L));
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(10L, 100L, false));
        when(userMapper.findVisibleScoped(20L, scope)).thenReturn(summary(20L, 100L));
        ControllerException error = assertThrows(ControllerException.class,
                () -> userService.update(20L, updateRequest(200L), scope));

        assertEquals(404, error.getCode());
        verifyNoInteractions(roleAssignmentService);
    }

    @Test
    void validAdminUpdateChangesOnlyProfileFieldsAndInvalidatesTokenWhenDepartmentChanges() {
        DepartmentScope scope = scoped(10L, Set.of(100L));
        AuthPrincipal operator = principal(10L, 100L, false);
        AdminUpdateUserRequest request = updateRequest(100L);
        when(currentPrincipalService.requireCurrent()).thenReturn(operator);
        when(userMapper.findVisibleScoped(20L, scope)).thenReturn(summary(20L, 100L));
        when(departmentMapper.countEnabled(100L)).thenReturn(1);
        when(userMapper.selectById(20L)).thenReturn(user(20L, 99L, 0, false));
        when(userMapper.updateAdminProfile(
                eq(20L), eq("新昵称"), eq(100L), eq(10L), anyString()))
                .thenReturn(1);

        userService.update(20L, request, scope);

        verify(userMapper).updateAdminProfile(
                eq(20L), eq("新昵称"), eq(100L), eq(10L), anyString());
        verify(tokenVersionService).bumpUser(20L, "USER_DEPARTMENT_CHANGED");
        verifyNoInteractions(roleAssignmentService);
    }

    @Test
    void accountStatusChangeUsesDedicatedCommandAndInvalidatesExistingToken() {
        DepartmentScope scope = scoped(10L, Set.of(100L));
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(10L, 100L, false));
        when(userMapper.findVisibleScoped(20L, scope)).thenReturn(summary(20L, 100L));
        when(userMapper.selectById(20L)).thenReturn(user(20L, 100L, 0, false));
        when(userMapper.updateAccountStatus(
                eq(20L), eq(1), eq(true), eq(10L), anyString())).thenReturn(1);

        userService.changeStatus(20L, new ChangeUserStatusRequest(1, true), scope);

        verify(userMapper).updateAccountStatus(
                eq(20L), eq(1), eq(true), eq(10L), anyString());
        verify(tokenVersionService).bumpUser(20L, "USER_STATUS_CHANGED");
    }

    @Test
    void selfProfileDtoCanOnlyUpdateNickname() {
        when(currentPrincipalService.requireCurrent())
                .thenReturn(principal(10L, 100L, false));
        when(userMapper.updateOwnProfile(
                eq(10L), eq("本人昵称"), eq(10L), anyString())).thenReturn(1);

        userService.updateOwnProfile(new UpdateOwnProfileRequest(" 本人昵称 "));

        verify(userMapper).updateOwnProfile(
                eq(10L), eq("本人昵称"), eq(10L), anyString());
        verifyNoInteractions(roleAssignmentService, departmentMapper, roleMapper);
    }

    @Test
    void userCannotDeleteSelfEvenWhenSelfIsVisible() {
        DepartmentScope scope = DepartmentScope.self(10L);
        when(currentPrincipalService.requireCurrent())
                .thenReturn(principal(10L, 100L, false));
        when(userMapper.findVisibleScoped(10L, scope)).thenReturn(summary(10L, 100L));

        assertThrows(AccessDeniedException.class, () -> userService.delete(10L, scope));

        verify(legacyUserService, never()).softDelete(anyLong(), anyLong());
    }

    private DepartmentScope scoped(long userId, Set<Long> deptIds) {
        return new DepartmentScope(false, deptIds, false, userId);
    }

    private AuthPrincipal principal(long userId, long deptId, boolean systemUser) {
        return new AuthPrincipal(
                PrincipalType.USER,
                userId,
                userId,
                "operator-" + userId,
                deptId,
                Set.of(2L),
                0L,
                systemUser,
                Set.of());
    }

    private UserSummary summary(long userId, long deptId) {
        UserSummary summary = new UserSummary();
        summary.setId(userId);
        summary.setDeptId(deptId);
        return summary;
    }

    private AdminUpdateUserRequest updateRequest(long deptId) {
        return new AdminUpdateUserRequest(" 新昵称 ", deptId);
    }

    private com.genersoft.iot.vmp.storager.dao.dto.User user(
            long userId, long deptId, int status, boolean locked) {
        com.genersoft.iot.vmp.storager.dao.dto.User user =
                new com.genersoft.iot.vmp.storager.dao.dto.User();
        user.setId(userId);
        user.setDeptId(deptId);
        user.setStatus(status);
        user.setLocked(locked);
        return user;
    }

    private Role role(long id, int status, boolean system) {
        Role role = new Role();
        role.setId(id);
        role.setStatus(status);
        role.setSystemRole(system ? 1 : 0);
        return role;
    }
}
