package com.genersoft.iot.vmp.auth.service.impl;

import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.service.RoleAssignmentService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.RoleMapper;
import com.genersoft.iot.vmp.storager.dao.UserRoleMapper;
import com.genersoft.iot.vmp.storager.dao.dto.Role;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import com.genersoft.iot.vmp.utils.DateUtil;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class RoleAssignmentServiceImpl implements RoleAssignmentService {

    private final IUserService userService;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionRepository permissionRepository;
    private final TokenVersionService tokenVersionService;

    public RoleAssignmentServiceImpl(
            IUserService userService,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            PermissionRepository permissionRepository,
            TokenVersionService tokenVersionService) {
        this.userService = userService;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionRepository = permissionRepository;
        this.tokenVersionService = tokenVersionService;
    }

    @Override
    @Transactional
    public void assignRoles(long operatorId, long userId, Set<Long> roleIds) {
        TreeSet<Long> normalizedRoleIds = normalize(roleIds);
        User target = userService.getUserById(userId);
        if (target == null || target.isDeleted()) {
            throw new IllegalArgumentException("用户不存在");
        }

        boolean operatorIsSystemUser = permissionRepository.isSystemUser(operatorId);
        if (permissionRepository.isSystemUser(userId) && !operatorIsSystemUser) {
            throw new AccessDeniedException("不能修改系统管理员的角色");
        }

        List<Role> roles = normalizedRoleIds.isEmpty()
                ? List.of()
                : roleMapper.findByIds(normalizedRoleIds);
        if (roles.size() != normalizedRoleIds.size()
                || roles.stream().anyMatch(role -> role.getStatus() != 0)) {
            throw new IllegalArgumentException("角色不存在或已停用");
        }
        if (!operatorIsSystemUser && roles.stream().anyMatch(Role::isSystem)) {
            throw new AccessDeniedException("普通管理员不能分配系统角色");
        }

        userRoleMapper.deleteByUserId(userId);
        if (!normalizedRoleIds.isEmpty()) {
            userRoleMapper.insertBatch(userId, normalizedRoleIds);
        }
        userRoleMapper.updateLegacyRoleId(
                userId,
                normalizedRoleIds.isEmpty() ? null : normalizedRoleIds.first(),
                DateUtil.getNow());
        tokenVersionService.bumpUser(userId, "USER_ROLES_CHANGED");
    }

    private TreeSet<Long> normalize(Set<Long> roleIds) {
        TreeSet<Long> normalized = new TreeSet<>();
        if (roleIds != null) {
            roleIds.stream()
                    .filter(roleId -> roleId != null && roleId > 0)
                    .forEach(normalized::add);
        }
        return normalized;
    }
}
