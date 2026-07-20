package com.genersoft.iot.vmp.auth.apikey;

import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeySummary;
import com.genersoft.iot.vmp.auth.department.DepartmentScopeService;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.service.IUserApiKeyService;
import com.genersoft.iot.vmp.vmanager.user.UserApiKeyController;
import com.github.pagehelper.PageInfo;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserApiKeyControllerSecurityTest {

    @Test
    void everyApiKeyManagementEndpointHasFunctionalPermission() {
        Set<String> endpointMethods = Set.of(
                "create", "addLegacy", "page", "enable", "disable", "rotate", "remark",
                "scopeOptions", "updateScope", "delete");
        Arrays.stream(UserApiKeyController.class.getDeclaredMethods())
                .filter(method -> endpointMethods.contains(method.getName()))
                .forEach(method -> assertNotNull(
                        method.getAnnotation(PreAuthorize.class), method::toGenericString));
    }

    @Test
    void anotherOwnersQueryUsesTheListPermissionsDecisionScope() {
        IUserApiKeyService apiKeyService = mock(IUserApiKeyService.class);
        CurrentPrincipalService currentPrincipalService = mock(CurrentPrincipalService.class);
        DepartmentScopeService departmentScopeService = mock(DepartmentScopeService.class);
        UserApiKeyController controller = new UserApiKeyController(
                apiKeyService, currentPrincipalService, departmentScopeService);
        AuthPrincipal principal = new AuthPrincipal(
                PrincipalType.USER,
                12L,
                12L,
                "department-admin",
                100L,
                Set.of(3L),
                7L,
                false,
                Set.of("system:apikey:list", "system:apikey:manageOthers"));
        DepartmentScope scope = new DepartmentScope(false, Set.of(100L, 101L), false, 12L);
        PageInfo<ApiKeySummary> expected = new PageInfo<>(List.of());
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(departmentScopeService.resolveDecisionScope(principal, "system:apikey:list"))
                .thenReturn(scope);
        when(apiKeyService.pageOwnKeys(
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(scope)))
                .thenReturn(expected);

        PageInfo<ApiKeySummary> actual = controller.page(1, 20, 20L);

        assertSame(expected, actual);
        verify(apiKeyService).pageOwnKeys(
                org.mockito.ArgumentMatchers.eq(20L),
                argThat(query -> query.page() == 1 && query.count() == 20),
                org.mockito.ArgumentMatchers.eq(scope));
        verify(departmentScopeService)
                .resolveDecisionScope(principal, "system:apikey:list");
    }
}
