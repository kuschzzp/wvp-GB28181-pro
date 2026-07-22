package com.genersoft.iot.vmp.auth;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
@ActiveProfiles("test")
class MigrationContractTest {

    private static final Set<String> ENABLED_ROUTE_COMPONENTS = Set.of(
            "Layout",
            "alarm/index",
            "channel/group/index",
            "channel/index",
            "channel/region/index",
            "cloud-record/index",
            "dashboard/index",
            "device/index",
            "jt1078/index",
            "live/index",
            "media-server/index",
            "operations/log/index",
            "operations/real-log/index",
            "operations/system/index",
            "platform/index",
            "position/index",
            "proxy/index",
            "push/index",
            "record-plan/index",
            "system/dept/index",
            "system/menu/index",
            "system/resource-group/index",
            "system/role/index",
            "system/user/index");

    private static final Set<String> REQUIRED_PERMISSIONS = Set.of(
            "system:user:list", "system:user:add", "system:user:edit", "system:user:remove",
            "system:user:resetPwd", "system:user:changeStatus",
            "system:role:list", "system:role:add", "system:role:edit", "system:role:remove",
            "system:role:changeStatus", "system:role:assign",
            "system:dept:list", "system:dept:add", "system:dept:edit", "system:dept:remove",
            "system:menu:list", "system:menu:add", "system:menu:edit", "system:menu:remove",
            "system:apikey:list", "system:apikey:add", "system:apikey:rotate", "system:apikey:disable",
            "system:apikey:remove", "system:apikey:manageOthers",
            "system:resource-group:list", "system:resource-group:add", "system:resource-group:edit",
            "system:resource-group:remove", "system:resource-group:assign",
            "operations:log:view", "operations:log:download", "operations:system:view",
            "video:device:list", "video:device:add", "video:device:edit", "video:device:remove",
            "video:device:sync", "video:device:config",
            "video:channel:list", "video:channel:edit", "video:channel:play", "video:channel:stop",
            "video:channel:talk", "video:channel:broadcast",
            "video:ptz:control", "video:ptz:preset", "video:ptz:cruise",
            "video:record:list", "video:record:play", "video:record:download", "video:record:remove",
            "video:cloud-record:list", "video:cloud-record:play", "video:cloud-record:download",
            "video:cloud-record:remove", "video:cloud-record:collect",
            "video:alarm:list", "video:alarm:remove", "video:alarm:clear",
            "video:position:list", "video:position:subscribe",
            "video:proxy:list", "video:proxy:add", "video:proxy:edit", "video:proxy:play",
            "video:proxy:stop", "video:proxy:remove",
            "video:push:list", "video:push:add", "video:push:edit", "video:push:play",
            "video:push:stop", "video:push:remove",
            "video:jt:list", "video:jt:edit", "video:jt:play", "video:jt:talk", "video:jt:ptz",
            "video:jt:record", "video:jt:control",
            "video:platform:list", "video:platform:manage", "video:platform:share-channel",
            "video:media-server:list", "video:media-server:manage",
            "video:rtp:manage", "video:ps:manage",
            "video:stream:share", "video:stream:revoke-share"
    );

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
    void createsExpandIdentitySchemaWithoutDestroyingLegacyRoleTable() {
        assertEquals(1, count("select count(*) from wvp_user_role where id = 1"));
        assertEquals(1, count("select count(*) from wvp_role where role_key = 'super_admin' and system_role = 1"));
        assertEquals(1, count("select count(*) from wvp_user_role_rel where user_id = 1 and role_id = 1"));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "select token_version from wvp_user where id = 1", Long.class));
        assertEquals("超级管理员", jdbcTemplate.queryForObject(
                "select nickname from wvp_user where id = 1", String.class));
    }

    @Test
    void seedsEveryPermissionFromTheApprovedDesign() {
        List<String> permissions = jdbcTemplate.queryForList(
                "select perms from wvp_menu where perms is not null and status = 0", String.class);

        assertEquals(REQUIRED_PERMISSIONS, new HashSet<>(permissions));
        assertEquals(REQUIRED_PERMISSIONS.size(), permissions.size(), "权限编码必须唯一");
    }

    @Test
    void onlyPublishesRouteComponentsImplementedByTheCurrentFrontendStage() {
        List<String> components = jdbcTemplate.queryForList("""
                select component
                  from wvp_menu
                 where status = 0
                   and visible = 0
                   and menu_type in ('M', 'C')
                """, String.class);

        assertEquals(26, components.size(), "当前阶段应发布 3 个目录和 23 个页面路由");
        assertEquals(ENABLED_ROUTE_COMPONENTS, new HashSet<>(components));
    }

    @Test
    void publishesSystemManagementRoutesWithRelativeChildPaths() {
        List<String> routes = jdbcTemplate.queryForList("""
                select component || ':' || path
                  from wvp_menu
                 where id in (110, 120, 130, 140, 160)
                   and status = 0
                 order by id
                """, String.class);

        assertEquals(List.of(
                "system/user/index:user",
                "system/role/index:role",
                "system/dept/index:dept",
                "system/menu/index:menu",
                "system/resource-group/index:resource-group"), routes);
        assertEquals(1, count("select count(*) from wvp_menu where id = 150 and status = 1"));
    }

    @Test
    void systemManagementMenuIconsHaveFrontendSvgAssets() throws IOException {
        List<String> icons = jdbcTemplate.queryForList("""
                select distinct icon
                  from wvp_menu
                 where id in (110, 120, 130, 140, 160)
                   and icon is not null
                   and icon <> ''
                   and icon not like 'el-icon%'
                """, String.class);

        for (String icon : icons) {
            assertTrue(Files.exists(Path.of("web/src/icons/svg", icon + ".svg")),
                    "缺少前端菜单图标资源: web/src/icons/svg/" + icon + ".svg");
        }
    }

    @Test
    void seedDataCanBeAppliedTwiceWithoutDuplicatingRelations() {
        new ResourceDatabasePopulator(new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);

        assertEquals(1, count("select count(*) from wvp_user_role_rel where user_id = 1 and role_id = 1"));
        assertEquals(REQUIRED_PERMISSIONS.size(), count(
                "select count(*) from wvp_menu where perms is not null"));
    }

    @Test
    void legacyCompatibilityRoleKeepsOldBusinessAccessWithoutSystemAdministration() {
        Set<String> permissions = new HashSet<>(jdbcTemplate.queryForList("""
                select distinct m.perms
                  from wvp_user_role_rel ur
                  join wvp_role_menu rm on rm.role_id = ur.role_id
                  join wvp_menu m on m.id = rm.menu_id
                 where ur.user_id = 99
                   and m.status = 0
                   and m.perms is not null
                """, String.class));
        Set<String> routeComponents = new HashSet<>(jdbcTemplate.queryForList("""
                select distinct m.component
                  from wvp_user_role_rel ur
                  join wvp_role_menu rm on rm.role_id = ur.role_id
                  join wvp_menu m on m.id = rm.menu_id
                 where ur.user_id = 99
                   and m.status = 0
                   and m.visible = 0
                   and m.menu_type in ('M', 'C')
                """, String.class));

        assertTrue(permissions.contains("video:channel:play"));
        assertTrue(permissions.contains("operations:log:view"));
        assertTrue(permissions.contains("system:user:list"));
        assertTrue(permissions.contains("system:apikey:add"));
        assertFalse(permissions.contains("system:apikey:manageOthers"));
        assertFalse(permissions.contains("system:role:add"));
        assertTrue(routeComponents.contains("device/index"));
        assertTrue(routeComponents.contains("system/user/index"));
        assertFalse(routeComponents.contains("system/role/index"));
    }

    @Test
    void configuredLegacyRoleIsNotGrantedCompatibilityPermissionsAgain() {
        jdbcTemplate.update("update wvp_role set update_by = 1 where id = 99");
        jdbcTemplate.update("delete from wvp_role_menu where role_id = 99");

        new ResourceDatabasePopulator(new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);

        assertEquals(0, count("select count(*) from wvp_role_menu where role_id = 99"));
    }

    @Test
    void relationAndAuditColumnsUseBigintIdentifiers() {
        List<String> bigintColumns = jdbcTemplate.queryForList("""
                select lower(table_name) || '.' || lower(column_name)
                 from information_schema.columns
                 where lower(data_type) = 'bigint'
                   and lower(table_name) || '.' || lower(column_name) in (
                       'wvp_user.id',
                       'wvp_user_role_rel.user_id',
                       'wvp_user_role_rel.role_id',
                       'wvp_authz_audit.principal_id',
                       'wvp_authz_audit.principal_dept_id',
                       'wvp_authz_audit.resource_id')
                """, String.class);

        assertEquals(6, bigintColumns.size());
        assertTrue(bigintColumns.contains("wvp_user.id"));
    }

    @Test
    void apiKeySchemaSeparatesLegacySecretFromHmacAndAbsoluteExpiry() {
        Set<String> columns = new HashSet<>(jdbcTemplate.queryForList("""
                select lower(column_name)
                  from information_schema.columns
                 where lower(table_name) = 'wvp_user_api_key'
                """, String.class));

        assertTrue(columns.containsAll(Set.of(
                "api_key", "key_hash", "key_prefix", "key_last4", "expired_at",
                "status", "ip_allowlist", "authz_version", "last_used_at")));
        assertEquals("BIGINT", jdbcTemplate.queryForObject("""
                select upper(data_type)
                  from information_schema.columns
                 where lower(table_name) = 'wvp_user_api_key'
                  and lower(column_name) = 'expired_at'
                """, String.class));
    }

    @Test
    void alarmRouteHasBackingAlarmTable() throws IOException {
        Set<String> columns = new HashSet<>(jdbcTemplate.queryForList("""
                select lower(column_name)
                  from information_schema.columns
                 where lower(table_name) = 'wvp_alarm'
                """, String.class));

        assertTrue(columns.containsAll(Set.of(
                "id", "channel_id", "description", "snap_path", "record_path",
                "longitude", "latitude", "alarm_type", "alarm_time")));
        assertEquals(0, count("select count(*) from wvp_alarm"));
    }

    @Test
    void bootstrapAndMigrationScriptsCreateBackingAlarmTable() throws IOException {
        List<String> schemaFiles = List.of(
                "docker/mysql/db/wvp.sql",
                "数据库/2.8.0-h2/h2-schema.sql",
                "数据库/2.8.0/初始化-mysql-2.8.0.sql",
                "数据库/2.8.0/更新-mysql-2.8.0.sql",
                "数据库/2.8.0/初始化-postgresql-2.8.0.sql",
                "数据库/2.8.0/更新-postgresql-2.8.0.sql",
                "数据库/2.8.0/初始化-kingbase-2.8.0.sql",
                "数据库/2.8.0/更新-kingbase-2.8.0.sql");

        for (String schemaFile : schemaFiles) {
            String sql = Files.readString(Path.of(schemaFile)).toLowerCase();
            assertTrue(sql.contains("create table") && sql.contains("wvp_alarm"), schemaFile);
            assertTrue(sql.contains("channel_id") && sql.contains("alarm_time"), schemaFile);
        }
    }

    @Test
    void apiKeyScopeRelationsEnforceUniquenessAndCascadeWithRevokedStatus() {
        jdbcTemplate.update("""
                insert into wvp_user_api_key
                    (id, user_id, app, key_hash, key_prefix, key_last4, status,
                     ip_allowlist, authz_version)
                values (500, 1, 'integration', 'scope-hash', 'wvp_scope', 'cope',
                        'REVOKED', '10.0.0.0/8,2001:db8::/32', 0)
                """);
        jdbcTemplate.update("""
                insert into wvp_api_key_permission (api_key_id, menu_id)
                select 500, id from wvp_menu where perms = 'video:channel:play'
                """);
        jdbcTemplate.update("""
                insert into wvp_api_key_resource_group (api_key_id, resource_group_id)
                values (500, 1)
                """);

        assertEquals("10.0.0.0/8,2001:db8::/32", jdbcTemplate.queryForObject(
                "select ip_allowlist from wvp_user_api_key where id = 500", String.class));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                insert into wvp_api_key_resource_group (api_key_id, resource_group_id)
                values (500, 1)
                """));

        jdbcTemplate.update("delete from wvp_user_api_key where id = 500");
        assertEquals(0, count(
                "select count(*) from wvp_api_key_permission where api_key_id = 500"));
        assertEquals(0, count(
                "select count(*) from wvp_api_key_resource_group where api_key_id = 500"));
    }

    @Test
    void mysqlExpandNormalizesLegacySerialAndGuardsRepeatableIndexes() throws IOException {
        String sql = Files.readString(Path.of("数据库/2.8.0/更新-mysql-2.8.0.sql"));
        String normalized = sql.toLowerCase();

        assertTrue(normalized.contains(
                "alter table wvp_user modify column id bigint not null auto_increment"));
        assertTrue(normalized.contains("alter table wvp_user modify column role_id bigint null"));
        assertTrue(normalized.contains(
                "alter table wvp_user_api_key modify column user_id bigint null"));
        assertTrue(normalized.contains("information_schema.statistics"));
        assertTrue(normalized.indexOf("call wvp_preflight_rbac_expand()")
                < normalized.indexOf("alter table wvp_user modify column id"));
        assertTrue(normalized.lines().noneMatch(line -> line.strip().equals(
                "create unique index uk_api_key_hash on wvp_user_api_key (key_hash);")));
        assertTrue(normalized.lines().noneMatch(line -> line.strip().equals(
                "create index idx_api_key_owner_status on wvp_user_api_key (user_id, status);")));
    }

    @Test
    void everyDialectNormalizesLegacyZeroExpiryToNull() throws IOException {
        for (String dialect : List.of("mysql", "postgresql", "kingbase")) {
            String sql = Files.readString(Path.of(
                    "数据库/2.8.0/更新-" + dialect + "-2.8.0.sql"));
            assertTrue(sql.toLowerCase().contains("expired_at = nullif(k.expired_at, 0)"), dialect);
        }
    }

    @Test
    void everyDialectPublishesOnlyImplementedSystemManagementPages() throws IOException {
        for (String phase : List.of("初始化", "更新")) {
            for (String dialect : List.of("mysql", "postgresql", "kingbase")) {
                String schemaFile = "数据库/2.8.0/" + phase + "-" + dialect + "-2.8.0.sql";
                String sql = Files.readString(Path.of(schemaFile)).toLowerCase();

                assertTrue(sql.contains("'systemrole', 'role', 'system/role/index', 'group'"), schemaFile);
                assertTrue(sql.contains("'systemdept', 'dept', 'system/dept/index'"), schemaFile);
                assertTrue(sql.contains("'systemmenu', 'menu', 'system/menu/index', 'table'"), schemaFile);
                assertFalse(sql.contains("'system/role/index', 'peoples'"), schemaFile);
                assertFalse(sql.contains("'system/menu/index', 'tree-table'"), schemaFile);
                assertTrue(sql.contains(
                        "'system/api-key/index', 'key', 5, 0, 1, 0, 0, 1"), schemaFile);
                assertTrue(sql.contains(
                        "'system/resource-group/index', 'nested', 6, 0, 0, 0, 0, 1"), schemaFile);
                assertTrue(sql.contains(
                        "status = case when id in (150, 330, 340, 430, 440) then 1 else status end"),
                        schemaFile);
                assertFalse(sql.contains(
                        "status = case when id in (120, 130, 140, 150, 160"),
                        schemaFile);
            }
        }
    }

    @Test
    void everyDialectIndexesAuthorizationAuditDepartmentSnapshot() throws IOException {
        List<String> schemaFiles = List.of(
                "数据库/2.8.0-h2/h2-schema.sql",
                "数据库/2.8.0/初始化-mysql-2.8.0.sql",
                "数据库/2.8.0/更新-mysql-2.8.0.sql",
                "数据库/2.8.0/初始化-postgresql-2.8.0.sql",
                "数据库/2.8.0/更新-postgresql-2.8.0.sql",
                "数据库/2.8.0/初始化-kingbase-2.8.0.sql",
                "数据库/2.8.0/更新-kingbase-2.8.0.sql");

        for (String schemaFile : schemaFiles) {
            String sql = Files.readString(Path.of(schemaFile)).toLowerCase();
            assertTrue(sql.contains("idx_authz_audit_dept"), schemaFile);
            assertTrue(sql.contains("(principal_dept_id, create_time)"), schemaFile);
        }
    }

    @Test
    void everyDialectDefinesEquivalentApiKeyScopeSchema() throws IOException {
        List<String> schemaFiles = List.of(
                "数据库/2.8.0-h2/h2-schema.sql",
                "数据库/2.8.0/初始化-mysql-2.8.0.sql",
                "数据库/2.8.0/更新-mysql-2.8.0.sql",
                "数据库/2.8.0/初始化-postgresql-2.8.0.sql",
                "数据库/2.8.0/更新-postgresql-2.8.0.sql",
                "数据库/2.8.0/初始化-kingbase-2.8.0.sql",
                "数据库/2.8.0/更新-kingbase-2.8.0.sql");

        for (String schemaFile : schemaFiles) {
            String sql = Files.readString(Path.of(schemaFile)).toLowerCase();
            assertTrue(sql.contains("ip_allowlist"), schemaFile);
            assertTrue(sql.contains("wvp_api_key_permission"), schemaFile);
            assertTrue(sql.contains("wvp_api_key_resource_group"), schemaFile);
            assertTrue(sql.contains("idx_api_key_permission_menu"), schemaFile);
            assertTrue(sql.contains("idx_api_key_resource_group_group"), schemaFile);
            assertTrue(sql.contains("references wvp_user_api_key (id) on delete cascade"),
                    schemaFile);
            assertTrue(sql.contains("references wvp_menu (id) on delete cascade"), schemaFile);
            assertTrue(sql.contains("references wvp_resource_group (id) on delete cascade"),
                    schemaFile);
        }
    }

    @Test
    void everyDialectSeedsRepeatSafeLegacyCompatibilityGrants() throws IOException {
        for (String dialect : List.of("mysql", "postgresql", "kingbase")) {
            String sql = Files.readString(Path.of(
                    "数据库/2.8.0/更新-" + dialect + "-2.8.0.sql")).toLowerCase();
            assertTrue(sql.contains("insert") && sql.contains("into wvp_role_menu"), dialect);
            assertTrue(sql.contains("legacy_role_[0-9]+"), dialect);
            assertTrue(sql.contains("r.update_by is null"), dialect);
            assertTrue(sql.contains("system:apikey:manageothers")
                    && !compatibilityGrantSection(sql).contains("system:apikey:manageothers"), dialect);
        }
    }

    @Test
    void legacyRoleColumnsSupportTheNewRoleContractDuringDualWrite() throws IOException {
        List<Long> lengths = jdbcTemplate.queryForList("""
                select character_maximum_length
                  from information_schema.columns
                 where lower(table_name) = 'wvp_user_role'
                   and lower(column_name) in ('name', 'authority')
                 order by lower(column_name)
                """, Long.class);
        assertEquals(List.of(64L, 64L), lengths);

        String mysql = Files.readString(Path.of("数据库/2.8.0/更新-mysql-2.8.0.sql"))
                .toLowerCase();
        assertTrue(mysql.contains("modify column name varchar(64)"));
        assertTrue(mysql.contains("modify column authority varchar(64)"));
        for (String dialect : List.of("postgresql", "kingbase")) {
            String sql = Files.readString(Path.of(
                    "数据库/2.8.0/更新-" + dialect + "-2.8.0.sql")).toLowerCase();
            assertTrue(sql.contains("alter column name type varchar(64)"), dialect);
            assertTrue(sql.contains("alter column authority type varchar(64)"), dialect);
        }
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private String compatibilityGrantSection(String sql) {
        int start = sql.indexOf("普通旧角色先显式继承");
        int end = sql.indexOf("do $$", start);
        if (end < 0) {
            end = sql.indexOf("drop procedure if exists wvp_validate_rbac_expand", start);
        }
        return start < 0 || end < 0 ? "" : sql.substring(start, end);
    }
}
