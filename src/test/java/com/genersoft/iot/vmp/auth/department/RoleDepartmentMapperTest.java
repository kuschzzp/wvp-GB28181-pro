package com.genersoft.iot.vmp.auth.department;

import com.genersoft.iot.vmp.auth.department.model.RoleDepartmentGrant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoleDepartmentMapperTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RoleDepartmentMapper mapper;

    @BeforeEach
    void loadSchema() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
        jdbcTemplate.update("update wvp_role set dept_data_scope = 'CUSTOM' where id = 99");
        jdbcTemplate.update("""
                insert into wvp_role
                    (id, name, role_key, role_sort, dept_data_scope, video_scope_type,
                     status, system_role)
                values (98, '停用角色', 'disabled_role', 98, 'CUSTOM', 'NONE', 1, 0)
                """);
        jdbcTemplate.update("""
                insert into wvp_dept
                    (id, parent_id, dept_name, dept_code, order_num, status, deleted)
                values
                    (20, 1, '启用部门', 'ENABLED', 0, 0, 0),
                    (21, 1, '停用部门', 'DISABLED', 0, 1, 0),
                    (22, 1, '删除部门', 'DELETED', 0, 0, 1)
                """);
        jdbcTemplate.update("""
                insert into wvp_role_dept (role_id, dept_id)
                values (99, 20), (99, 21), (99, 22), (98, 20)
                """);
    }

    @Test
    void mapsScopeRowsAndFiltersCustomDepartmentsByRoleAndDepartmentStatus() {
        assertEquals(List.of(
                        new RoleDepartmentGrant(1L, "ALL"),
                        new RoleDepartmentGrant(99L, "CUSTOM")),
                mapper.findEnabledScopes(Set.of(1L, 99L)));
        assertEquals(List.of(20L),
                mapper.findEnabledCustomDepartmentIds(Set.of(99L)));

        assertEquals(List.of(), mapper.findEnabledScopes(Set.of(98L)));
        assertEquals(List.of(), mapper.findEnabledCustomDepartmentIds(Set.of(98L)));
    }
}
