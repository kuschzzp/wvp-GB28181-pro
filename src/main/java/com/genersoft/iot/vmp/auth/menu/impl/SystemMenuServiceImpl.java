package com.genersoft.iot.vmp.auth.menu.impl;

import com.genersoft.iot.vmp.auth.menu.SystemMenuService;
import com.genersoft.iot.vmp.auth.menu.MenuComponentRegistry;
import com.genersoft.iot.vmp.auth.menu.dto.MenuRequest;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.storager.dao.MenuMapper;
import com.genersoft.iot.vmp.storager.dao.dto.Menu;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class SystemMenuServiceImpl implements SystemMenuService {

    private static final Set<String> MENU_TYPES = Set.of("M", "C", "F");
    private static final int MAX_PARENT_DEPTH = 1024;

    private final MenuMapper menuMapper;
    private final CurrentPrincipalService currentPrincipalService;
    private final TokenVersionService tokenVersionService;
    private final MenuComponentRegistry componentRegistry;

    public SystemMenuServiceImpl(
            MenuMapper menuMapper,
            CurrentPrincipalService currentPrincipalService,
            TokenVersionService tokenVersionService,
            MenuComponentRegistry componentRegistry) {
        this.menuMapper = menuMapper;
        this.currentPrincipalService = currentPrincipalService;
        this.tokenVersionService = tokenVersionService;
        this.componentRegistry = componentRegistry;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Menu> list() {
        return List.copyOf(menuMapper.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public Menu detail(long id) {
        Menu menu = id > 0 ? menuMapper.findById(id) : null;
        if (menu == null) {
            throw new ControllerException(404, "菜单不存在");
        }
        return menu;
    }

    @Override
    @Transactional
    public long create(MenuRequest request) {
        AuthPrincipal operator = requireOperator();
        Menu menu = normalizeAndValidate(request);
        if (menu.isSystemMenu() && !operator.systemUser()) {
            throw new AccessDeniedException("只有系统管理员可以创建系统菜单");
        }
        validateParent(menu, null);
        validateUniquePermission(menu.getPerms(), null);
        if (menuMapper.insert(menu, operator.userId(), LocalDateTime.now()) != 1
                || menu.getId() <= 0) {
            throw new IllegalStateException("菜单创建失败");
        }
        return menu.getId();
    }

    @Override
    @Transactional
    public void update(long id, MenuRequest request) {
        if (id <= 0) {
            throw new IllegalArgumentException("菜单 ID 非法");
        }
        AuthPrincipal operator = requireOperator();
        Menu current = menuMapper.findByIdForUpdate(id);
        if (current == null) {
            throw new ControllerException(404, "菜单不存在");
        }
        if (current.isSystemMenu() && !operator.systemUser()) {
            throw new AccessDeniedException("不能修改系统菜单");
        }
        Menu menu = normalizeAndValidate(request);
        if (menu.isSystemMenu() != current.isSystemMenu()) {
            throw new IllegalArgumentException("系统菜单标识不能修改");
        }
        if (current.isSystemMenu()) {
            validateSystemMenuUpdate(current, menu);
        }
        menu.setId(id);
        validateParent(menu, id);
        validateUniquePermission(menu.getPerms(), id);
        List<Long> affectedUserIds = menuMapper.findUserIdsByMenuId(id);
        if (menuMapper.update(menu, operator.userId(), LocalDateTime.now()) != 1) {
            throw new IllegalStateException("菜单更新失败");
        }
        tokenVersionService.bumpUsers(affectedUserIds, "MENU_CHANGED");
    }

    @Override
    @Transactional
    public void delete(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("菜单 ID 非法");
        }
        requireOperator();
        Menu menu = menuMapper.findByIdForUpdate(id);
        if (menu == null) {
            throw new ControllerException(404, "菜单不存在");
        }
        if (menu.isSystemMenu()) {
            throw new AccessDeniedException("系统菜单不能删除");
        }
        if (menuMapper.countChildren(id) > 0) {
            throw new IllegalStateException("菜单存在子菜单，不能删除");
        }
        List<Long> affectedUserIds = menuMapper.findUserIdsByMenuId(id);
        menuMapper.deleteRoleMenusByMenuId(id);
        if (menuMapper.deleteById(id) != 1) {
            throw new IllegalStateException("菜单删除失败");
        }
        tokenVersionService.bumpUsers(affectedUserIds, "MENU_DELETED");
    }

    private Menu normalizeAndValidate(MenuRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String menuName = required(request.menuName(), "菜单名称", 64);
        String menuType = required(request.menuType(), "菜单类型", 1);
        if (!MENU_TYPES.contains(menuType)) {
            throw new IllegalArgumentException("菜单类型非法");
        }
        if (request.parentId() < 0) {
            throw new IllegalArgumentException("上级菜单非法");
        }
        if ((request.visible() != 0 && request.visible() != 1)
                || (request.status() != 0 && request.status() != 1)) {
            throw new IllegalArgumentException("菜单显示或状态值非法");
        }

        String routeName = optional(request.routeName(), 64, "路由名称");
        String path = optional(request.path(), 255, "路由地址");
        String component = optional(request.component(), 255, "组件路径");
        String permission = optional(request.perms(), 128, "权限标识");
        String icon = optional(request.icon(), 100, "菜单图标");
        if ("F".equals(menuType)) {
            if (request.parentId() == 0 || permission == null) {
                throw new IllegalArgumentException("按钮权限必须指定上级菜单和权限标识");
            }
        } else if (routeName == null || path == null || component == null) {
            throw new IllegalArgumentException("目录和菜单必须填写路由名称、地址与组件路径");
        }
        if (!"F".equals(menuType)) {
            componentRegistry.requireSupported(component);
        }
        if (request.external()
                && (path == null
                || !(path.startsWith("https://") || path.startsWith("http://")))) {
            throw new IllegalArgumentException("外链菜单必须填写 HTTP(S) 地址");
        }

        Menu menu = new Menu();
        menu.setParentId(request.parentId());
        menu.setMenuName(menuName);
        menu.setMenuType(menuType);
        menu.setRouteName(routeName);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setPerms(permission);
        menu.setIcon(icon);
        menu.setOrderNum(request.orderNum());
        menu.setVisible(request.visible());
        menu.setStatus(request.status());
        menu.setCacheable(request.cacheable());
        menu.setExternal(request.external());
        menu.setSystemMenu(request.systemMenu());
        return menu;
    }

    private void validateSystemMenuUpdate(Menu current, Menu requested) {
        boolean immutableFieldsChanged = current.getParentId() != requested.getParentId()
                || !Objects.equals(current.getMenuName(), requested.getMenuName())
                || !Objects.equals(current.getMenuType(), requested.getMenuType())
                || !Objects.equals(current.getRouteName(), requested.getRouteName())
                || !Objects.equals(current.getPath(), requested.getPath())
                || !Objects.equals(current.getComponent(), requested.getComponent())
                || !Objects.equals(current.getPerms(), requested.getPerms())
                || !Objects.equals(current.getIcon(), requested.getIcon())
                || current.getStatus() != requested.getStatus()
                || current.isCacheable() != requested.isCacheable()
                || current.isExternal() != requested.isExternal();
        if (immutableFieldsChanged) {
            throw new AccessDeniedException("系统内置菜单只允许调整显示状态和排序");
        }
    }

    private void validateParent(Menu menu, Long updatingId) {
        if (updatingId != null && menu.getParentId() == updatingId) {
            throw new IllegalArgumentException("菜单不能以自身作为上级");
        }
        if (menu.getParentId() == 0) {
            return;
        }

        Set<Long> visited = new HashSet<>();
        long parentId = menu.getParentId();
        int depth = 0;
        while (parentId != 0) {
            if (++depth > MAX_PARENT_DEPTH || !visited.add(parentId)) {
                throw new IllegalStateException("菜单树存在循环或层级过深");
            }
            if (updatingId != null && parentId == updatingId) {
                throw new IllegalArgumentException("不能将菜单移动到自身的后代节点");
            }
            Menu parent = menuMapper.findByIdForUpdate(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("上级菜单不存在");
            }
            if (parentId == menu.getParentId() && "F".equals(parent.getMenuType())) {
                throw new IllegalArgumentException("按钮权限不能作为上级菜单");
            }
            parentId = parent.getParentId();
        }
    }

    private void validateUniquePermission(String permission, Long updatingId) {
        if (permission == null) {
            return;
        }
        int count = updatingId == null
                ? menuMapper.countByPermission(permission)
                : menuMapper.countByPermissionExcludingId(permission, updatingId);
        if (count > 0) {
            throw new IllegalArgumentException("权限标识已存在");
        }
    }

    private AuthPrincipal requireOperator() {
        AuthPrincipal operator = currentPrincipalService.requireCurrent();
        if (operator.userId() == null || operator.userId() <= 0) {
            throw new AccessDeniedException("菜单管理操作人必须绑定有效用户");
        }
        return operator;
    }

    private String required(String value, String fieldName, int maxLength) {
        String normalized = optional(value, maxLength, fieldName);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String optional(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maxLength);
        }
        return normalized;
    }
}
