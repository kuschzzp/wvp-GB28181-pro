package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.service.impl.SystemUserServiceImpl;
import com.genersoft.iot.vmp.service.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemUserServiceTest {

    @Mock private IUserService userService;
    @Mock private RoleAssignmentService roleAssignmentService;
    @Mock private CurrentPrincipalService currentPrincipalService;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private SystemUserService service;

    @BeforeEach
    void setUp() {
        service = new SystemUserServiceImpl(
                userService,
                roleAssignmentService,
                currentPrincipalService,
                permissionRepository,
                passwordEncoder);
    }

    @Test
    void ordinaryAdministratorCannotResetPasswordOrPushKeyOfSystemUser() {
        when(currentPrincipalService.requireCurrent()).thenReturn(operator(false));
        when(permissionRepository.isSystemUser(1L)).thenReturn(true);

        assertThrows(AccessDeniedException.class,
                () -> service.resetPassword(1L, "new-password"));
        assertThrows(AccessDeniedException.class,
                () -> service.changePushKey(1L, "new-push-key"));

        verifyNoInteractions(userService, passwordEncoder);
    }

    private AuthPrincipal operator(boolean systemUser) {
        return new AuthPrincipal(
                PrincipalType.USER,
                9L,
                9L,
                "operator",
                null,
                Set.of(3L),
                7L,
                systemUser,
                Set.of("system:user:resetPwd", "system:user:edit"));
    }
}
