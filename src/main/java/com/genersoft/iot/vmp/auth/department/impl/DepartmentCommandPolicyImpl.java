package com.genersoft.iot.vmp.auth.department.impl;

import com.genersoft.iot.vmp.auth.department.DepartmentCommandPolicy;
import com.genersoft.iot.vmp.auth.department.DepartmentMapper;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.storager.dao.RoleMapper;
import com.genersoft.iot.vmp.storager.dao.UserMapper;
import com.genersoft.iot.vmp.storager.dao.dto.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
public class DepartmentCommandPolicyImpl implements DepartmentCommandPolicy {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final RoleMapper roleMapper;

    public DepartmentCommandPolicyImpl(
            UserMapper userMapper,
            DepartmentMapper departmentMapper,
            RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public void requireUserInScope(long targetUserId, DepartmentScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        if (targetUserId <= 0 || userMapper.findVisibleScoped(targetUserId, scope) == null) {
            throw notFound("用户不存在");
        }
    }

    @Override
    public void requireDepartmentInScope(long targetDeptId, DepartmentScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        boolean inScope = scope.all() || scope.deptIds().contains(targetDeptId);
        if (targetDeptId <= 0 || !inScope || departmentMapper.countEnabled(targetDeptId) == 0) {
            throw notFound("部门不存在");
        }
    }

    @Override
    public void requireRoleAssignmentAllowed(AuthPrincipal operator, Set<Long> roleIds) {
        Objects.requireNonNull(operator, "operator must not be null");
        TreeSet<Long> normalizedRoleIds = normalizeRequiredRoleIds(roleIds);
        List<Role> roles = roleMapper.findByIds(normalizedRoleIds);
        if (roles == null
                || roles.size() != normalizedRoleIds.size()
                || roles.stream().anyMatch(role -> role == null || role.getStatus() != 0)) {
            throw new IllegalArgumentException("角色不存在或已停用");
        }
        if (!operator.systemUser() && roles.stream().anyMatch(Role::isSystem)) {
            throw new AccessDeniedException("普通管理员不能分配系统角色");
        }
    }

    private TreeSet<Long> normalizeRequiredRoleIds(Set<Long> roleIds) {
        TreeSet<Long> normalized = new TreeSet<>();
        if (roleIds != null) {
            roleIds.stream()
                    .filter(Objects::nonNull)
                    .filter(roleId -> roleId > 0)
                    .forEach(normalized::add);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("至少分配一个角色");
        }
        return normalized;
    }

    private ControllerException notFound(String message) {
        return new ControllerException(404, message);
    }
}
