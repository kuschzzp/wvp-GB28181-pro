package com.genersoft.iot.vmp.authorization.resourcegroup;

import com.genersoft.iot.vmp.authorization.resourcegroup.model.RoleVideoScope;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@Repository
public interface RoleResourceGroupMapper {

    @Select("""
            select id as roleId, video_scope_type as videoScopeType, status,
                   system_role as systemRole
              from wvp_role
             where id = #{roleId}
             for update
            """)
    RoleVideoScope findRoleForUpdate(@Param("roleId") long roleId);

    @Update("""
            update wvp_role
               set video_scope_type = #{videoScopeType},
                   update_by = #{operatorId},
                   update_time = #{updateTime}
             where id = #{roleId}
            """)
    int updateVideoScope(
            @Param("roleId") long roleId,
            @Param("videoScopeType") String videoScopeType,
            @Param("operatorId") Long operatorId,
            @Param("updateTime") LocalDateTime updateTime);

    @Delete("delete from wvp_role_resource_group where role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") long roleId);

    @Insert("""
            insert into wvp_role_resource_group (role_id, resource_group_id)
            values (#{roleId}, #{groupId})
            """)
    int insert(
            @Param("roleId") long roleId,
            @Param("groupId") long groupId);

    @Select("""
            select resource_group_id
              from wvp_role_resource_group
             where role_id = #{roleId}
             order by resource_group_id
            """)
    List<Long> findGroupIdsByRoleId(@Param("roleId") long roleId);

    @Select("select count(*) from wvp_role_resource_group where resource_group_id = #{groupId}")
    int countByGroupId(@Param("groupId") long groupId);

    @Insert("""
            insert into wvp_role_resource_group (role_id, resource_group_id)
            select source.role_id, #{targetGroupId}
              from wvp_role_resource_group source
             where source.resource_group_id = #{sourceGroupId}
               and not exists (
                   select 1
                     from wvp_role_resource_group target
                    where target.role_id = source.role_id
                      and target.resource_group_id = #{targetGroupId}
               )
            """)
    int migrateToGroup(
            @Param("sourceGroupId") long sourceGroupId,
            @Param("targetGroupId") long targetGroupId);

    @Select("""
            select distinct ur.user_id
              from wvp_user_role_rel ur
              join wvp_role_resource_group rrg on rrg.role_id = ur.role_id
              join wvp_resource_group_closure path
                on path.ancestor_id = rrg.resource_group_id
             where path.descendant_id = #{groupId}
            """)
    List<Long> findUserIdsCoveringGroup(@Param("groupId") long groupId);

    @Select("""
            select distinct ur.user_id
              from wvp_user_role_rel ur
              join wvp_role_resource_group rrg on rrg.role_id = ur.role_id
             where exists (
                 select 1
                   from wvp_resource_group_closure path
                  where (path.ancestor_id = rrg.resource_group_id
                         and path.descendant_id = #{groupId})
                     or (path.ancestor_id = #{groupId}
                         and path.descendant_id = rrg.resource_group_id)
             )
            """)
    List<Long> findUserIdsRelatedToGroup(@Param("groupId") long groupId);
}
