package com.genersoft.iot.vmp.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
@ActiveProfiles("test")
class VideoResourceSchemaH2Test {

    private static final List<String> DIALECT_SCHEMA_FILES = List.of(
            "数据库/2.8.0/初始化-mysql-2.8.0.sql",
            "数据库/2.8.0/更新-mysql-2.8.0.sql",
            "数据库/2.8.0/初始化-postgresql-2.8.0.sql",
            "数据库/2.8.0/更新-postgresql-2.8.0.sql",
            "数据库/2.8.0/初始化-kingbase-2.8.0.sql",
            "数据库/2.8.0/更新-kingbase-2.8.0.sql");

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
    void seedsSafeVideoScopeDefaultsAndRootClosure() {
        assertEquals(1, count("""
                select count(*)
                  from wvp_resource_group g
                  join wvp_resource_group_closure c
                    on c.ancestor_id = g.id
                   and c.descendant_id = g.id
                   and c.depth = 0
                 where g.group_code = 'ROOT_VIDEO'
                   and g.parent_id = 0
                   and g.status = 0
                """));
        assertEquals("ALL", jdbcTemplate.queryForObject(
                "select video_scope_type from wvp_role where role_key = 'super_admin'",
                String.class));
        assertEquals(0, count("""
                select count(*)
                  from wvp_role
                 where system_role = 0
                   and video_scope_type <> 'NONE'
                """));
        assertEquals(0, count("""
                select count(*)
                  from wvp_role_resource_group rrg
                  join wvp_resource_group g on g.id = rrg.resource_group_id
                 where g.status <> 0
                """));
    }

