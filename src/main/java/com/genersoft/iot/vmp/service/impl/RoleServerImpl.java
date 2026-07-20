package com.genersoft.iot.vmp.service.impl;

import com.genersoft.iot.vmp.auth.department.DepartmentMapper;
import com.genersoft.iot.vmp.auth.department.RoleDepartmentMapper;
import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.dto.RoleCommand;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.authorization.resourcegroup.ResourceGroupService;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeType;
import com.genersoft.iot.vmp.service.IRoleService;
import com.genersoft.iot.vmp.storager.dao.LegacyRoleMapper;
import com.genersoft.iot.vmp.storager.dao.MenuMapper;
import com.genersoft.iot.vmp.storager.dao.RoleMapper;
import com.genersoft.iot.vmp.storager.dao.UserMapper;
import com.genersoft.iot.vmp.storager.dao.UserRoleMapper;
import com.genersoft.iot.vmp.storager.dao.dto.LegacyRole;
import com.genersoft.iot.vmp.storager.dao.dto.Role;
import com.genersoft.iot.vmp.utils.DateUtil;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

@Service
public class RoleServerImpl implements IRoleService {

    private static final Pattern ROLE_KEY = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    private static final Set<String> DEPT_SCOPES = Set.of(
            "ALL", "CUSTOM", "DEPT", "DEPT_AND_CHILD", "SELF", "NONE");
    private static final Set<String> VIDEO_SCOPES = Set.of("ALL", "CUSTOM", "NONE");
    private static final String RESOURCE_GROUP_ASSIGN_PERMISSION =
            "system:resource-group:assign";

    private final RoleMapper roleMapper;
    private final LegacyRoleMapper legacyRoleMapper;
    private final MenuMapper menuMapper;
    private final RoleDepartmentMapper roleDepartmentMapper;
    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final TokenVersionService tokenVersionService;
    private final CurrentPrincipalService currentPrincipalService;
    private final PermissionEnforcer permissionEnforcer;
    private final ResourceGroupService resourceGroupService;
    private final boolean legacyRoleDualWriteEnabled;

    public RoleServerImpl(
            RoleMapper roleMapper,
            LegacyRoleMapper legacyRoleMapper,
            MenuMapper menuMapper,
            RoleDepartmentMapper roleDepartmentMapper,
            DepartmentMapper departmentMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            TokenVersionService tokenVersionService,
            CurrentPrincipalService currentPrincipalService,
            PermissionEnforcer permissionEnforcer,
            ResourceGroupService resourceGroupService,
            AuthorizationProperties authorizationProperties) {
        this.roleMapper = roleMapper;
        this.legacyRoleMapper = legacyRoleMapper;
        this.menuMapper = menuMapper;
        this.roleDepartmentMapper = roleDepartmentMapper;
        this.departmentMapper = departmentMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.tokenVersionService = tokenVersionService;
        this.currentPrincipalService = currentPrincipalService;
        this.permissionEnforcer = permissionEnforcer;
        this.resourceGroupService = resourceGroupService;
        this.legacyRoleDualWriteEnabled =
                authorizationProperties.legacyRoleDualWriteEnabled();
    }

    @Override
    public Role getRoleById(long id) {
        return roleMapper.selectById(id);
    }

