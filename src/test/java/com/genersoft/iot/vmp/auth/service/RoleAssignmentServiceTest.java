package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.service.impl.RoleAssignmentServiceImpl;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.RoleMapper;
import com.genersoft.iot.vmp.storager.dao.UserRoleMapper;
import com.genersoft.iot.vmp.storager.dao.dto.Role;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceTest {

    @Mock private IUserService userService;
    @Mock private RoleMapper roleMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private PermissionRepository permissionRepository;
    @Mock private TokenVersionService tokenVersionService;

    private RoleAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new RoleAssignmentServiceImpl(
                userService, roleMapper, userRoleMapper, permissionRepository, tokenVersionService);
    }

    @Test
    void replacesDeduplicatedRolesAndBumpsTokenVersionExactlyOnce() {
        when(userService.getUserById(12L)).thenReturn(user(12L));
        when(permissionRepository.isSystemUser(1L)).thenReturn(true);
        when(permissionRepository.isSystemUser(12L)).thenReturn(false);
        when(roleMapper.findByIds(Set.of(3L, 4L))).thenReturn(List.of(role(3L, 0, false), role(4L, 0, false)));

        service.assignRoles(1L, 12L, new java.util.HashSet<>(List.of(4L, 3L, 4L)));

        verify(userRoleMapper).deleteByUserId(12L);
        verify(userRoleMapper).insertBatch(12L, new java.util.TreeSet<>(Set.of(3L, 4L)));
        verify(userRoleMapper).updateLegacyRoleId(org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(3L), anyString());
        verify(tokenVersionService).bumpUser(12L, "USER_ROLES_CHANGED");
    }

    @Test
    void rejectsDisabledRoleWithoutChangingRelations() {
        when(userService.getUserById(12L)).thenReturn(user(12L));
        when(permissionRepository.isSystemUser(1L)).thenReturn(true);
        when(permissionRepository.isSystemUser(12L)).thenReturn(false);
        when(roleMapper.findByIds(Set.of(3L))).thenReturn(List.of(role(3L, 1, false)));

        assertThrows(IllegalArgumentException.class,
                () -> service.assignRoles(1L, 12L, Set.of(3L)));

        verifyNoInteractions(userRoleMapper, tokenVersionService);
    }

    @Test
    void nonSystemOperatorCannotAssignSystemRoleOrModifySystemUser() {
        when(userService.getUserById(12L)).thenReturn(user(12L));
        when(permissionRepository.isSystemUser(9L)).thenReturn(false);
        when(permissionRepository.isSystemUser(12L)).thenReturn(false);
        when(roleMapper.findByIds(Set.of(1L))).thenReturn(List.of(role(1L, 0, true)));

        assertThrows(AccessDeniedException.class,
                () -> service.assignRoles(9L, 12L, Set.of(1L)));
        verify(userRoleMapper, never()).deleteByUserId(12L);
    }

    @Test
    void clearingRolesAlsoClearsLegacyCompatibilityRole() {
        when(userService.getUserById(12L)).thenReturn(user(12L));
        when(permissionRepository.isSystemUser(1L)).thenReturn(true);
        when(permissionRepository.isSystemUser(12L)).thenReturn(false);

        service.assignRoles(1L, 12L, Set.of());

        verify(userRoleMapper).deleteByUserId(12L);
        verify(userRoleMapper, never()).insertBatch(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyCollection());
        verify(userRoleMapper).updateLegacyRoleId(
                org.mockito.ArgumentMatchers.eq(12L), isNull(), anyString());
        verify(tokenVersionService).bumpUser(12L, "USER_ROLES_CHANGED");
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
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
