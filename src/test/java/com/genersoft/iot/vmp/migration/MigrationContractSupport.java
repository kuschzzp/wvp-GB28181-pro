package com.genersoft.iot.vmp.migration;

import org.testcontainers.containers.Container;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MigrationContractSupport {

    static final Path MYSQL_BASE = Path.of("数据库/2.7.4/初始化-mysql-2.7.4.sql");
    static final Path DOCKER_MYSQL_BOOTSTRAP = Path.of("docker/mysql/db/wvp.sql");
    static final Path DOCKER_MYSQL_AUTHORIZATION =
            Path.of("docker/mysql/db/wvp-authorization.sql");
    static final Path DOCKER_MYSQL_AUTHORIZATION_FINAL =
            Path.of("docker/mysql/db/wvp-authorization-final.sql");
    static final Path POSTGRESQL_BASE = Path.of("数据库/2.7.4/初始化-postgresql-kingbase-2.7.4.sql");
    static final Path MYSQL_INIT = Path.of("数据库/2.8.0/初始化-mysql-2.8.0.sql");
    static final Path MYSQL_UPDATE = Path.of("数据库/2.8.0/更新-mysql-2.8.0.sql");
    static final Path MYSQL_CONTRACT = Path.of("数据库/2.8.0/contract-mysql-2.8.0.sql");
    static final Path POSTGRESQL_INIT = Path.of("数据库/2.8.0/初始化-postgresql-2.8.0.sql");
    static final Path POSTGRESQL_UPDATE = Path.of("数据库/2.8.0/更新-postgresql-2.8.0.sql");
    static final Path POSTGRESQL_CONTRACT =
            Path.of("数据库/2.8.0/contract-postgresql-2.8.0.sql");
    static final Path KINGBASE_INIT = Path.of("数据库/2.8.0/初始化-kingbase-2.8.0.sql");
    static final Path KINGBASE_UPDATE = Path.of("数据库/2.8.0/更新-kingbase-2.8.0.sql");
    static final Path KINGBASE_CONTRACT = Path.of("数据库/2.8.0/contract-kingbase-2.8.0.sql");

    private static final long LEGACY_ROLE_ID = 55L;
    private static final long LEGACY_USER_ID = 55L;
    private static final long LEGACY_API_KEY_ID = 88L;
    private static final long LEGACY_CLOUD_RECORD_ID = 77L;

    private MigrationContractSupport() {
    }

    static void runMySqlScript(JdbcDatabaseContainer<?> container, Path script) throws Exception {
        String target = "/tmp/" + UUID.randomUUID() + ".sql";
        container.copyFileToContainer(MountableFile.forHostPath(script.toAbsolutePath()), target);
        Container.ExecResult result = container.execInContainer(
                "mysql",
                "--default-character-set=utf8mb4",
                "--show-warnings",
                "-u" + container.getUsername(),
                "-p" + container.getPassword(),
                "--database=" + container.getDatabaseName(),
                "--execute=source " + target);
        assertEquals(0, result.getExitCode(), () -> script + "\n" + result.getStderr() + result.getStdout());
    }

    static void assertMySqlScriptFails(
            JdbcDatabaseContainer<?> container,
            Path script,
            String expectedMessage) throws Exception {
        String target = "/tmp/" + UUID.randomUUID() + ".sql";
        container.copyFileToContainer(MountableFile.forHostPath(script.toAbsolutePath()), target);
        Container.ExecResult result = container.execInContainer(
                "mysql",
                "--default-character-set=utf8mb4",
                "--show-warnings",
                "-u" + container.getUsername(),
                "-p" + container.getPassword(),
                "--database=" + container.getDatabaseName(),
                "--execute=source " + target);
        assertTrue(result.getExitCode() != 0, "contract 前置条件不满足时必须失败");
        assertTrue((result.getStderr() + result.getStdout()).contains(expectedMessage),
                () -> script + " 未返回预期错误 " + expectedMessage + "\n"
                        + result.getStderr() + result.getStdout());
    }

    static void runDockerMySqlBootstrap(JdbcDatabaseContainer<?> container) throws Exception {
        runMySqlScript(container, DOCKER_MYSQL_BOOTSTRAP);
        runMySqlScript(container, DOCKER_MYSQL_AUTHORIZATION);
        runMySqlScript(container, DOCKER_MYSQL_AUTHORIZATION_FINAL);
    }

    static void runPostgreSqlScript(JdbcDatabaseContainer<?> container, Path script) throws Exception {
        String target = "/tmp/" + UUID.randomUUID() + ".sql";
        container.copyFileToContainer(MountableFile.forHostPath(script.toAbsolutePath()), target);
        Container.ExecResult result = container.execInContainer(
                "psql",
                "--set=ON_ERROR_STOP=1",
                "--username=" + container.getUsername(),
                "--dbname=" + container.getDatabaseName(),
                "--file=" + target);
        assertEquals(0, result.getExitCode(), () -> script + "\n" + result.getStderr() + result.getStdout());
    }

    static void assertPostgreSqlScriptFails(
            JdbcDatabaseContainer<?> container,
            Path script,
            String expectedMessage) throws Exception {
        String target = "/tmp/" + UUID.randomUUID() + ".sql";
        container.copyFileToContainer(MountableFile.forHostPath(script.toAbsolutePath()), target);
        Container.ExecResult result = container.execInContainer(
                "psql",
                "--set=ON_ERROR_STOP=1",
                "--username=" + container.getUsername(),
                "--dbname=" + container.getDatabaseName(),
                "--file=" + target);
        assertTrue(result.getExitCode() != 0, "contract 前置条件不满足时必须失败");
        assertTrue((result.getStderr() + result.getStdout()).contains(expectedMessage),
                () -> script + " 未返回预期错误 " + expectedMessage + "\n"
                        + result.getStderr() + result.getStdout());
    }

    static void runJdbcScript(Connection connection, Path script) throws IOException, SQLException {
        for (String statement : splitStatements(Files.readString(script))) {
            try (Statement jdbcStatement = connection.createStatement()) {
                jdbcStatement.execute(statement);
            } catch (SQLException exception) {
                throw new SQLException("执行迁移脚本失败: " + script + "\nSQL: " + abbreviate(statement), exception);
            }
        }
    }

    static void resetMySql(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        List<String> views = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                select table_name, table_type
                  from information_schema.tables
                 where table_schema = database()
                """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                if ("VIEW".equalsIgnoreCase(resultSet.getString(2))) {
                    views.add(resultSet.getString(1));
                } else {
                    tables.add(resultSet.getString(1));
                }
            }
        }
        execute(connection, "set foreign_key_checks = 0");
        try {
            for (String view : views) {
                execute(connection, "drop view if exists `" + view.replace("`", "``") + "`");
            }
            for (String table : tables) {
                execute(connection, "drop table if exists `" + table.replace("`", "``") + "`");
            }
        } finally {
            execute(connection, "set foreign_key_checks = 1");
        }
    }

    static void resetPostgreSql(Connection connection) throws SQLException {
        execute(connection, "drop schema if exists public cascade");
        execute(connection, "create schema public");
    }

    static void seedLegacyCloudRecord(Connection connection) throws SQLException {
        update(connection, """
                insert into wvp_cloud_record (id, app, stream, start_time)
                values (?, ?, ?, ?)
                """, LEGACY_CLOUD_RECORD_ID, "rtp", "legacy-cloud", 1_700_000_000_000L);
    }

    static void seedLegacyAuthorizationData(Connection connection) throws SQLException {
        update(connection, """
                insert into wvp_user_role (id, name, authority, create_time, update_time)
                values (?, ?, ?, ?, ?)
                """, LEGACY_ROLE_ID, "legacy-operator", "1",
                "2024-01-01 00:00:00", "2024-01-01 00:00:00");
        update(connection, """
                insert into wvp_user
                    (id, username, password, role_id, create_time, update_time, push_key)
                values (?, ?, ?, ?, ?, ?, ?)
                """, LEGACY_USER_ID, "legacy_operator", "legacy-md5", LEGACY_ROLE_ID,
                "2024-01-01 00:00:00", "2024-01-01 00:00:00", "legacy-push-key");
        update(connection, """
                insert into wvp_user_api_key
                    (id, user_id, app, api_key, expired_at, remark, enable, create_time, update_time)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, LEGACY_API_KEY_ID, LEGACY_USER_ID, "legacy-app", "legacy-plain-key",
                4_102_444_800_000L, "migration-contract", true,
                "2024-01-01 00:00:00", "2024-01-01 00:00:00");
        seedLegacyCloudRecord(connection);
    }

    static void dropLegacyAuthorizationTablesForFreshInit(Connection connection, boolean mysql)
            throws SQLException {
        if (mysql) {
            execute(connection, "set foreign_key_checks = 0");
        }
        try {
            execute(connection, "drop table if exists wvp_user_api_key" + (mysql ? "" : " cascade"));
            execute(connection, "drop table if exists wvp_user" + (mysql ? "" : " cascade"));
            execute(connection, "drop table if exists wvp_user_role" + (mysql ? "" : " cascade"));
        } finally {
            if (mysql) {
                execute(connection, "set foreign_key_checks = 1");
            }
        }
    }

    static void verifyAuthorizationContract(Connection connection, boolean upgraded) throws SQLException {
        verifyLegacyCompatibility(connection, upgraded);
        verifyClosuresAndRootScopes(connection);
        verifyApiKeyHashAndScopes(connection, upgraded);
        verifyCloudRecordSource(connection);
        verifyGeneratedIdentifiers(connection);
        verifyResourceUniqueness(connection);
        verifyIndexes(connection);
    }

    static void prepareForContract(Connection connection) throws SQLException {
        update(connection, "delete from wvp_user_api_key where status = 'LEGACY'");
        update(connection, """
                update wvp_cloud_record
                   set source_type = 'CHANNEL',
                       source_id = id,
                       channel_id = id,
                       source_reason = 'CONTRACT_BOUND'
                 where source_type is null
                    or source_type = 'LEGACY_UNBOUND'
                    or source_id is null
                    or channel_id is null
                """);
    }

    static void verifyContractedSchema(Connection connection) throws SQLException {
        assertTrue(hasColumn(connection, "wvp_user_role", "user_id"));
        assertTrue(hasColumn(connection, "wvp_user_role", "role_id"));
        assertTrue(!hasColumn(connection, "wvp_user_role", "name"),
                "最终 wvp_user_role 不能再是旧角色主表");
        assertEquals("TABLE", relationKind(connection, "wvp_user_role"));
        assertEquals("VIEW", relationKind(connection, "wvp_user_role_rel"));
        assertEquals(null, relationKind(connection, "wvp_user_role_legacy_contract"));
        assertEquals(
                scalarLong(connection, "select count(*) from wvp_user_role"),
                scalarLong(connection, "select count(*) from wvp_user_role_rel"));

        String suffix = UUID.randomUUID().toString().replace("-", "");
        long userId = insertAndAssertGeneratedId(
                connection,
                "wvp_user",
                "username",
                "contracted_user_" + suffix,
                """
                insert into wvp_user
                    (username, password, role_id, dept_id, nickname, create_time, update_time)
                values (?, ?, 1, 1, ?, current_timestamp, current_timestamp)
                """,
                "contracted_user_" + suffix,
                "{bcrypt}contracted",
                "收缩契约用户");
        update(connection,
                "insert into wvp_user_role_rel (user_id, role_id) values (?, 1)",
                userId);
        assertEquals(1, scalarLong(connection, """
                select count(*)
                  from wvp_user_role
                 where user_id = ? and role_id = 1
                """, userId));

        insertAndAssertGeneratedId(
                connection,
                "wvp_role",
                "role_key",
                "contracted_role_" + suffix,
                """
                insert into wvp_role
                    (name, role_key, dept_data_scope, video_scope_type, status, system_role)
                values (?, ?, 'NONE', 'NONE', 0, 0)
                """,
                "收缩后角色",
                "contracted_role_" + suffix);
    }

    private static boolean hasColumn(Connection connection, String table, String column)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        for (String catalog : nullableCandidates(connection.getCatalog())) {
            for (String schema : nullableCandidates(connection.getSchema())) {
                for (String tableName : Set.of(table, table.toUpperCase(Locale.ROOT))) {
                    try (ResultSet resultSet = metadata.getColumns(
                            catalog, schema, tableName, column)) {
                        if (resultSet.next()) {
                            return true;
                        }
                    }
                    try (ResultSet resultSet = metadata.getColumns(
                            catalog, schema, tableName, column.toUpperCase(Locale.ROOT))) {
                        if (resultSet.next()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static String relationKind(Connection connection, String relation) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        for (String catalog : nullableCandidates(connection.getCatalog())) {
            for (String schema : nullableCandidates(connection.getSchema())) {
                for (String relationName : Set.of(relation, relation.toUpperCase(Locale.ROOT))) {
                    try (ResultSet resultSet = metadata.getTables(
                            catalog, schema, relationName, new String[]{"TABLE", "VIEW"})) {
                        if (resultSet.next()) {
                            String type = resultSet.getString("TABLE_TYPE");
                            return type == null ? null : type.toUpperCase(Locale.ROOT);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void verifyLegacyCompatibility(Connection connection, boolean upgraded) throws SQLException {
        long usersWithLegacyRole = scalarLong(connection, """
                select count(*) from wvp_user where role_id is not null
                """);
        long migratedLegacyRoles = scalarLong(connection, """
                select count(*)
                  from wvp_user u
                  join wvp_user_role_rel urr
                    on urr.user_id = u.id and urr.role_id = u.role_id
                 where u.role_id is not null
                """);
        assertEquals(usersWithLegacyRole, migratedLegacyRoles,
                "每个旧 role_id 都必须回填到新用户角色关系表");

        assertEquals(usersWithLegacyRole, scalarLong(connection, """
                select count(*)
                  from wvp_user u
                  join wvp_user_role legacy_role on legacy_role.id = u.role_id
                 where u.role_id is not null
                """), "expand 阶段必须保留旧应用可读取的用户和角色表");

        if (upgraded) {
            assertEquals("legacy-operator", scalarString(connection,
                    "select name from wvp_user_role where id = ?", LEGACY_ROLE_ID));
            assertEquals("LEGACY", scalarString(connection,
                    "select status from wvp_user_api_key where id = ?", LEGACY_API_KEY_ID));
            assertEquals("legacy-plain-key", scalarString(connection,
                    "select api_key from wvp_user_api_key where id = ?", LEGACY_API_KEY_ID));
            assertNull(scalarObject(connection,
                    "select key_hash from wvp_user_api_key where id = ?", LEGACY_API_KEY_ID));
        }
    }

    private static void verifyClosuresAndRootScopes(Connection connection) throws SQLException {
        assertEquals(1, scalarLong(connection, """
                select count(*)
                  from wvp_dept d
                  join wvp_dept_closure c
                    on c.ancestor_id = d.id and c.descendant_id = d.id and c.depth = 0
                 where d.dept_code = 'ROOT' and d.parent_id = 0
                """));
        assertEquals(1, scalarLong(connection, """
                select count(*)
                  from wvp_resource_group g
                  join wvp_resource_group_closure c
                    on c.ancestor_id = g.id and c.descendant_id = g.id and c.depth = 0
                 where g.group_code = 'ROOT_VIDEO' and g.parent_id = 0
                """));
        assertEquals(0, scalarLong(connection, """
                select count(*)
                  from wvp_resource_group g
                 where not exists (
                     select 1 from wvp_resource_group_closure c
                      where c.ancestor_id = g.id and c.descendant_id = g.id and c.depth = 0
                 )
                """));
        assertEquals(1, scalarLong(connection, """
                select count(*) from wvp_role
                 where role_key = 'super_admin'
                   and system_role = 1
                   and dept_data_scope = 'ALL'
                   and video_scope_type = 'ALL'
                """));
    }

    private static void verifyApiKeyHashAndScopes(Connection connection, boolean upgraded) throws SQLException {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long userId = insertAndAssertGeneratedId(connection, "wvp_user", "username", "contract_user_" + suffix,
                """
                insert into wvp_user
                    (username, password, role_id, dept_id, nickname, create_time, update_time)
                values (?, ?, 1, 1, ?, current_timestamp, current_timestamp)
                """, "contract_user_" + suffix, "{bcrypt}contract", "契约用户");
        update(connection, "insert into wvp_user_role_rel (user_id, role_id) values (?, 1)", userId);

        String hash = (upgraded ? "a" : "b").repeat(64);
        long apiKeyId = insertAndAssertGeneratedId(connection, "wvp_user_api_key", "key_hash", hash,
                """
                insert into wvp_user_api_key
                    (user_id, app, key_hash, key_prefix, key_last4, status, authz_version)
                values (?, ?, ?, ?, ?, 'ACTIVE', 0)
                """, userId, "contract-app", hash, "wvp_contract", "0001");

        long permissionId = scalarLong(connection,
                "select id from wvp_menu where perms = ?", "video:channel:play");
        long rootGroupId = scalarLong(connection,
                "select id from wvp_resource_group where group_code = ?", "ROOT_VIDEO");
        update(connection, "insert into wvp_api_key_permission (api_key_id, menu_id) values (?, ?)",
                apiKeyId, permissionId);
        update(connection, """
                insert into wvp_api_key_resource_group (api_key_id, resource_group_id)
                values (?, ?)
                """, apiKeyId, rootGroupId);

        assertEquals(1, scalarLong(connection,
                "select count(*) from wvp_api_key_permission where api_key_id = ?", apiKeyId));
        assertEquals(1, scalarLong(connection,
                "select count(*) from wvp_api_key_resource_group where api_key_id = ?", apiKeyId));
        assertThrows(SQLException.class, () -> update(connection, """
                insert into wvp_user_api_key
                    (user_id, app, key_hash, key_prefix, key_last4, status, authz_version)
                values (?, ?, ?, ?, ?, 'ACTIVE', 0)
                """, userId, "duplicate-hash", hash, "duplicate", "0002"));
        assertThrows(SQLException.class, () -> update(connection,
                "insert into wvp_api_key_permission (api_key_id, menu_id) values (?, ?)",
                apiKeyId, permissionId));
        assertThrows(SQLException.class, () -> update(connection, """
                insert into wvp_api_key_resource_group (api_key_id, resource_group_id)
                values (?, ?)
                """, apiKeyId, rootGroupId));
    }

    private static void verifyCloudRecordSource(Connection connection) throws SQLException {
        assertEquals("LEGACY_UNBOUND", scalarString(connection,
                "select source_type from wvp_cloud_record where id = ?", LEGACY_CLOUD_RECORD_ID));
        assertNull(scalarObject(connection,
                "select source_id from wvp_cloud_record where id = ?", LEGACY_CLOUD_RECORD_ID));
        assertNull(scalarObject(connection,
                "select channel_id from wvp_cloud_record where id = ?", LEGACY_CLOUD_RECORD_ID));
        assertEquals(0, scalarLong(connection, """
                select count(*) from wvp_cloud_record
                 where channel_id is null and source_type <> 'LEGACY_UNBOUND'
                """));
    }

    private static void verifyGeneratedIdentifiers(Connection connection) throws SQLException {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        insertAndAssertGeneratedId(connection, "wvp_user_role", "name", "generated-role-" + suffix,
                "insert into wvp_user_role (name, authority) values (?, ?)",
                "generated-role-" + suffix, "generated");
        insertAndAssertGeneratedId(connection, "wvp_role", "role_key", "generated_role_" + suffix,
                """
                insert into wvp_role
                    (name, role_key, dept_data_scope, video_scope_type, status, system_role)
                values (?, ?, 'NONE', 'NONE', 0, 0)
                """, "生成角色", "generated_role_" + suffix);
        long deptId = insertAndAssertGeneratedId(connection, "wvp_dept", "dept_code", "DEPT_" + suffix,
                """
                insert into wvp_dept (parent_id, dept_name, dept_code, status, deleted)
                values (1, ?, ?, 0, 0)
                """, "契约部门", "DEPT_" + suffix);
        update(connection, """
                insert into wvp_dept_closure (ancestor_id, descendant_id, depth)
                values (1, ?, 1)
                """, deptId);
        update(connection, """
                insert into wvp_dept_closure (ancestor_id, descendant_id, depth)
                values (?, ?, 0)
                """, deptId, deptId);

        long groupId = insertAndAssertGeneratedId(connection, "wvp_resource_group", "group_code",
                "GROUP_" + suffix, """
                insert into wvp_resource_group
                    (parent_id, group_code, group_name, status)
                values (1, ?, ?, 0)
                """, "GROUP_" + suffix, "契约资源组");
        update(connection, """
                insert into wvp_resource_group_closure (ancestor_id, descendant_id, depth)
                values (1, ?, 1)
                """, groupId);
        update(connection, """
                insert into wvp_resource_group_closure (ancestor_id, descendant_id, depth)
                values (?, ?, 0)
                """, groupId, groupId);

        insertAndAssertGeneratedId(connection, "wvp_menu", "perms", "contract:test:" + suffix,
                """
                insert into wvp_menu
                    (parent_id, menu_name, menu_type, perms, visible, status, system_menu)
                values (0, ?, 'F', ?, 1, 0, 0)
                """, "契约权限", "contract:test:" + suffix);
    }

    private static void verifyResourceUniqueness(Connection connection) throws SQLException {
        long childGroupId = scalarLong(connection, """
                select max(id) from wvp_resource_group where group_code like 'GROUP_%'
                """);
        long resourceId = 9_000_000L + childGroupId;
        update(connection, """
                insert into wvp_resource_group_item (group_id, resource_type, resource_id)
                values (1, 'CHANNEL', ?)
                """, resourceId);
        update(connection, """
                insert into wvp_resource_group_item (group_id, resource_type, resource_id)
                values (?, 'CHANNEL', ?)
                """, childGroupId, resourceId);
        assertThrows(SQLException.class, () -> update(connection, """
                insert into wvp_resource_group_item (group_id, resource_type, resource_id)
                values (1, 'CHANNEL', ?)
                """, resourceId));
        assertEquals(2, scalarLong(connection, """
                select count(*) from wvp_resource_group_item
                 where resource_type = 'CHANNEL' and resource_id = ?
                """, resourceId));
        assertEquals(0, scalarLong(connection, """
                select count(*) from wvp_resource_group g
                 where not exists (
                     select 1 from wvp_resource_group_closure c
                      where c.ancestor_id = g.id and c.descendant_id = g.id and c.depth = 0
                 )
                """));
    }

    private static void verifyIndexes(Connection connection) throws SQLException {
        assertIndex(connection, "wvp_user", "dept_id", "status", "deleted");
        assertIndex(connection, "wvp_user_role_rel", "role_id", "user_id");
        assertIndex(connection, "wvp_resource_group_closure", "descendant_id", "ancestor_id");
        assertIndex(connection, "wvp_resource_group_item", "resource_type", "resource_id");
        assertIndex(connection, "wvp_role_resource_group", "resource_group_id", "role_id");
        assertIndex(connection, "wvp_user_api_key", "user_id", "status");
        assertIndex(connection, "wvp_api_key_permission", "menu_id", "api_key_id");
        assertIndex(connection, "wvp_api_key_resource_group", "resource_group_id", "api_key_id");
        assertIndex(connection, "wvp_cloud_record", "channel_id", "start_time");
    }

    private static long insertAndAssertGeneratedId(
            Connection connection,
            String table,
            String lookupColumn,
            String lookupValue,
            String sql,
            Object... arguments) throws SQLException {
        long previousMax = scalarLong(connection, "select coalesce(max(id), 0) from " + table);
        update(connection, sql, arguments);
        long generatedId = scalarLong(connection,
                "select id from " + table + " where " + lookupColumn + " = ?", lookupValue);
        assertTrue(generatedId > previousMax,
                () -> table + " 的 sequence/auto_increment 未越过已有最大 ID " + previousMax
                        + "，实际生成 " + generatedId);
        return generatedId;
    }

    private static void assertIndex(Connection connection, String table, String... expectedColumns)
            throws SQLException {
        List<String> expected = List.of(expectedColumns);
        Map<String, List<IndexedColumn>> indexes = readIndexes(connection, table);
        boolean found = indexes.values().stream()
                .map(columns -> columns.stream()
                        .sorted(Comparator.comparingInt(IndexedColumn::position))
                        .map(IndexedColumn::name)
                        .toList())
                .anyMatch(columns -> columns.size() >= expected.size()
                        && columns.subList(0, expected.size()).equals(expected));
        assertTrue(found, () -> table + " 缺少索引前缀 " + expected + "，实际索引: " + indexes);
    }

    private static Map<String, List<IndexedColumn>> readIndexes(Connection connection, String table)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> catalogs = nullableCandidates(connection.getCatalog());
        Set<String> schemas = nullableCandidates(connection.getSchema());
        Set<String> tableNames = Set.of(table, table.toUpperCase(Locale.ROOT));
        Map<String, List<IndexedColumn>> result = new HashMap<>();
        for (String catalog : catalogs) {
            for (String schema : schemas) {
                for (String tableName : tableNames) {
                    try (ResultSet resultSet = metadata.getIndexInfo(
                            catalog, schema, tableName, false, false)) {
                        while (resultSet.next()) {
                            String indexName = resultSet.getString("INDEX_NAME");
                            String columnName = resultSet.getString("COLUMN_NAME");
                            if (indexName == null || columnName == null) {
                                continue;
                            }
                            String key = indexName.toLowerCase(Locale.ROOT);
                            IndexedColumn column = new IndexedColumn(
                                    resultSet.getInt("ORDINAL_POSITION"),
                                    columnName.toLowerCase(Locale.ROOT));
                            List<IndexedColumn> columns = result.computeIfAbsent(
                                    key, ignored -> new ArrayList<>());
                            if (!columns.contains(column)) {
                                columns.add(column);
                            }
                        }
                    }
                    if (!result.isEmpty()) {
                        return result;
                    }
                }
            }
        }
        return result;
    }

    private static Set<String> nullableCandidates(String value) {
        Set<String> values = new LinkedHashSet<>();
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
        values.add(null);
        return values;
    }

    private static long scalarLong(Connection connection, String sql, Object... arguments)
            throws SQLException {
        Object value = scalarObject(connection, sql, arguments);
        assertNotNull(value, "查询必须返回数值: " + sql);
        return ((Number) value).longValue();
    }

    private static String scalarString(Connection connection, String sql, Object... arguments)
            throws SQLException {
        Object value = scalarObject(connection, sql, arguments);
        assertNotNull(value, "查询必须返回字符串: " + sql);
        return value.toString();
    }

    private static Object scalarObject(Connection connection, String sql, Object... arguments)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "查询必须返回一行: " + sql);
                return resultSet.getObject(1);
            }
        }
    }

    private static int update(Connection connection, String sql, Object... arguments)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            return statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... arguments) throws SQLException {
        for (int index = 0; index < arguments.length; index++) {
            statement.setObject(index + 1, arguments[index]);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean lineComment = false;
        boolean blockComment = false;
        String dollarTag = null;

        for (int index = 0; index < sql.length(); index++) {
            char character = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

            if (lineComment) {
                if (character == '\n') {
                    lineComment = false;
                    current.append(character);
                }
                continue;
            }
            if (blockComment) {
                if (character == '*' && next == '/') {
                    blockComment = false;
                    index++;
                }
                continue;
            }
            if (dollarTag != null) {
                if (sql.startsWith(dollarTag, index)) {
                    current.append(dollarTag);
                    index += dollarTag.length() - 1;
                    dollarTag = null;
                } else {
                    current.append(character);
                }
                continue;
            }
            if (!singleQuoted && !doubleQuoted && character == '-' && next == '-') {
                lineComment = true;
                index++;
                continue;
            }
            if (!singleQuoted && !doubleQuoted && character == '/' && next == '*') {
                blockComment = true;
                index++;
                continue;
            }
            if (!singleQuoted && !doubleQuoted && character == '$') {
                int tagEnd = sql.indexOf('$', index + 1);
                if (tagEnd > index) {
                    String candidate = sql.substring(index, tagEnd + 1);
                    if (candidate.matches("\\$[A-Za-z0-9_]*\\$")) {
                        dollarTag = candidate;
                        current.append(candidate);
                        index = tagEnd;
                        continue;
                    }
                }
            }
            if (!doubleQuoted && character == '\'' && !isEscaped(sql, index)) {
                if (singleQuoted && next == '\'') {
                    current.append(character).append(next);
                    index++;
                    continue;
                }
                singleQuoted = !singleQuoted;
            } else if (!singleQuoted && character == '"' && !isEscaped(sql, index)) {
                if (doubleQuoted && next == '"') {
                    current.append(character).append(next);
                    index++;
                    continue;
                }
                doubleQuoted = !doubleQuoted;
            }
            if (!singleQuoted && !doubleQuoted && character == ';') {
                addStatement(statements, current);
            } else {
                current.append(character);
            }
        }
        addStatement(statements, current);
        return statements;
    }

    private static boolean isEscaped(String sql, int index) {
        int backslashes = 0;
        for (int current = index - 1; current >= 0 && sql.charAt(current) == '\\'; current--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
        current.setLength(0);
    }

    private static String abbreviate(String sql) {
        String compact = sql.replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500) + "...";
    }

    private record IndexedColumn(int position, String name) {
    }
}
