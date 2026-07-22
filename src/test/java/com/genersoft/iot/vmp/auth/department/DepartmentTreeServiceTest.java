package com.genersoft.iot.vmp.auth.department;

import com.genersoft.iot.vmp.auth.department.dto.CreateDepartmentCommand;
import com.genersoft.iot.vmp.auth.department.dto.UpdateDepartmentCommand;
import com.genersoft.iot.vmp.auth.department.impl.DepartmentTreeServiceImpl;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.conf.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DepartmentTreeServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DepartmentTreeServiceTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DepartmentTreeService service;

    @Autowired
    private RoleDepartmentMapper roleDepartmentMapper;

    @MockitoBean
    private DepartmentCommandPolicy commandPolicy;

    @BeforeEach
    void loadSchema() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
    }

    @Test
    void createsMovesAndRejectsCyclesWithoutDamagingClosure() {
        long a = create(1L, "A", "A");
        long a1 = create(a, "A1", "A1");
        long b = create(1L, "B", "B");

        assertEquals(Set.of(a, a1), service.findEnabledDescendantIds(a));
        assertThrows(IllegalArgumentException.class, () -> service.move(a, a1, operator()));
        assertEquals(1L, parentId(a));

        service.move(a, b, operator());

        assertEquals(b, parentId(a));
        assertEquals(1, depth(b, a));
        assertEquals(2, depth(b, a1));
        assertEquals(2, depth(1L, a));
        assertEquals(3, depth(1L, a1));
        assertEquals(Set.of(a, a1), service.findEnabledDescendantIds(a));
    }

    @Test
    void moveRollsBackParentAndClosureWhenParentUpdateFails() {
        long a = create(1L, "A", "A");
        long a1 = create(a, "A1", "A1");
        long b = create(1L, "B", "B");
        jdbcTemplate.execute("alter table wvp_dept add constraint ck_test_move_failure check "
                + "(id <> " + a + " or parent_id <> " + b + ")");

        assertThrows(DataIntegrityViolationException.class,
                () -> service.move(a, b, operator()));

        assertEquals(1L, parentId(a));
        assertEquals(1, depth(1L, a));
        assertEquals(2, depth(1L, a1));
    }

    @Test
    void refusesRootDepartmentsWithChildrenOrUsersAndDeletesEmptyLeaf() {
        long parent = create(1L, "业务部", "BIZ");
        long child = create(parent, "业务一组", "BIZ-1");

        BusinessException childException = assertThrows(BusinessException.class,
                () -> service.delete(parent, operator()));
        assertEquals("部门存在子部门，不能删除", childException.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> service.delete(1L, operator()));

        jdbcTemplate.update("""
                insert into wvp_user
                    (username, password, role_id, dept_id, nickname, status, locked, deleted,
                     token_version, password_version)
                values ('dept_user', 'password', 99, ?, '部门用户', 0, 0, 0, 0, 1)
                """, child);
        BusinessException userException = assertThrows(BusinessException.class,
                () -> service.delete(child, operator()));
        assertEquals("部门存在用户，不能删除", userException.getMessage());

        long leaf = create(1L, "空部门", "EMPTY");
        service.delete(leaf, operator());
        assertEquals(0, count("select count(*) from wvp_dept where id = ?", leaf));
        assertEquals(0, count("select count(*) from wvp_dept_closure where descendant_id = ?", leaf));
    }

    @Test
    void scopedCommandsCheckEveryObjectAndCannotDeleteOwnMainDepartment() {
        long source = create(1L, "来源部门", "SOURCE");
        long destination = create(1L, "目标部门", "DESTINATION");
        long ownDepartment = create(1L, "本人部门", "OWN");
        DepartmentScope scope = new DepartmentScope(
                false, Set.of(source, destination, ownDepartment), false, 7L);
        clearInvocations(commandPolicy);

        long child = service.create(new CreateDepartmentCommand(
                source, "范围子部门", "SCOPED-CHILD", 0, null, 0), operator(), scope);
        verify(commandPolicy).requireDepartmentInScope(source, scope);

        service.move(child, destination, operator(), scope);
        verify(commandPolicy).requireDepartmentInScope(child, scope);
        verify(commandPolicy).requireDepartmentInScope(destination, scope);

        AuthPrincipal departmentOwner = operator(7L, ownDepartment, false);
        assertThrows(AccessDeniedException.class,
                () -> service.delete(ownDepartment, departmentOwner, scope));
        verify(commandPolicy).requireDepartmentInScope(ownDepartment, scope);
    }

    @Test
    void scopedUpdateMovesDepartmentAndUpdatesItsMetadataAtomically() {
        long source = create(1L, "待编辑部门", "EDIT-ME");
        long destination = create(1L, "新上级", "NEW-PARENT");
        DepartmentScope scope = new DepartmentScope(
                false, Set.of(source, destination), false, 7L);
        clearInvocations(commandPolicy);

        service.update(new UpdateDepartmentCommand(
                source,
                destination,
                "编辑后部门",
                "EDITED",
                9,
                null,
                0), operator(), scope);

        assertEquals(destination, parentId(source));
        assertEquals("编辑后部门", jdbcTemplate.queryForObject(
                "select dept_name from wvp_dept where id = ?", String.class, source));
        assertEquals("EDITED", jdbcTemplate.queryForObject(
                "select dept_code from wvp_dept where id = ?", String.class, source));
        assertEquals(1, depth(destination, source));
        verify(commandPolicy, atLeastOnce()).requireDepartmentInScope(source, scope);
        verify(commandPolicy, atLeastOnce()).requireDepartmentInScope(destination, scope);
    }

    @Test
    void disabledIntermediateDepartmentClosesItsEntireSubtree() {
        long top = create(1L, "上级部门", "FAIL-CLOSED-TOP");
        long middle = create(top, "停用部门", "FAIL-CLOSED-MIDDLE");
        long leaf = create(middle, "仍启用的孙部门", "FAIL-CLOSED-LEAF");
        jdbcTemplate.update("update wvp_dept set status = 1 where id = ?", middle);

        assertEquals(Set.of(top), service.findEnabledDescendantIds(top));
        assertTrue(service.tree(new DepartmentScope(
                false, Set.of(leaf), false, 7L)).isEmpty());
        jdbcTemplate.update(
                "update wvp_role set dept_data_scope = 'CUSTOM' where id = 99");
        jdbcTemplate.update(
                "insert into wvp_role_dept (role_id, dept_id) values (99, ?)", leaf);
        assertTrue(roleDepartmentMapper.findEnabledCustomDepartmentIds(Set.of(99L)).isEmpty());
    }

    private long create(long parentId, String name, String code) {
        return service.create(new CreateDepartmentCommand(
                parentId, name, code, 0, null, 0), operator());
    }

    private AuthPrincipal operator() {
        return operator(1L, 1L, true);
    }

    private AuthPrincipal operator(long userId, long deptId, boolean systemUser) {
        return new AuthPrincipal(
                PrincipalType.USER,
                userId,
                userId,
                "admin",
                deptId,
                Set.of(1L),
                0L,
                systemUser,
                Set.of("system:dept:add", "system:dept:edit", "system:dept:remove"));
    }

    private long parentId(long deptId) {
        return jdbcTemplate.queryForObject(
                "select parent_id from wvp_dept where id = ?", Long.class, deptId);
    }

    private int depth(long ancestorId, long descendantId) {
        return jdbcTemplate.queryForObject("""
                select depth from wvp_dept_closure
                 where ancestor_id = ? and descendant_id = ?
                """, Integer.class, ancestorId, descendantId);
    }

    private int count(String sql, Object value) {
        return jdbcTemplate.queryForObject(sql, Integer.class, value);
    }
}
