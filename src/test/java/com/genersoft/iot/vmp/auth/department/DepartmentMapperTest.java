package com.genersoft.iot.vmp.auth.department;

import com.genersoft.iot.vmp.auth.department.model.Department;
import com.genersoft.iot.vmp.auth.department.model.DepartmentClosurePath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DepartmentMapperTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DepartmentMapper mapper;

    @BeforeEach
    void loadSchema() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
    }

    @Test
    void insertsDepartmentWithGeneratedIdAndPortableClosureStatements() {
        Department department = department(1L, "研发部", "RD", 0);

        assertEquals(1, mapper.insert(department));
        assertNotNull(department.getId());
        assertTrue(department.getId() > 1L);
        assertEquals(1, mapper.insertAncestorPaths(1L, department.getId()));
        assertEquals(1, mapper.insertSelfPath(department.getId()));

        Department loaded = mapper.findById(department.getId());
        assertEquals(1L, loaded.getParentId());
        assertEquals("研发部", loaded.getDeptName());
        assertEquals(List.of(1L, department.getId()),
                mapper.findEnabledDescendantIds(1L));

        List<DepartmentClosurePath> ancestors = mapper.findAncestorPathsForUpdate(department.getId());
        assertEquals(List.of(1L, department.getId()),
                ancestors.stream().map(DepartmentClosurePath::ancestorId).toList());
        assertEquals(List.of(1, 0),
                ancestors.stream().map(DepartmentClosurePath::depth).toList());
    }

    @Test
    void enabledDescendantQueryExcludesDisabledAndDeletedNodes() {
        Department disabled = department(1L, "停用部门", "DISABLED", 1);
        mapper.insert(disabled);
        mapper.insertAncestorPaths(1L, disabled.getId());
        mapper.insertSelfPath(disabled.getId());

        Department deleted = department(1L, "已删除部门", "DELETED", 0);
        deleted.setDeleted(1);
        mapper.insert(deleted);
        mapper.insertAncestorPaths(1L, deleted.getId());
        mapper.insertSelfPath(deleted.getId());

        assertEquals(List.of(1L), mapper.findEnabledDescendantIds(1L));
    }

    private Department department(long parentId, String name, String code, int status) {
        Department department = new Department();
        department.setParentId(parentId);
        department.setDeptName(name);
        department.setDeptCode(code);
        department.setOrderNum(0);
        department.setStatus(status);
        department.setDeleted(0);
        department.setCreateBy(1L);
        department.setUpdateBy(1L);
        department.setCreateTime(LocalDateTime.now());
        department.setUpdateTime(LocalDateTime.now());
        return department;
    }
}