    @Override
    @Transactional
    public void saveRole(
            RoleCommand command,
            Set<Long> menuIds,
            Set<Long> deptIds,
            Set<Long> resourceGroupIds) {
        validate(command);
        AuthPrincipal operator = currentPrincipalService.requireCurrent();
        TreeSet<Long> normalizedMenuIds = normalize(menuIds);
        TreeSet<Long> normalizedDeptIds = normalize(deptIds);
        TreeSet<Long> normalizedResourceGroupIds = normalize(resourceGroupIds);

        Role role;
        boolean updating = command.id() != null;
        if (updating) {
            role = roleMapper.selectByIdForUpdate(command.id());
            if (role == null) {
                throw new IllegalArgumentException("角色不存在");
            }
            if (role.isSystem() && !operator.systemUser()) {
                throw new AccessDeniedException("不能修改系统角色");
            }
        } else {
            role = new Role();
            role.setCreateBy(operator.userId());
            role.setCreateTime(LocalDateTime.now());
        }
        requireVideoScopeAssignmentPermission(command, role, updating, normalizedResourceGroupIds);
        if (!normalizedMenuIds.isEmpty()
                && menuMapper.countEnabledByIds(normalizedMenuIds) != normalizedMenuIds.size()) {
            throw new IllegalArgumentException("菜单不存在或已停用");
        }
        validateDepartmentGrants(command.deptDataScope(), normalizedDeptIds);

        if (command.systemRole() && !operator.systemUser()) {
            throw new AccessDeniedException("只有系统管理员可以创建系统角色");
        }
        apply(command, role, operator.userId(), updating);
        if (updating) {
            if (roleMapper.update(role) <= 0) {
                throw new IllegalStateException("角色更新失败");
            }
            if (legacyRoleDualWriteEnabled
                    && legacyRoleMapper.update(toLegacyRole(role)) <= 0) {
                throw new IllegalStateException("旧角色主表双写更新失败");
            }
        } else {
            if (legacyRoleDualWriteEnabled) {
                LegacyRole legacyRole = toLegacyRole(role);
                if (legacyRoleMapper.add(legacyRole) <= 0 || legacyRole.getId() <= 0) {
                    throw new IllegalStateException("旧角色主表写入失败");
                }
                role.setId(legacyRole.getId());
            }
            if (roleMapper.add(role) <= 0 || role.getId() <= 0) {
                throw new IllegalStateException("角色创建失败");
            }
        }

        menuMapper.deleteRoleMenus(role.getId());
        if (!normalizedMenuIds.isEmpty()) {
            menuMapper.insertRoleMenus(role.getId(), normalizedMenuIds);
        }
        roleDepartmentMapper.deleteByRoleId(role.getId());
        if ("CUSTOM".equals(command.deptDataScope())) {
            roleDepartmentMapper.insertBatch(role.getId(), normalizedDeptIds);
        }
        resourceGroupService.replaceRoleGroupsForRoleUpdate(
                role.getId(),
                VideoScopeType.fromDatabaseValue(command.videoScopeType()),
                normalizedResourceGroupIds,
                operator.userId());
        if (updating) {
            tokenVersionService.bumpUsersByRoleId(role.getId(), "ROLE_PERMISSIONS_CHANGED");
        }
    }

    @Override
    public Set<Long> getMenuIds(long roleId) {
        if (roleMapper.selectById(roleId) == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        return Set.copyOf(menuMapper.findMenuIdsByRoleId(roleId));
    }

    @Override
    @Transactional
    public void changeStatus(long id, int status) {
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("角色状态不合法");
        }
        AuthPrincipal operator = currentPrincipalService.requireCurrent();
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        if (role.isSystem()) {
            throw new AccessDeniedException("系统角色状态不能修改");
        }
        if (role.getStatus() == status) {
            return;
        }
        if (roleMapper.updateStatus(id, status, operator.userId(), LocalDateTime.now()) != 1) {
            throw new IllegalStateException("角色状态更新失败");
        }
        tokenVersionService.bumpUsersByRoleId(id, "ROLE_STATUS_CHANGED");
    }

