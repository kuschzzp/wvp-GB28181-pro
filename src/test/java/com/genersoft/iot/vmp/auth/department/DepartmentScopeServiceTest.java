package com.genersoft.iot.vmp.auth.department;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.department.impl.DepartmentScopeServiceImpl;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.department.model.RoleDepartmentGrant;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.repository.AuthorizationAuditSink;
import com.genersoft.iot.vmp.auth.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentScopeServiceTest {

    private static final String USER_LIST = "system:user:list";

    @Mock private PermissionService permissionService;
    @Mock private RoleDepartmentMapper roleDepartmentMapper;
    @Mock private DepartmentMapper departmentMapper;
    @Mock private DepartmentTreeService departmentTreeService;
    @Mock private AuthorizationAuditSink auditSink;

    private DepartmentScopeService service;

    @BeforeEach
    void setUp() {
        service = service(AuthorizationMode.ENFORCE);
    }

    @Test
    void permissionScopeCannotBorrowAllFromRoleThatDidNotGrantThePermission() {
        AuthPrincipal principal = principal(false, Set.of(7L, 8L), 10L);
        when(permissionService.findGrantingRoleIds(12L, USER_LIST)).thenReturn(Set.of(7L));
        when(roleDepartmentMapper.findEnabledScopes(Set.of(7L)))
                .thenReturn(List.of(new RoleDepartmentGrant(7L, "DEPT")));
        when(departmentMapper.countEnabled(10L)).thenReturn(1);

        DepartmentScope scope = service.resolveDecisionScope(principal, USER_LIST);

        assertFalse(scope.all());
        assertFalse(scope.includeSelf());
        assertEquals(Set.of(10L), scope.deptIds());
        verify(roleDepartmentMapper, never()).findEnabledScopes(Set.of(8L));
    }

    @Test
    void mergesSelfAndOnlyExplicitEnabledCustomDepartments() {
        when(permissionService.findGrantingRoleIds(12L, USER_LIST)).thenReturn(Set.of(9L, 10L));
        when(roleDepartmentMapper.findEnabledScopes(Set.of(9L, 10L))).thenReturn(List.of(
                new RoleDepartmentGrant(9L, "SELF"),
                new RoleDepartmentGrant(10L, "CUSTOM")));
        when(roleDepartmentMapper.findEnabledCustomDepartmentIds(Set.of(10L)))
                .thenReturn(List.of(20L, 21L));

        DepartmentScope scope = service.resolveDecisionScope(
                principal(false, Set.of(9L, 10L), 10L), USER_LIST);

        assertEquals(Set.of(20L, 21L), scope.deptIds());
        assertTrue(scope.includeSelf());
        assertFalse(scope.all());
    }

    @Test
    void deptAndChildUsesEnabledClosureWhileDisabledOwnDepartmentFailsClosed() {
        when(permissionService.findGrantingRoleIds(12L, USER_LIST)).thenReturn(Set.of(11L));
        when(roleDepartmentMapper.findEnabledScopes(Set.of(11L)))
                .thenReturn(List.of(new RoleDepartmentGrant(11L, "DEPT_AND_CHILD")));
        when(departmentMapper.countEnabled(10L)).thenReturn(1);
        when(departmentTreeService.findEnabledDescendantIds(10L))
                .thenReturn(Set.of(10L, 20L));

        assertEquals(Set.of(10L, 20L), service.resolveDecisionScope(
                principal(false, Set.of(11L), 10L), USER_LIST).deptIds());

        when(departmentMapper.countEnabled(10L)).thenReturn(0);
        assertTrue(service.resolveDecisionScope(
                principal(false, Set.of(11L), 10L), USER_LIST).isNone());
    }

    @Test
    void allShortCircuitsAndNoneOrUnknownContributesNothing() {
        when(permissionService.findGrantingRoleIds(12L, USER_LIST)).thenReturn(Set.of(12L, 13L));
        when(roleDepartmentMapper.findEnabledScopes(Set.of(12L, 13L))).thenReturn(List.of(
                new RoleDepartmentGrant(12L, "NONE"),
                new RoleDepartmentGrant(13L, "ALL")));

        assertTrue(service.resolveDecisionScope(
                principal(false, Set.of(12L, 13L), 10L), USER_LIST).all());
        verify(roleDepartmentMapper, never()).findEnabledCustomDepartmentIds(Set.of());

        when(permissionService.findGrantingRoleIds(12L, "system:dept:list"))
                .thenReturn(Set.of(14L));
        when(roleDepartmentMapper.findEnabledScopes(Set.of(14L)))
                .thenReturn(List.of(new RoleDepartmentGrant(14L, "BROKEN")));
        assertTrue(service.resolveDecisionScope(
                principal(false, Set.of(14L), 10L), "system:dept:list").isNone());
    }

    @Test
    void systemUserAlwaysGetsAllWithoutRoleScopeQueries() {
        assertTrue(service.resolveDecisionScope(
                principal(true, Set.of(1L), 1L), USER_LIST).all());

        verifyNoInteractions(permissionService, roleDepartmentMapper, departmentMapper,
                departmentTreeService, auditSink);
    }

    @Test
    void auditKeepsRealDecisionButExpandsEffectiveScopeAndRecordsDifference() {
        service = service(AuthorizationMode.AUDIT);
        when(permissionService.findGrantingRoleIds(12L, USER_LIST)).thenReturn(Set.of(9L));
        when(roleDepartmentMapper.findEnabledScopes(Set.of(9L)))
                .thenReturn(List.of(new RoleDepartmentGrant(9L, "SELF")));
        AuthPrincipal principal = principal(false, Set.of(9L), 10L);

        DepartmentScope decision = service.resolveDecisionScope(principal, USER_LIST);
        DepartmentScope effective = service.resolveEffectiveScope(principal, USER_LIST);

        assertTrue(decision.includeSelf());
        assertFalse(decision.all());
        assertTrue(effective.all());
        ArgumentCaptor<AuthzAuditRecord> captor = ArgumentCaptor.forClass(AuthzAuditRecord.class);
        verify(auditSink).record(captor.capture());
        assertEquals("DEPARTMENT_SCOPE_AUDIT_EXPANDED", captor.getValue().reasonCode());
        assertEquals(10L, captor.getValue().principalDeptId());
    }

    private DepartmentScopeService service(AuthorizationMode mode) {
        return new DepartmentScopeServiceImpl(
                permissionService,
                roleDepartmentMapper,
                departmentMapper,
                departmentTreeService,
                auditSink,
                new AuthorizationProperties(mode, false, Duration.ofMinutes(5)));
    }

    private AuthPrincipal principal(boolean systemUser, Set<Long> roleIds, Long deptId) {
        return new AuthPrincipal(
                PrincipalType.USER,
                12L,
                12L,
                "operator",
                deptId,
                roleIds,
                0L,
                systemUser,
                Set.of(USER_LIST));
    }
}
