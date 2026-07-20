package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.dto.RouteDto;
import com.genersoft.iot.vmp.auth.menu.MenuComponentRegistry;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.impl.MenuRouteServiceImpl;
import com.genersoft.iot.vmp.storager.dao.MenuMapper;
import com.genersoft.iot.vmp.storager.dao.dto.Menu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuRouteServiceTest {

    @Mock private CurrentPrincipalService currentPrincipalService;
    @Mock private MenuMapper menuMapper;

    private MenuRouteService service;

    @BeforeEach
    void setUp() {
        service = new MenuRouteServiceImpl(
                currentPrincipalService, menuMapper, new MenuComponentRegistry());
    }

    @Test
    void prunesHiddenDisabledButtonsAndEmptyDirectoriesWithStableOrder() {
        AuthPrincipal principal = principal(false);
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(menuMapper.findEnabledForUser(12L)).thenReturn(List.of(
                menu(200, 0, "M", "Operations", "/operations", "Layout", 2, 0, 0),
                menu(100, 0, "M", "System", "/system", "Layout", 1, 0, 0),
                menu(110, 100, "C", "SystemUser", "user", "system/user/index", 1, 0, 0),
                menu(111, 100, "C", "Hidden", "hidden", "system/role/index", 2, 1, 0),
                menu(112, 100, "F", null, null, null, 3, 0, 0),
                menu(210, 200, "C", "Disabled", "disabled", "operations/log/index", 1, 0, 1)));

        List<RouteDto> routes = service.currentRoutes();

        assertEquals(1, routes.size());
        assertEquals("System", routes.getFirst().name());
        assertEquals(List.of("SystemUser"), routes.getFirst().children().stream().map(RouteDto::name).toList());
    }

    @Test
    void rejectsUnknownComponentBeforeItCanReachFrontend() {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(false));
        when(menuMapper.findEnabledForUser(12L)).thenReturn(List.of(
                menu(100, 0, "C", "Bad", "/bad", "../../evil", 1, 0, 0)));

        assertThrows(IllegalStateException.class, service::currentRoutes);
    }

    @Test
    void systemUserLoadsAllEnabledMenus() {
        AuthPrincipal principal = principal(true);
        when(currentPrincipalService.requireCurrent()).thenReturn(principal);
        when(menuMapper.findAllEnabled()).thenReturn(List.of(
                menu(100, 0, "C", "Device", "/device", "device/index", 1, 0, 0)));

        assertEquals(1, service.currentRoutes().size());
        verify(menuMapper).findAllEnabled();
    }

    private AuthPrincipal principal(boolean systemUser) {
        return new AuthPrincipal(
                PrincipalType.USER, 12L, 12L, "operator", null,
                Set.of(3L), 7L, systemUser, Set.of("system:user:list"));
    }

    private Menu menu(
            long id,
            long parentId,
            String type,
            String name,
            String path,
            String component,
            int order,
            int visible,
            int status) {
        Menu menu = new Menu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuType(type);
        menu.setRouteName(name);
        menu.setMenuName(name);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setOrderNum(order);
        menu.setVisible(visible);
        menu.setStatus(status);
        return menu;
    }
}
