package com.genersoft.iot.vmp.authorization.resourcegroup;

import com.genersoft.iot.vmp.authorization.resourcegroup.model.ResourceGroup;
import com.genersoft.iot.vmp.authorization.resourcegroup.model.ResourceGroupClosurePath;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
@Repository
public interface ResourceGroupMapper {

    String RESOURCE_GROUP_COLUMNS = """
            id, parent_id as parentId, group_code as groupCode, group_name as groupName,
            order_num as orderNum, status, description, create_by as createBy,
            create_time as createTime, update_by as updateBy, update_time as updateTime
            """;

    @Insert("""
            insert into wvp_resource_group
                (parent_id, group_code, group_name, order_num, status, description,
                 create_by, create_time, update_by, update_time)
            values
                (#{parentId}, #{groupCode}, #{groupName}, #{orderNum}, #{status}, #{description},
                 #{createBy}, #{createTime}, #{updateBy}, #{updateTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ResourceGroup group);

    @Select("select " + RESOURCE_GROUP_COLUMNS + " from wvp_resource_group where id = #{groupId}")
    ResourceGroup findById(@Param("groupId") long groupId);

    @Select("select " + RESOURCE_GROUP_COLUMNS
            + " from wvp_resource_group where id = #{groupId} for update")
    ResourceGroup findByIdForUpdate(@Param("groupId") long groupId);

    @Select("select " + RESOURCE_GROUP_COLUMNS
            + " from wvp_resource_group order by parent_id, order_num, id")
    List<ResourceGroup> findAll();

    @Select("select count(*) from wvp_resource_group where group_code = #{groupCode}")
    int countByCode(@Param("groupCode") String groupCode);

    @Select("""
            select count(*)
              from wvp_resource_group
             where group_code = #{groupCode}
               and id <> #{groupId}
            """)
    int countByCodeExcludingId(
            @Param("groupCode") String groupCode,
            @Param("groupId") long groupId);

    @Select("""
            select count(*)
              from wvp_resource_group g
             where g.id = #{groupId}
               and g.status = 0
               and not exists (
                   select 1
                     from wvp_resource_group_closure path
                     join wvp_resource_group ancestor on ancestor.id = path.ancestor_id
                    where path.descendant_id = g.id
                      and ancestor.status <> 0
               )
            """)
    int countEnabled(@Param("groupId") long groupId);

    @Select("""
            <script>
            select count(*)
              from wvp_resource_group g
             where g.status = 0
               and g.id in
             <foreach collection="groupIds" item="groupId" open="(" separator="," close=")">
                 #{groupId}
             </foreach>
               and not exists (
                   select 1
                     from wvp_resource_group_closure path
                     join wvp_resource_group ancestor on ancestor.id = path.ancestor_id
                    where path.descendant_id = g.id
                      and ancestor.status &lt;&gt; 0
               )
            </script>
            """)
    int countEnabledByIds(@Param("groupIds") Collection<Long> groupIds);

    @Select("""
            select ancestor_id as ancestorId, descendant_id as descendantId, depth
              from wvp_resource_group_closure
             where descendant_id = #{groupId}
             order by depth desc, ancestor_id
             for update
            """)
    List<ResourceGroupClosurePath> findAncestorPathsForUpdate(@Param("groupId") long groupId);

    @Select("""
            select ancestor_id as ancestorId, descendant_id as descendantId, depth
              from wvp_resource_group_closure
             where ancestor_id = #{groupId}
             order by depth, descendant_id
             for update
            """)
    List<ResourceGroupClosurePath> findSubtreePathsForUpdate(@Param("groupId") long groupId);

    @Insert("""
            insert into wvp_resource_group_closure (ancestor_id, descendant_id, depth)
            select ancestor_id, #{newGroupId}, depth + 1
              from wvp_resource_group_closure
             where descendant_id = #{parentId}
            """)
    int insertAncestorPaths(
            @Param("parentId") long parentId,
            @Param("newGroupId") long newGroupId);

    @Insert("""
            insert into wvp_resource_group_closure (ancestor_id, descendant_id, depth)
            values (#{groupId}, #{groupId}, 0)
            """)
    int insertSelfPath(@Param("groupId") long groupId);

    @Insert("""
            insert into wvp_resource_group_closure (ancestor_id, descendant_id, depth)
            values (#{ancestorId}, #{descendantId}, #{depth})
            """)
    int insertPath(
            @Param("ancestorId") long ancestorId,
            @Param("descendantId") long descendantId,
            @Param("depth") int depth);

    @Delete("""
            delete from wvp_resource_group_closure
             where ancestor_id = #{ancestorId}
               and descendant_id = #{descendantId}
            """)
    int deletePath(
            @Param("ancestorId") long ancestorId,
            @Param("descendantId") long descendantId);

    @Update("""
            update wvp_resource_group
               set parent_id = #{parentId},
                   update_by = #{operatorId},
                   update_time = #{updateTime}
             where id = #{groupId}
            """)
    int updateParent(
            @Param("groupId") long groupId,
            @Param("parentId") long parentId,
            @Param("operatorId") Long operatorId,
            @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            update wvp_resource_group
               set group_code = #{groupCode},
                   group_name = #{groupName},
                   order_num = #{orderNum},
                   status = #{status},
                   description = #{description},
                   update_by = #{operatorId},
                   update_time = #{updateTime}
             where id = #{groupId}
            """)
    int updateDetails(
            @Param("groupId") long groupId,
            @Param("groupCode") String groupCode,
            @Param("groupName") String groupName,
            @Param("orderNum") int orderNum,
            @Param("status") int status,
            @Param("description") String description,
            @Param("operatorId") Long operatorId,
            @Param("updateTime") LocalDateTime updateTime);

    @Select("select count(*) from wvp_resource_group where parent_id = #{groupId}")
    int countDirectChildren(@Param("groupId") long groupId);

    @Delete("delete from wvp_resource_group where id = #{groupId}")
    int deleteById(@Param("groupId") long groupId);
}