    @Override
    public Set<Long> getDepartmentIds(long roleId) {
        if (roleMapper.selectById(roleId) == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        return Set.copyOf(roleDepartmentMapper.findDepartmentIdsByRoleId(roleId));
    }

    @Override
    public Set<Long> getResourceGroupIds(long roleId) {
        if (roleMapper.selectById(roleId) == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        return resourceGroupService.findRoleGroupIds(roleId);
    }

    @Override
    @Transactional
    public void delete(long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        if (role.isSystem()) {
            throw new AccessDeniedException("系统角色不能删除");
        }
        List<Long> activeAffectedUsers = userMapper.findUserIdsByRoleId(id);
        List<Long> compatibilityUsers = userMapper.findAllUserIdsByRoleId(id);
        if (roleMapper.delete(id) <= 0) {
            throw new IllegalStateException("角色删除失败");
        }
        String updateTime = DateUtil.getNow();
        for (Long userId : compatibilityUsers) {
            userRoleMapper.updateLegacyRoleId(
                    userId,
                    userRoleMapper.findFirstRoleIdByUserId(userId),
                    updateTime);
        }
        if (legacyRoleDualWriteEnabled && legacyRoleMapper.delete(id) <= 0) {
            throw new IllegalStateException("旧角色主表删除失败");
        }
        tokenVersionService.bumpUsers(activeAffectedUsers, "ROLE_DELETED");
    }

    @Override
    public List<Role> getAll() {
        return roleMapper.selectAll();
    }

    private void apply(RoleCommand command, Role role, Long operatorId, boolean updating) {
        role.setName(command.name().trim());
        role.setRoleKey(command.roleKey().trim());
        role.setRoleSort(command.roleSort());
        role.setDeptDataScope(command.deptDataScope());
        role.setVideoScopeType(command.videoScopeType());
        if (!updating) {
            role.setStatus(command.status());
            role.setSystemRole(command.systemRole() ? 1 : 0);
        }
        role.setUpdateBy(operatorId);
        role.setUpdateTime(LocalDateTime.now());
    }

    private void validate(RoleCommand command) {
        if (command == null || command.name() == null || command.name().isBlank()
                || command.name().length() > 64) {
            throw new IllegalArgumentException("角色名称不合法");
        }
        if (command.roleKey() == null || !ROLE_KEY.matcher(command.roleKey()).matches()) {
            throw new IllegalArgumentException("角色标识不合法");
        }
        if (!DEPT_SCOPES.contains(command.deptDataScope())) {
            throw new IllegalArgumentException("部门数据范围不合法");
        }
        if (!VIDEO_SCOPES.contains(command.videoScopeType())) {
            throw new IllegalArgumentException("视频资源范围不合法");
        }
        if (command.status() != 0 && command.status() != 1) {
            throw new IllegalArgumentException("角色状态不合法");
        }
    }

    private TreeSet<Long> normalize(Set<Long> ids) {
        TreeSet<Long> result = new TreeSet<>();
        if (ids != null) {
            ids.stream().filter(id -> id != null && id > 0).forEach(result::add);
        }
        return result;
    }

    private void requireVideoScopeAssignmentPermission(
            RoleCommand command,
            Role currentRole,
            boolean updating,
            Set<Long> requestedResourceGroupIds) {
        if (!videoScopeChanged(command, currentRole, updating, requestedResourceGroupIds)) {
            return;
        }
        if (!permissionEnforcer.require(RESOURCE_GROUP_ASSIGN_PERMISSION)) {
            throw new AccessDeniedException("缺少视频资源组分配权限");
        }
    }

    private boolean videoScopeChanged(
            RoleCommand command,
            Role currentRole,
            boolean updating,
            Set<Long> requestedResourceGroupIds) {
        if (!updating) {
            return !"NONE".equals(command.videoScopeType());
        }
        if (!command.videoScopeType().equals(currentRole.getVideoScopeType())) {
            return true;
        }
        if (!"CUSTOM".equals(command.videoScopeType())) {
            return false;
        }
        Set<Long> currentResourceGroupIds =
                new TreeSet<>(resourceGroupService.findRoleGroupIds(currentRole.getId()));
        return !currentResourceGroupIds.equals(requestedResourceGroupIds);
    }

    private void validateDepartmentGrants(String scopeType, TreeSet<Long> deptIds) {
        if ("CUSTOM".equals(scopeType)) {
            if (deptIds.isEmpty()) {
                throw new IllegalArgumentException("CUSTOM 部门范围至少选择一个部门");
            }
            if (departmentMapper.countEnabledByIds(deptIds) != deptIds.size()) {
                throw new IllegalArgumentException("授权部门不存在或已停用");
            }
            return;
        }
        if (!deptIds.isEmpty()) {
            throw new IllegalArgumentException("非 CUSTOM 部门范围不能携带部门授权");
        }
    }

    private LegacyRole toLegacyRole(Role role) {
        LegacyRole legacyRole = new LegacyRole();
        legacyRole.setId(role.getId());
        legacyRole.setName(role.getName());
        legacyRole.setAuthority(role.getRoleKey());
        legacyRole.setCreateTime(role.getCreateTime() == null
                ? DateUtil.getNow()
                : DateUtil.formatter.format(role.getCreateTime()));
        legacyRole.setUpdateTime(role.getUpdateTime() == null
                ? DateUtil.getNow()
                : DateUtil.formatter.format(role.getUpdateTime()));
        return legacyRole;
    }
}
