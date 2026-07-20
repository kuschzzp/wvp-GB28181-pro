package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoRoleScopeGrant;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeResourceRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VideoScopeProviderTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private VideoScopeMapper mapper;

    private final VideoScopeProvider provider = new VideoScopeProvider();

    @BeforeEach
    void loadSchemaAndFixtures() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"))
                .execute(dataSource);
        insertRoles();
        insertGroupsAndClosure();
        insertBindingsAndItems();
    }

    @Test
    void providerUsesBoundRoleIdsAndDefensiveStatusPredicates() {
        String roleSql = provider.findEnabledRoleScopes(
                Map.of("roleIds", Set.of(17L, 29L)));
        String resourceSql = provider.findEnabledResources(
                Map.of("roleIds", Set.of(17L, 29L)));

        assertTrue(roleSql.contains("collection=\"roleIds\""));
        assertTrue(roleSql.contains("#{roleId}"));
        assertTrue(roleSql.contains("r.status = 0"));
        assertFalse(roleSql.contains("17"));
        assertFalse(roleSql.contains("${"));

        assertTrue(resourceSql.contains("wvp_resource_group_closure"));
        assertTrue(resourceSql.contains("r.video_scope_type = 'CUSTOM'"));
        assertTrue(resourceSql.contains("ancestor.status &lt;&gt; 0"));
        assertTrue(resourceSql.contains("select distinct"));
        assertFalse(resourceSql.contains("29"));
        assertFalse(resourceSql.contains("${"));
        assertThrows(IllegalArgumentException.class,
                () -> provider.findEnabledResources(Map.of("roleIds", Set.of())));
    }

    @Test
    void enabledRoleScopesExcludeDisabledRolesButKeepAllCustomAndNoneModes() {
        assertEquals(List.of(
                        new VideoRoleScopeGrant(10L, "CUSTOM"),
                        new VideoRoleScopeGrant(12L, "NONE"),
                        new VideoRoleScopeGrant(13L, "ALL")),
                mapper.findEnabledRoleScopes(Set.of(10L, 11L, 12L, 13L)));
    }

    @Test
    void parentBindingIncludesEnabledChildrenAndDeduplicatesOverlappingBindings() {
        assertEquals(List.of(
                        new VideoScopeResourceRow(VideoResourceType.CHANNEL, 1001L),
                        new VideoScopeResourceRow(VideoResourceType.GB_DEVICE, 1000L)),
                mapper.findEnabledResources(Set.of(10L, 11L, 12L)));
    }

    @Test
    void disabledGroupOrAncestorRemovesTheWholeAffectedSubtree() {
        assertTrue(mapper.findEnabledResources(Set.of(14L)).isEmpty());

        jdbcTemplate.update("update wvp_resource_group set status = 1 where id = 100");

        assertTrue(mapper.findEnabledResources(Set.of(10L)).isEmpty());
    }

    private void insertRoles() {
        jdbcTemplate.update("""
                insert into wvp_role
                    (id, name, role_key, role_sort, dept_data_scope, video_scope_type,
                     status, system_role)
                values
                    (10, 'play role', 'play_role', 10, 'NONE', 'CUSTOM', 0, 0),
                    (11, 'disabled role', 'disabled_role', 11, 'NONE', 'CUSTOM', 1, 0),
                    (12, 'none role', 'none_role', 12, 'NONE', 'NONE', 0, 0),
                    (13, 'all role', 'all_role', 13, 'NONE', 'ALL', 0, 0),
                    (14, 'blocked child role', 'blocked_child_role', 14, 'NONE', 'CUSTOM', 0, 0)
                """);
    }

    private void insertGroupsAndClosure() {
        jdbcTemplate.update("""
                insert into wvp_resource_group
                    (id, parent_id, group_code, group_name, order_num, status)
                values
                    (100, 0, 'BUILDING_1', '一号楼', 1, 0),
                    (101, 100, 'BUILDING_1_F1', '一号楼一层', 1, 0),
                    (102, 100, 'BUILDING_1_DISABLED', '停用区域', 2, 1),
                    (103, 102, 'BUILDING_1_DISABLED_CHILD', '停用区域子组', 1, 0)
                """);
        jdbcTemplate.update("""
                insert into wvp_resource_group_closure (ancestor_id, descendant_id, depth)
                values
                    (100, 100, 0),
                    (101, 101, 0),
                    (100, 101, 1),
                    (102, 102, 0),
                    (100, 102, 1),
                    (103, 103, 0),
                    (102, 103, 1),
                    (100, 103, 2)
                """);
    }

    private void insertBindingsAndItems() {
        jdbcTemplate.update("""
                insert into wvp_role_resource_group (role_id, resource_group_id)
                values (10, 100), (10, 101), (11, 101), (14, 103)
                """);
        jdbcTemplate.update("""
                insert into wvp_resource_group_item
                    (group_id, resource_type, resource_id)
                values
                    (100, 'GB_DEVICE', 1000),
                    (100, 'CHANNEL', 1001),
                    (101, 'CHANNEL', 1001),
                    (102, 'STREAM_PUSH', 1002),
                    (103, 'STREAM_PROXY', 1003)
                """);
    }
}
