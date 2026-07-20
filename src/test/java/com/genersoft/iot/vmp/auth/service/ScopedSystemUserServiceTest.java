package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.department.DepartmentCommandPolicy;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.user.impl.SystemUserServiceImpl;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.UserMapper;
import com.genersoft.iot.vmp.storager.dao.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScopedSystemUserServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private DepartmentCommandPolicy commandPolicy;
    @Mock private CurrentPrincipalService currentPrincipalService;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private IUserService legacyUserService;
    @Mock private RoleAssignmentService roleAssignmentService;
    @Mock private TokenVersionService tokenVersionService;

    private SystemUserServiceImpl service;
    private DepartmentScope scope;

    @BeforeEach
    void setUp() {
        service = new SystemUserServiceImpl(
                userMapper,
                userRoleMapper,
                commandPolicy,
                currentPrincipalService,
                permissionRepository,
                passwordEncoder,
                legacyUserService,
                roleAssignmentService,
                tokenVersionService);
        scope = new DepartmentScope(false, Set.of(100L, 101L), false, 10L);
        when(currentPrincipalService.requireCurrent()).thenReturn(operator());
    }

    @Test
    void changingAnotherUsersPushKeyRequiresDepartmentScope() {
        when(legacyUserService.changePushKey(20L, "rotated-key")).thenReturn(1);

        service.changePushKey(20L, "  rotated-key  ", scope);

        verify(commandPolicy).requireUserInScope(20L, scope);
        verify(legacyUserService).changePushKey(20L, "rotated-key");
    }

    @Test
    void outOfScopePushKeyMutationNeverReachesLegacyWritePath() {
        doThrow(new AccessDeniedException("用户不在管理范围内"))
                .when(commandPolicy).requireUserInScope(20L, scope);

        assertThrows(AccessDeniedException.class,
                () -> service.changePushKey(20L, "rotated-key", scope));

        verifyNoInteractions(legacyUserService);
    }

    private AuthPrincipal operator() {
        return new AuthPrincipal(
                PrincipalType.USER,
                10L,
                10L,
                "user-admin",
                100L,
                Set.of(3L),
                0L,
                false,
                Set.of("system:user:edit"));
    }
}
