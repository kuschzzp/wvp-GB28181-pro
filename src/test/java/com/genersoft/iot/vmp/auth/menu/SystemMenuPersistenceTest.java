package com.genersoft.iot.vmp.auth.menu;

import com.genersoft.iot.vmp.auth.menu.dto.MenuRequest;
import com.genersoft.iot.vmp.auth.menu.impl.SystemMenuServiceImpl;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SystemMenuServiceImpl.class, MenuComponentRegistry.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SystemMenuPersistenceTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SystemMenuService menuService;

    @MockitoBean private CurrentPrincipalService currentPrincipalService;
    @MockitoBean private TokenVersionService tokenVersionService;

    @BeforeEach
    void loadSchema() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
        when(currentPrincipalService.requireCurrent()).thenReturn(principal());
    }

    @Test
    void createsUpdatesAndDeletesMenuWithRoleRevocation() {
        long menuId = menuService.create(directory("扩展管理"));

        assertTrue(menuId > 0);
        assertEquals("扩展管理", jdbcTemplate.queryForObject(
                "select menu_name from wvp_menu where id = ?", String.class, menuId));
        assertEquals(10L, jdbcTemplate.queryForObject(
                "select create_by from wvp_menu where id = ?", Long.class, menuId));

        jdbcTemplate.update(
                "insert into wvp_role_menu (role_id, menu_id) values (?, ?)", 99L, menuId);
        menuService.update(menuId, directory("扩展能力"));

        assertEquals("扩展能力", jdbcTemplate.queryForObject(
                "select menu_name from wvp_menu where id = ?", String.class, menuId));
        verify(tokenVersionService).bumpUsers(List.of(99L), "MENU_CHANGED");

        menuService.delete(menuId);

        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from wvp_menu where id = ?", Integer.class, menuId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from wvp_role_menu where menu_id = ?", Integer.class, menuId));
        verify(tokenVersionService).bumpUsers(List.of(99L), "MENU_DELETED");
    }

    private MenuRequest directory(String name) {
        return new MenuRequest(
                0L,
                name,
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
                false);
    }

    private AuthPrincipal principal() {
        return new AuthPrincipal(
                PrincipalType.USER,
                10L,
                10L,
                "menu-admin",
                1L,
                Set.of(3L),
                0L,
                false,
                Set.of("system:menu:add", "system:menu:edit", "system:menu:remove"));
    }
}
