package com.genersoft.iot.vmp.authorization.resourcegroup;

import com.genersoft.iot.vmp.authorization.resourcegroup.model.ResourceGroupItem;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@Repository
public interface ResourceGroupItemMapper {

    @Insert("""
            insert into wvp_resource_group_item
                (group_id, resource_type, resource_id, create_by, create_time)
            values
                (#{groupId}, #{resourceType}, #{resourceId}, #{createBy}, #{createTime})
            """)
    int insert(ResourceGroupItem item);

    @Delete("delete from wvp_resource_group_item where group_id = #{groupId}")
    int deleteByGroupId(@Param("groupId") long groupId);

    @Select("select count(*) from wvp_resource_group_item where group_id = #{groupId}")
    int countByGroupId(@Param("groupId") long groupId);

    @Select("""
            select id, group_id as groupId, resource_type as resourceType,
                   resource_id as resourceId, create_by as createBy, create_time as createTime
              from wvp_resource_group_item
             where group_id = #{groupId}
             order by resource_type, resource_id
            """)
    List<ResourceGroupItem> findByGroupId(@Param("groupId") long groupId);

    @Select("""
            select group_id
              from wvp_resource_group_item
             where resource_type = #{resourceType}
               and resource_id = #{resourceId}
             order by group_id
            """)
    List<Long> findGroupIdsByResource(
            @Param("resourceType") VideoResourceType resourceType,
            @Param("resourceId") long resourceId);

    @Insert("""
            insert into wvp_resource_group_item
                (group_id, resource_type, resource_id, create_by, create_time)
            select #{targetGroupId}, source.resource_type, source.resource_id,
                   #{operatorId}, #{createTime}
              from wvp_resource_group_item source
             where source.group_id = #{sourceGroupId}
               and not exists (
                   select 1
                     from wvp_resource_group_item target
                    where target.group_id = #{targetGroupId}
                      and target.resource_type = source.resource_type
                      and target.resource_id = source.resource_id
               )
            """)
    int migrateToGroup(
            @Param("sourceGroupId") long sourceGroupId,
            @Param("targetGroupId") long targetGroupId,
            @Param("operatorId") Long operatorId,
            @Param("createTime") LocalDateTime createTime);

    @Insert("""
            insert into wvp_resource_group_item
                (group_id, resource_type, resource_id, create_by, create_time)
            select source.group_id, #{targetType}, #{targetId},
                   #{operatorId}, #{createTime}
              from wvp_resource_group_item source
             where source.resource_type = #{sourceType}
               and source.resource_id = #{sourceId}
               and not exists (
                   select 1
                     from wvp_resource_group_item target
                    where target.group_id = source.group_id
                      and target.resource_type = #{targetType}
                      and target.resource_id = #{targetId}
               )
            """)
    int copyResourceMemberships(
            @Param("sourceType") VideoResourceType sourceType,
            @Param("sourceId") long sourceId,
            @Param("targetType") VideoResourceType targetType,
            @Param("targetId") long targetId,
            @Param("operatorId") Long operatorId,
            @Param("createTime") LocalDateTime createTime);

    @Delete("""
            delete from wvp_resource_group_item
             where resource_type = #{resourceType}
               and resource_id = #{resourceId}
            """)
    int deleteByResource(
            @Param("resourceType") VideoResourceType resourceType,
            @Param("resourceId") long resourceId);
}
