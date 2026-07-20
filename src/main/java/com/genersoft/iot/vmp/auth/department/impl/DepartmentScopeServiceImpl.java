package com.genersoft.iot.vmp.auth.department.impl;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.department.DepartmentMapper;
import com.genersoft.iot.vmp.auth.department.DepartmentScopeService;
import com.genersoft.iot.vmp.auth.department.DepartmentTreeService;
import com.genersoft.iot.vmp.auth.department.RoleDepartmentMapper;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScopeType;
import com.genersoft.iot.vmp.auth.department.model.RoleDepartmentGrant;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.repository.AuthorizationAuditSink;
import com.genersoft.iot.vmp.auth.service.PermissionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
public class DepartmentScopeServiceImpl implements DepartmentScopeService {

    private final PermissionService permissionService;
    private final RoleDepartmentMapper roleDepartmentMapper;
    private final DepartmentMapper departmentMapper;
    private final DepartmentTreeService departmentTreeService;
    private final AuthorizationAuditSink auditSink;
    private final AuthorizationMode mode;

    public DepartmentScopeServiceImpl(
            PermissionService permissionService,
            RoleDepartmentMapper roleDepartmentMapper,
            DepartmentMapper departmentMapper,
            DepartmentTreeService departmentTreeService,
            AuthorizationAuditSink auditSink,
            AuthorizationProperties properties) {
        this.permissionService = permissionService;
        this.roleDepartmentMapper = roleDepartmentMapper;
        this.departmentMapper = departmentMapper;
        this.departmentTreeService = departmentTreeService;
        this.auditSink = auditSink;
        this.mode = properties.mode();
    }

    @Override
    public DepartmentScope resolveDecisionScope(AuthPrincipal principal, String permissionCode) {
        Objects.requireNonNull(principal, "principal must not be null");
        requirePermissionCode(permissionCode);
        long userId = principal.userId() == null ? 0L : principal.userId();
        if (principal.systemUser()) {
            return DepartmentScope.all(userId);
        }
        if (userId <= 0) {
            return DepartmentScope.none(userId);
        }

        Set<Long> grantingRoleIds = permissionService.findGrantingRoleIds(userId, permissionCode);
        if (grantingRoleIds == null || grantingRoleIds.isEmpty()) {
            return DepartmentScope.none(userId);
        }
        Set<Long> normalizedRoleIds = normalizeIds(grantingRoleIds);
        if (normalizedRoleIds.isEmpty()) {
            return DepartmentScope.none(userId);
        }

        List<RoleDepartmentGrant> grants = roleDepartmentMapper.findEnabledScopes(normalizedRoleIds);
        if (grants == null || grants.isEmpty()) {
            return DepartmentScope.none(userId);
        }
        LinkedHashSet<Long> deptIds = new LinkedHashSet<>();
        TreeSet<Long> customRoleIds = new TreeSet<>();
        boolean includeSelf = false;
        Long principalDeptId = principal.deptId();

        for (RoleDepartmentGrant grant : grants) {
            if (grant == null || !normalizedRoleIds.contains(grant.roleId())) {
                continue;
            }
            DepartmentScopeType scopeType = DepartmentScopeType.fromDatabaseValue(grant.scopeType());
            switch (scopeType) {
                case ALL -> {
                    return DepartmentScope.all(userId);
                }
                case CUSTOM -> customRoleIds.add(grant.roleId());
                case DEPT -> addEnabledOwnDepartment(principalDeptId, deptIds);
                case DEPT_AND_CHILD -> addEnabledOwnDepartmentAndChildren(principalDeptId, deptIds);
                case SELF -> includeSelf = true;
                case NONE -> {
                    // NONE deliberately contributes no departments.
                }
            }
        }

        if (!customRoleIds.isEmpty()) {
            List<Long> customIds = roleDepartmentMapper.findEnabledCustomDepartmentIds(customRoleIds);
            if (customIds != null) {
                customIds.stream()
                        .filter(Objects::nonNull)
                        .filter(id -> id > 0)
                        .forEach(deptIds::add);
            }
        }
        return new DepartmentScope(false, deptIds, includeSelf, userId);
    }

    @Override
    public DepartmentScope resolveEffectiveScope(AuthPrincipal principal, String permissionCode) {
        DepartmentScope decisionScope = resolveDecisionScope(principal, permissionCode);
        if (mode == AuthorizationMode.ENFORCE || decisionScope.all()) {
            return decisionScope;
        }
        if (mode == AuthorizationMode.AUDIT) {
            auditSink.record(new AuthzAuditRecord(
                    principal.type(),
                    principal.id(),
                    principal.deptId(),
                    permissionCode,
                    "DEPARTMENT_SCOPE",
                    null,
                    false,
                    "DEPARTMENT_SCOPE_AUDIT_EXPANDED",
                    null,
                    null,
                    LocalDateTime.now()));
        }
        return DepartmentScope.all(decisionScope.userId());
    }

    private void addEnabledOwnDepartment(Long deptId, Set<Long> result) {
        if (deptId != null && deptId > 0 && departmentMapper.countEnabled(deptId) > 0) {
            result.add(deptId);
        }
    }

    private void addEnabledOwnDepartmentAndChildren(Long deptId, Set<Long> result) {
        if (deptId == null || deptId <= 0 || departmentMapper.countEnabled(deptId) == 0) {
            return;
        }
        Set<Long> descendantIds = departmentTreeService.findEnabledDescendantIds(deptId);
        if (descendantIds != null && descendantIds.contains(deptId)) {
            result.addAll(descendantIds);
        }
    }

    private Set<Long> normalizeIds(Set<Long> ids) {
        TreeSet<Long> normalized = new TreeSet<>();
        ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .forEach(normalized::add);
        return Set.copyOf(normalized);
    }

    private void requirePermissionCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            throw new IllegalArgumentException("permissionCode must not be blank");
        }
    }
}