    @Test
    void resourceItemIsUniqueInsideAGroupButReusableAcrossGroups() {
        insertGroup(10, "BUILDING_A", "一号楼");
        insertGroup(11, "BUILDING_B", "二号楼");

        jdbcTemplate.update("""
                insert into wvp_resource_group_item
                    (id, group_id, resource_type, resource_id, create_time)
                values (100, 10, 'CHANNEL', 9001, current_timestamp)
                """);
        jdbcTemplate.update("""
                insert into wvp_resource_group_item
                    (id, group_id, resource_type, resource_id, create_time)
                values (101, 11, 'CHANNEL', 9001, current_timestamp)
                """);

        assertEquals(2, count("""
                select count(*)
                  from wvp_resource_group_item
                 where resource_type = 'CHANNEL'
                   and resource_id = 9001
                """));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                insert into wvp_resource_group_item
                    (id, group_id, resource_type, resource_id, create_time)
                values (102, 10, 'CHANNEL', 9001, current_timestamp)
                """));
    }

    @Test
    void definesResourceRelationsAndLookupIndexes() throws SQLException {
        assertEquals(List.of("ancestor_id", "descendant_id"),
                primaryKeyColumns("WVP_RESOURCE_GROUP_CLOSURE"));
        assertEquals(List.of("role_id", "resource_group_id"),
                primaryKeyColumns("WVP_ROLE_RESOURCE_GROUP"));
        assertEquals(List.of("descendant_id", "ancestor_id"),
                indexColumns("WVP_RESOURCE_GROUP_CLOSURE", "IDX_WVP_RESOURCE_GROUP_CLOSURE_DESC"));
        assertEquals(List.of("resource_type", "resource_id"),
                indexColumns("WVP_RESOURCE_GROUP_ITEM", "IDX_WVP_RESOURCE_ITEM_REF"));
        assertEquals(List.of("resource_group_id", "role_id"),
                indexColumns("WVP_ROLE_RESOURCE_GROUP", "IDX_WVP_ROLE_RESOURCE_GROUP_GROUP"));
        assertEquals(List.of("channel_id", "start_time"),
                indexColumns("WVP_CLOUD_RECORD", "IDX_WVP_CLOUD_RECORD_CHANNEL"));
        assertTrue(hasForeignKey(
                "WVP_ROLE_RESOURCE_GROUP", "ROLE_ID", "WVP_ROLE", "ID"));
        assertTrue(hasForeignKey(
                "WVP_ROLE_RESOURCE_GROUP", "RESOURCE_GROUP_ID", "WVP_RESOURCE_GROUP", "ID"));
    }

    @Test
    void cloudRecordStoresBoundSourceAndKeepsLegacyRowsUnbound() {
        Set<String> columns = new HashSet<>(jdbcTemplate.queryForList("""
                select lower(column_name)
                  from information_schema.columns
                 where lower(table_name) = 'wvp_cloud_record'
                """, String.class));
        assertTrue(columns.containsAll(Set.of(
                "source_type", "source_id", "channel_id", "source_reason")));

        jdbcTemplate.update("""
                insert into wvp_cloud_record
                    (id, app, stream, start_time, source_type, source_id, channel_id)
                values (2, 'rtp', '34020000001320000001', 1000, 'CHANNEL', 88, 88)
                """);

        assertEquals("CHANNEL:88:88", jdbcTemplate.queryForObject("""
                select source_type || ':' || source_id || ':' || channel_id
                  from wvp_cloud_record
                 where id = 2
                """, String.class));
        assertEquals(1, count("""
                select count(*)
                  from wvp_cloud_record
                 where id = 1
                   and source_type = 'LEGACY_UNBOUND'
                   and source_id is null
                   and channel_id is null
                """));
    }

    @Test
    void everyDialectDefinesEquivalentVideoAuthorizationSchema() throws IOException {
        for (String schemaFile : DIALECT_SCHEMA_FILES) {
            String sql = Files.readString(Path.of(schemaFile)).toLowerCase(Locale.ROOT);

            assertTrue(sql.contains("wvp_resource_group"), schemaFile);
            assertTrue(sql.contains("wvp_resource_group_closure"), schemaFile);
            assertTrue(sql.contains("wvp_resource_group_item"), schemaFile);
            assertTrue(sql.contains("wvp_role_resource_group"), schemaFile);
            assertTrue(sql.contains("idx_wvp_resource_group_closure_desc"), schemaFile);
            assertTrue(sql.contains("idx_wvp_resource_item_ref"), schemaFile);
            assertTrue(sql.contains("idx_wvp_role_resource_group_group"), schemaFile);
            assertTrue(sql.contains("idx_wvp_cloud_record_channel"), schemaFile);
            assertTrue(sql.contains("source_type") && sql.contains("source_id")
                    && sql.contains("channel_id") && sql.contains("source_reason"),
                    schemaFile);
            assertTrue(sql.contains("root_video"), schemaFile);
            assertTrue(sql.contains("legacy_unbound"), schemaFile);
        }
    }

    private void insertGroup(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into wvp_resource_group
                    (id, parent_id, group_code, group_name, status, create_time, update_time)
                values (?, 1, ?, ?, 0, current_timestamp, current_timestamp)
                """, id, code, name);
        jdbcTemplate.update("""
                insert into wvp_resource_group_closure (ancestor_id, descendant_id, depth)
                values (1, ?, 1), (?, ?, 0)
                """, id, id, id);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private List<String> primaryKeyColumns(String tableName) throws SQLException {
        List<OrderedColumn> columns = new ArrayList<>();
        DatabaseMetaData metadata = dataSource.getConnection().getMetaData();
        try (ResultSet resultSet = metadata.getPrimaryKeys(null, null, tableName)) {
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
        DatabaseMetaData metadata = dataSource.getConnection().getMetaData();
        try (ResultSet resultSet = metadata.getIndexInfo(null, null, tableName, false, false)) {
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

    private boolean hasForeignKey(String tableName, String columnName,
                                  String referencedTable, String referencedColumn)
            throws SQLException {
        DatabaseMetaData metadata = dataSource.getConnection().getMetaData();
        try (ResultSet resultSet = metadata.getImportedKeys(null, null, tableName)) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("FKCOLUMN_NAME"))
                        && referencedTable.equalsIgnoreCase(resultSet.getString("PKTABLE_NAME"))
                        && referencedColumn.equalsIgnoreCase(resultSet.getString("PKCOLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> orderedNames(List<OrderedColumn> columns) {
        return columns.stream()
                .sorted((left, right) -> Short.compare(left.order(), right.order()))
                .map(OrderedColumn::name)
                .toList();
    }

    private record OrderedColumn(short order, String name) {
    }
}
