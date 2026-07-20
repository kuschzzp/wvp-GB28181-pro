package com.genersoft.iot.vmp.auth.department;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
@ActiveProfiles("test")
class DepartmentMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void loadSchema() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
    }

    @Test
    void createsRootClosureAndBackfillsEveryUser() {
        assertEquals(1, count("select count(*) from wvp_dept where dept_code = 'ROOT'"));
        assertEquals(1, count("""
                select count(*)
                  from wvp_dept_closure
                 where ancestor_id = descendant_id
                   and depth = 0
                """));
        assertEquals(0, count("select count(*) from wvp_user where dept_id is null"));
        assertEquals(0, count("""
                select count(*)
                  from wvp_user u
                  left join wvp_dept d on d.id = u.dept_id
                 where d.id is null
                """));
    }

    @Test
    void definesDepartmentKeysIndexesAndUserReference() throws SQLException {
        assertEquals(List.of("role_id", "dept_id"), primaryKeyColumns("WVP_ROLE_DEPT"));
        assertEquals(List.of("ancestor_id", "descendant_id"),
                primaryKeyColumns("WVP_DEPT_CLOSURE"));
        assertEquals(List.of("descendant_id", "ancestor_id"),
                indexColumns("WVP_DEPT_CLOSURE", "IDX_WVP_DEPT_CLOSURE_DESC"));
        assertEquals(List.of("dept_id", "status", "deleted"),
                indexColumns("WVP_USER", "IDX_USER_DEPT_STATUS"));
        assertEquals(List.of("dept_id", "role_id"),
                indexColumns("WVP_ROLE_DEPT", "IDX_WVP_ROLE_DEPT_DEPT"));
        assertTrue(hasForeignKey("WVP_USER", "DEPT_ID", "WVP_DEPT", "ID"));
    }

    @Test
    void allProductionDialectsDefineEquivalentDepartmentStructures() throws IOException {
        for (String dialect : List.of("mysql", "postgresql", "kingbase")) {
            for (String kind : List.of("初始化", "更新")) {
                String sql = Files.readString(Path.of(
                        "数据库/2.8.0/" + kind + "-" + dialect + "-2.8.0.sql"))
                        .toLowerCase(Locale.ROOT);
                assertTrue(sql.contains("wvp_dept"), dialect + " " + kind + " 缺少部门表");
                assertTrue(sql.contains("wvp_dept_closure"), dialect + " " + kind + " 缺少闭包表");
                assertTrue(sql.contains("wvp_role_dept"), dialect + " " + kind + " 缺少角色部门关系");
                assertTrue(sql.contains("idx_wvp_dept_closure_desc"),
                        dialect + " " + kind + " 缺少闭包反向索引");
                assertTrue(sql.contains("idx_wvp_role_dept_dept"),
                        dialect + " " + kind + " 缺少角色部门反向索引");
                assertTrue(sql.contains("dept_code") && sql.contains("'root'"),
                        dialect + " " + kind + " 缺少根部门种子");
            }
        }
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private List<String> primaryKeyColumns(String tableName) throws SQLException {
        List<OrderedColumn> columns = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData()
                     .getPrimaryKeys(null, null, tableName)) {
            while (resultSet.next()) {
                columns.add(new OrderedColumn(
                        resultSet.getShort("KEY_SEQ"),
                        resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT)));
            }
        }
        return orderedNames(columns);
    }

    private List<String> indexColumns(String tableName, String indexName) throws SQLException {
        List<OrderedColumn> columns = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData()
                     .getIndexInfo(null, null, tableName, false, false)) {
            while (resultSet.next()) {
                if (indexName.equalsIgnoreCase(resultSet.getString("INDEX_NAME"))) {
                    columns.add(new OrderedColumn(
                            resultSet.getShort("ORDINAL_POSITION"),
                            resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT)));
                }
            }
        }
        return orderedNames(columns);
    }

    private boolean hasForeignKey(
            String tableName,
            String columnName,
            String referencedTable,
            String referencedColumn) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet resultSet = metadata.getImportedKeys(null, null, tableName)) {
                while (resultSet.next()) {
                    if (columnName.equalsIgnoreCase(resultSet.getString("FKCOLUMN_NAME"))
                            && referencedTable.equalsIgnoreCase(resultSet.getString("PKTABLE_NAME"))
                            && referencedColumn.equalsIgnoreCase(resultSet.getString("PKCOLUMN_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<String> orderedNames(List<OrderedColumn> columns) {
        columns.sort(java.util.Comparator.comparingInt(OrderedColumn::position));
        return columns.stream().map(OrderedColumn::name).toList();
    }

    private record OrderedColumn(int position, String name) {
    }
}
