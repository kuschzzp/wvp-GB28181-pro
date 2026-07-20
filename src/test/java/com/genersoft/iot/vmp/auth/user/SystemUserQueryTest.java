package com.genersoft.iot.vmp.auth.user;

import com.genersoft.iot.vmp.auth.department.DepartmentCommandPolicy;
import com.genersoft.iot.vmp.auth.department.DepartmentTreeService;
import com.genersoft.iot.vmp.auth.department.impl.DepartmentTreeServiceImpl;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.department.dto.DepartmentTreeNode;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.RoleAssignmentService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.auth.user.dto.UserQuery;
import com.genersoft.iot.vmp.auth.user.dto.UserSummary;
import com.genersoft.iot.vmp.auth.user.impl.SystemUserServiceImpl;
import com.genersoft.iot.vmp.service.IUserService;
import com.github.pagehelper.PageInfo;
import com.github.pagehelper.autoconfigure.PageHelperAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(PageHelperAutoConfiguration.class)
@Import({
        SystemUserServiceImpl.class,
        DepartmentTreeServiceImpl.class
})
class SystemUserQueryTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SystemUserService userService;
    @Autowired private DepartmentTreeService departmentTreeService;

    @MockitoBean private DepartmentCommandPolicy commandPolicy;
    @MockitoBean private CurrentPrincipalService currentPrincipalService;
    @MockitoBean private PermissionRepository permissionRepository;
    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean private IUserService legacyUserService;
    @MockitoBean private RoleAssignmentService roleAssignmentService;
    @MockitoBean private TokenVersionService tokenVersionService;

    @BeforeEach
    void loadSchema() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
        seedDepartmentsAndUsers();
    }

    @Test
    void paginationTotalAndSearchAreCalculatedAfterScopeFiltering() {
        PageInfo<UserSummary> all = userService.query(query(null, 1, 1), DepartmentScope.all(10L));
        assertEquals(4, all.getTotal());
        assertEquals(1, all.getList().size());

        assertEquals(1, userService.query(query(null, 1, 20),
                scope(Set.of(10L), false, 10L)).getTotal());
        assertEquals(2, userService.query(query(null, 1, 20),
                scope(Set.of(10L, 11L), false, 10L)).getTotal());
        assertEquals(1, userService.query(query(null, 1, 20),
                DepartmentScope.self(20L)).getTotal());
        assertEquals(0, userService.query(query(null, 1, 20),
                DepartmentScope.none(20L)).getTotal());

        assertEquals(0, userService.query(query("scope_b2", 1, 20),
                scope(Set.of(10L), false, 10L)).getTotal());
        assertEquals(1, userService.query(query("scope_b2", 1, 20),
                scope(Set.of(20L), false, 10L)).getTotal());
    }

    @Test
    void visibleLookupReturnsEmptyInsteadOfLeakingOutOfScopeUser() {
        assertTrue(userService.findVisible(10L, scope(Set.of(10L), false, 10L)).isPresent());
        assertTrue(userService.findVisible(20L, scope(Set.of(10L), false, 10L)).isEmpty());
        assertTrue(userService.findVisible(20L, DepartmentScope.self(20L)).isPresent());
        assertTrue(userService.findVisible(10L, DepartmentScope.none(10L)).isEmpty());
    }

    @Test
    void departmentTreeContainsOnlyAuthorizedNodesAndReadOnlyPathAncestors() {
        List<DepartmentTreeNode> scoped = departmentTreeService.tree(
                scope(Set.of(11L), false, 10L));

        assertEquals(List.of(1L), scoped.stream().map(DepartmentTreeNode::id).toList());
        DepartmentTreeNode root = scoped.getFirst();
        assertTrue(root.readOnlyPath());
        assertEquals(List.of(10L), root.children().stream().map(DepartmentTreeNode::id).toList());
        assertTrue(root.children().getFirst().readOnlyPath());
        assertEquals(List.of(11L), root.children().getFirst().children().stream()
                .map(DepartmentTreeNode::id).toList());
        assertFalse(root.children().getFirst().children().getFirst().readOnlyPath());

        List<DepartmentTreeNode> selfTree = departmentTreeService.tree(DepartmentScope.self(20L));
        assertEquals(List.of(1L), selfTree.stream().map(DepartmentTreeNode::id).toList());
        assertEquals(List.of(20L), selfTree.getFirst().children().stream()
                .map(DepartmentTreeNode::id).toList());
        assertFalse(selfTree.getFirst().children().getFirst().readOnlyPath());

        assertTrue(departmentTreeService.tree(DepartmentScope.none(20L)).isEmpty());
    }

    private UserQuery query(String keyword, int page, int count) {
        return new UserQuery(page, count, keyword, null, null);
    }

    private DepartmentScope scope(Set<Long> deptIds, boolean self, long userId) {
        return new DepartmentScope(false, deptIds, self, userId);
    }

    private void seedDepartmentsAndUsers() {
        jdbcTemplate.update("delete from wvp_user_role_rel");
        jdbcTemplate.update("delete from wvp_user_api_key");
        jdbcTemplate.update("delete from wvp_user");
        jdbcTemplate.update("""
                insert into wvp_dept
                    (id, parent_id, dept_name, dept_code, order_num, status, deleted)
                values
                    (10, 1, 'A部门', 'A', 10, 0, 0),
                    (11, 10, 'A1部门', 'A1', 10, 0, 0),
                    (20, 1, 'B部门', 'B', 20, 0, 0)
                """);
        jdbcTemplate.update("""
                insert into wvp_dept_closure (ancestor_id, descendant_id, depth)
                values
                    (1, 10, 1), (10, 10, 0),
                    (1, 11, 2), (10, 11, 1), (11, 11, 0),
                    (1, 20, 1), (20, 20, 0)
                """);
        jdbcTemplate.update("""
                insert into wvp_user
                    (id, username, password, role_id, dept_id, nickname, status, locked,
                     deleted, token_version, password_version)
                values
                    (10, 'scope_a', 'password', 99, 10, 'A用户', 0, 0, 0, 0, 1),
                    (11, 'scope_a1', 'password', 99, 11, 'A1用户', 0, 0, 0, 0, 1),
                    (20, 'scope_b', 'password', 99, 20, 'B用户', 0, 0, 0, 0, 1),
                    (21, 'scope_b2', 'password', 99, 20, 'B2用户', 0, 0, 0, 0, 1)
                """);
        jdbcTemplate.update("""
                insert into wvp_user_role_rel (user_id, role_id)
                values (10, 99), (11, 99), (20, 99), (21, 99)
                """);
    }
}
