package com.genersoft.iot.vmp.auth.department;

import com.genersoft.iot.vmp.auth.apikey.ApiKeySecretHasher;
import com.genersoft.iot.vmp.auth.apikey.ApiKeyScopeService;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyQuery;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.service.impl.UserApiKeyServiceImpl;
import com.genersoft.iot.vmp.storager.dao.UserApiKeyMapper;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentManagedResourceTest {

    @Mock private UserApiKeyMapper mapper;
    @Mock private IUserService userService;
    @Mock private ApiKeySecretHasher secretHasher;
    @Mock private CurrentPrincipalService currentPrincipalService;
    @Mock private DepartmentCommandPolicy commandPolicy;
    @Mock private ApiKeyScopeService scopeService;

    private UserApiKeyServiceImpl service;
    private DepartmentScope departmentScope;

    @BeforeEach
    void setUp() {
        service = new UserApiKeyServiceImpl(
                mapper, userService, secretHasher, currentPrincipalService, commandPolicy,
                scopeService);
        departmentScope = new DepartmentScope(false, Set.of(100L, 101L), false, 12L);
    }

    @Test
    void departmentAdministratorCanListKeysForAnInScopeUser() {
        when(currentPrincipalService.requireCurrent()).thenReturn(
                principal(Set.of("system:apikey:list", "system:apikey:manageOthers")));
        when(mapper.findByOwnerId(20L)).thenReturn(List.of());

        var result = service.pageOwnKeys(20L, new ApiKeyQuery(1, 20), departmentScope);

        assertEquals(0, result.getList().size());
        verify(commandPolicy).requireUserInScope(20L, departmentScope);
        verify(mapper).findByOwnerId(20L);
    }

    @Test
    void replacingOwnerIdWithAnOutOfScopeUserReturnsNotFound() {
        when(currentPrincipalService.requireCurrent()).thenReturn(
                principal(Set.of("system:apikey:list", "system:apikey:manageOthers")));
        doThrow(new ControllerException(404, "用户不存在"))
                .when(commandPolicy).requireUserInScope(20L, departmentScope);

        ControllerException exception = assertThrows(ControllerException.class,
                () -> service.pageOwnKeys(20L, new ApiKeyQuery(1, 20), departmentScope));

        assertEquals(404, exception.getCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void matchingDepartmentScopeDoesNotReplaceManageOthersPermission() {
        when(currentPrincipalService.requireCurrent()).thenReturn(
                principal(Set.of("system:apikey:list")));

        assertThrows(AccessDeniedException.class,
                () -> service.pageOwnKeys(20L, new ApiKeyQuery(1, 20), departmentScope));

        verify(commandPolicy).requireUserInScope(20L, departmentScope);
        verifyNoInteractions(mapper);
    }

    @Test
    void ownKeysDoNotRequireDepartmentAdministrationScope() {
        when(currentPrincipalService.requireCurrent()).thenReturn(
                principal(Set.of("system:apikey:list")));
        when(mapper.findByOwnerId(12L)).thenReturn(List.of());

        service.pageOwnKeys(12L, new ApiKeyQuery(1, 20), DepartmentScope.self(12L));

        verifyNoInteractions(commandPolicy);
        verify(mapper).findByOwnerId(12L);
    }

    private AuthPrincipal principal(Set<String> permissions) {
        return new AuthPrincipal(
                PrincipalType.USER,
                12L,
                12L,
                "department-admin",
                100L,
                Set.of(3L),
                7L,
                false,
                permissions);
    }
}
