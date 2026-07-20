package com.genersoft.iot.vmp.authorization.resourcegroup;

import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.CreateResourceGroupCommand;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.UpdateResourceGroupCommand;
import com.genersoft.iot.vmp.authorization.resourcegroup.impl.ResourceGroupServiceImpl;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeType;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ResourceGroupServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ResourceGroupServiceTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ResourceGroupService service;

    @MockitoBean
    private ResourceResolverRegistry resolverRegistry;

    @MockitoBean
    private TokenVersionService tokenVersionService;

    @BeforeEach
    void loadSchema() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
        when(resolverRegistry.requireExisting(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsMovesAndRejectsCyclesWithoutMixingDepartmentTree() {
        long buildingA = create(1, "BUILDING_A", "一号楼", 0);
        long floorA1 = create(buildingA, "FLOOR_A1", "一层", 0);
        long buildingB = create(1, "BUILDING_B", "二号楼", 0);

        assertEquals(2, depth(1, floorA1));
        assertThrows(IllegalArgumentException.class,
                () -> service.move(buildingA, floorA1, 1L));
        assertEquals(1L, parentId(buildingA));

        service.move(buildingA, buildingB, 1L);

        assertEquals(buildingB, parentId(buildingA));
        assertEquals(1, depth(buildingB, buildingA));
        assertEquals(2, depth(buildingB, floorA1));
        assertEquals(3, depth(1, floorA1));
        assertEquals(1, count("select count(*) from wvp_dept where dept_code = 'ROOT'"));
    }

    @Test
    void protectsRootAndRejectsChildrenUnderDisabledGroups() {
        long building = create(1, "BUILDING", "园区", 0);
        service.update(new UpdateResourceGroupCommand(
                building, 1, "BUILDING", "停用园区", 0, 1, null, 1L));

        assertThrows(IllegalArgumentException.class,
                () -> create(building, "CAMERAS", "摄像头", 0));
        assertThrows(AccessDeniedException.class,
                () -> service.update(new UpdateResourceGroupCommand(
                        1, 0, "CHANGED_ROOT", "错误根组", 0, 1, null, 1L)));
        assertThrows(AccessDeniedException.class, () -> service.move(1, building, 1L));
        assertThrows(AccessDeniedException.class, () -> service.delete(1, null, 1L));
    }

    @Test
    void validatesItemsDeduplicatesInsideGroupAndAllowsCrossGroupReuse() {
        long buildingA = create(1, "ITEM_A", "资源组A", 0);
        long buildingB = create(1, "ITEM_B", "资源组B", 0);
        VideoResourceRef channel = new VideoResourceRef(VideoResourceType.CHANNEL, 9001);

        service.replaceItems(buildingA, Set.of(channel), 1L);
        service.replaceItems(buildingA, Set.of(channel), 1L);
        service.replaceItems(buildingB, Set.of(channel), 1L);

        assertEquals(2, count("""
                select count(*)
                  from wvp_resource_group_item
                 where resource_type = 'CHANNEL' and resource_id = 9001
                """));
        assertThrows(IllegalArgumentException.class, () -> service.replaceItems(
                buildingA,
                Set.of(new VideoResourceRef(VideoResourceType.CLOUD_RECORD, 81)),
                1L));
        verify(resolverRegistry, never()).requireExisting(
                new VideoResourceRef(VideoResourceType.CLOUD_RECORD, 81));
    }

    @Test
    void replacesRoleGroupsClearsNonCustomRelationsAndProtectsSystemRole() {
        long enabled = create(1, "ROLE_ENABLED", "启用资源组", 0);
        long disabled = create(1, "ROLE_DISABLED", "停用资源组", 1);

        service.replaceRoleGroups(99, VideoScopeType.CUSTOM, Set.of(enabled), 1L);
        assertEquals("CUSTOM", roleScope(99));
        assertEquals(1, relationCount(99, enabled));
        verify(tokenVersionService).bumpUsersByRoleId(99, "ROLE_VIDEO_SCOPE_CHANGED");

        service.replaceRoleGroups(99, VideoScopeType.NONE, Set.of(enabled), 1L);
        assertEquals("NONE", roleScope(99));
        assertEquals(0, relationCount(99, enabled));

        assertThrows(IllegalArgumentException.class,
                () -> service.replaceRoleGroups(
                        99, VideoScopeType.CUSTOM, Set.of(disabled), 1L));
        assertThrows(AccessDeniedException.class,
                () -> service.replaceRoleGroups(1, VideoScopeType.NONE, Set.of(), 1L));
        assertEquals("ALL", roleScope(1));
    }

    @Test
    void rollsBackRoleScopeAndRelationsWhenTokenVersionUpdateFails() {
        long groupId = create(1, "ROLLBACK", "回滚资源组", 0);
        doThrow(new IllegalStateException("token version failed"))
                .when(tokenVersionService)
                .bumpUsersByRoleId(99, "ROLE_VIDEO_SCOPE_CHANGED");

        assertThrows(IllegalStateException.class,
                () -> service.replaceRoleGroups(
                        99, VideoScopeType.CUSTOM, Set.of(groupId), 1L));

        assertEquals("NONE", roleScope(99));
        assertEquals(0, relationCount(99, groupId));
    }

    @Test
    void requiresMigrationBeforeDeletingPopulatedGroup() {
        long source = create(1, "DELETE_SOURCE", "待删除资源组", 0);
        long target = create(1, "DELETE_TARGET", "迁移目标", 0);
        VideoResourceRef channel = new VideoResourceRef(VideoResourceType.CHANNEL, 7001);
        service.replaceItems(source, Set.of(channel), 1L);
        service.replaceRoleGroups(99, VideoScopeType.CUSTOM, Set.of(source), 1L);

        assertThrows(IllegalStateException.class, () -> service.delete(source, null, 1L));

        service.delete(source, target, 1L);

        assertEquals(0, count("select count(*) from wvp_resource_group where id = " + source));
        assertEquals(1, count("""
                select count(*) from wvp_resource_group_item
                 where group_id = %d and resource_type = 'CHANNEL' and resource_id = 7001
                """.formatted(target)));
        assertEquals(1, relationCount(99, target));
        assertEquals("CUSTOM", roleScope(99));
    }

    private long create(long parentId, String code, String name, int status) {
        return service.create(new CreateResourceGroupCommand(
                parentId, code, name, 0, status, null, 1L));
    }

    private long parentId(long groupId) {
        return jdbcTemplate.queryForObject(
                "select parent_id from wvp_resource_group where id = ?",
                Long.class,
                groupId);
    }

    private int depth(long ancestorId, long descendantId) {
        return jdbcTemplate.queryForObject("""
                select depth from wvp_resource_group_closure
                 where ancestor_id = ? and descendant_id = ?
                """, Integer.class, ancestorId, descendantId);
    }

    private String roleScope(long roleId) {
        return jdbcTemplate.queryForObject(
                "select video_scope_type from wvp_role where id = ?", String.class, roleId);
    }

    private int relationCount(long roleId, long groupId) {
        return jdbcTemplate.queryForObject("""
                select count(*) from wvp_role_resource_group
                 where role_id = ? and resource_group_id = ?
                """, Integer.class, roleId, groupId);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
