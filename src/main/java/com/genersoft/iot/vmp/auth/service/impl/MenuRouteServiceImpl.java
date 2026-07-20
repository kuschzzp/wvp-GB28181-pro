package com.genersoft.iot.vmp.auth.service.impl;

import com.genersoft.iot.vmp.auth.dto.RouteDto;
import com.genersoft.iot.vmp.auth.dto.RouteMetaDto;
import com.genersoft.iot.vmp.auth.menu.MenuComponentRegistry;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.MenuRouteService;
import com.genersoft.iot.vmp.storager.dao.MenuMapper;
import com.genersoft.iot.vmp.storager.dao.dto.Menu;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MenuRouteServiceImpl implements MenuRouteService {

    private static final Comparator<Menu> MENU_ORDER = Comparator
            .comparingInt(Menu::getOrderNum)
            .thenComparingLong(Menu::getId);

    private final CurrentPrincipalService currentPrincipalService;
    private final MenuMapper menuMapper;
    private final MenuComponentRegistry componentRegistry;

    public MenuRouteServiceImpl(
            CurrentPrincipalService currentPrincipalService,
            MenuMapper menuMapper,
            MenuComponentRegistry componentRegistry) {
        this.currentPrincipalService = currentPrincipalService;
        this.menuMapper = menuMapper;
        this.componentRegistry = componentRegistry;
    }

    @Override
    public List<RouteDto> currentRoutes() {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        List<Menu> menus = principal.systemUser()
                ? menuMapper.findAllEnabled()
                : menuMapper.findEnabledForUser(principal.userId());
        return buildRoutes(menus);
    }

    List<RouteDto> buildRoutes(List<Menu> menus) {
        Map<Long, List<Menu>> childrenByParent = new HashMap<>();
        menus.stream()
                .filter(this::isVisibleRouteNode)
                .sorted(MENU_ORDER)
                .forEach(menu -> childrenByParent
                        .computeIfAbsent(menu.getParentId(), ignored -> new ArrayList<>())
                        .add(menu));
        return buildChildren(0L, childrenByParent);
    }

    private List<RouteDto> buildChildren(long parentId, Map<Long, List<Menu>> childrenByParent) {
        List<RouteDto> routes = new ArrayList<>();
        for (Menu menu : childrenByParent.getOrDefault(parentId, List.of())) {
            validateComponent(menu);
            List<RouteDto> children = buildChildren(menu.getId(), childrenByParent);
            if ("M".equals(menu.getMenuType()) && children.isEmpty()) {
                continue;
            }
            routes.add(new RouteDto(
                    menu.getRouteName(),
                    menu.getPath(),
                    menu.getComponent(),
                    new RouteMetaDto(
                            menu.getMenuName(),
                            menu.getIcon(),
                            !menu.isCacheable(),
                            menu.isExternal() ? menu.getPath() : null),
                    List.copyOf(children)));
        }
        return List.copyOf(routes);
    }

    private boolean isVisibleRouteNode(Menu menu) {
        return menu != null
                && menu.getStatus() == 0
                && menu.getVisible() == 0
                && ("M".equals(menu.getMenuType()) || "C".equals(menu.getMenuType()));
    }

    private void validateComponent(Menu menu) {
        if (!componentRegistry.supports(menu.getComponent())) {
            throw new IllegalStateException(
                    "菜单 " + menu.getId() + " 使用了未注册组件：" + menu.getComponent());
        }
    }
}
