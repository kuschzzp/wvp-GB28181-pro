package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.menu.dto.MenuRequest;
import com.genersoft.iot.vmp.auth.menu.MenuComponentRegistry;
import com.genersoft.iot.vmp.auth.menu.impl.SystemMenuServiceImpl;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.storager.dao.MenuMapper;
import com.genersoft.iot.vmp.storager.dao.dto.Menu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemMenuServiceTest {

    @Mock private MenuMapper menuMapper;
    @Mock private CurrentPrincipalService currentPrincipalService;
    @Mock private TokenVersionService tokenVersionService;

    private SystemMenuServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemMenuServiceImpl(
                menuMapper,
                currentPrincipalService,
                tokenVersionService,
                new MenuComponentRegistry());
    }

    @Test
    void nonSystemOperatorCannotEditSystemMenu() {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(false));
        when(menuMapper.findByIdForUpdate(100L)).thenReturn(menu(100L, 0L, "M", true));

        assertThrows(AccessDeniedException.class,
                () -> service.update(100L, directory(0L, true)));

        verify(menuMapper, never()).update(any(), any(Long.class), any(LocalDateTime.class));
    }

    @Test
    void updatingAssignedMenuInvalidatesEveryAffectedUser() {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(false));
        when(menuMapper.findByIdForUpdate(7L)).thenReturn(menu(7L, 0L, "M", false));
        when(menuMapper.findUserIdsByMenuId(7L)).thenReturn(List.of(21L, 22L));
        when(menuMapper.update(any(Menu.class), eq(10L), any(LocalDateTime.class)))
                .thenReturn(1);

        service.update(7L, directory(0L, false));

        verify(tokenVersionService).bumpUsers(List.of(21L, 22L), "MENU_CHANGED");
    }

    @Test
    void movingMenuUnderItsDescendantIsRejected() {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(false));
        when(menuMapper.findByIdForUpdate(7L)).thenReturn(menu(7L, 0L, "M", false));
        when(menuMapper.findByIdForUpdate(9L)).thenReturn(menu(9L, 7L, "M", false));

        assertThrows(IllegalArgumentException.class,
                () -> service.update(7L, directory(9L, false)));

        verify(menuMapper, never()).update(any(), any(Long.class), any(LocalDateTime.class));
    }

    @Test
    void deletingAssignedOrdinaryMenuRevokesRelationsAndTokens() {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(false));
        when(menuMapper.findByIdForUpdate(7L)).thenReturn(menu(7L, 0L, "M", false));
        when(menuMapper.countChildren(7L)).thenReturn(0);
        when(menuMapper.findUserIdsByMenuId(7L)).thenReturn(List.of(21L, 22L));
        when(menuMapper.deleteById(7L)).thenReturn(1);

        service.delete(7L);

        verify(menuMapper).deleteRoleMenusByMenuId(7L);
        verify(tokenVersionService).bumpUsers(List.of(21L, 22L), "MENU_DELETED");
    }

    @Test
    void unknownRouteComponentIsRejectedAtMenuWriteTime() {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(false));
        MenuRequest request = new MenuRequest(
                0L,
                "恶意组件",
                "C",
                "Unsafe",
                "/unsafe",
                "../../unsafe",
                null,
                null,
                10,
                0,
                0,
                false,
                false,
                false);

        assertThrows(IllegalArgumentException.class, () -> service.create(request));

        verify(menuMapper, never()).insert(any(), any(Long.class), any(LocalDateTime.class));
    }

    @Test
    void systemMenuCannotRewriteImmutableRouteFields() {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(true));
        Menu current = menu(100L, 0L, "M", true);
        current.setMenuName("扩展管理");
        current.setRouteName("Extension");
        current.setPath("/extension");
        current.setComponent("Layout");
        current.setIcon("tree");
        current.setOrderNum(10);
        current.setVisible(0);
        current.setStatus(0);
        when(menuMapper.findByIdForUpdate(100L)).thenReturn(current);

        MenuRequest rewritten = new MenuRequest(
                0L,
                "扩展管理",
                "M",
                "Extension",
                "/rewritten",
                "Layout",
                null,
                "tree",
                20,
                1,
                0,
                false,
                false,
                true);

        assertThrows(AccessDeniedException.class,
                () -> service.update(100L, rewritten));

        verify(menuMapper, never()).update(any(), any(Long.class), any(LocalDateTime.class));
    }

    private MenuRequest directory(long parentId, boolean systemMenu) {
        return new MenuRequest(
                parentId,
                "扩展管理",
                "M",
                "Extension",
                "/extension",
                "Layout",
                null,
                "tree",
                10,
                0,
                0,
                false,
                false,
                systemMenu);
    }

    private Menu menu(long id, long parentId, String type, boolean systemMenu) {
        Menu menu = new Menu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuName("菜单-" + id);
        menu.setMenuType(type);
        menu.setSystemMenu(systemMenu);
        return menu;
    }

    private AuthPrincipal principal(boolean systemUser) {
        return new AuthPrincipal(
                PrincipalType.USER,
                10L,
                10L,
                "menu-admin",
                100L,
                Set.of(3L),
                0L,
                systemUser,
                Set.of("system:menu:edit", "system:menu:remove"));
    }
}
